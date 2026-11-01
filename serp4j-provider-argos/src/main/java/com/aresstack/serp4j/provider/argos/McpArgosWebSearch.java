package com.aresstack.serp4j.provider.argos;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.json.JsonTrees;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Adapter for {@link ArgosWebSearch}: calls the {@code web_search} tool of the AskAI/Argos browser sidecar
 * ({@code browser-mcp-server-java21}, Solon MCP, streamable HTTP on loopback) through the Serp4J
 * {@link HttpTransport} port.
 *
 * <p>Protocol: one {@code initialize} per MCP session (the {@code Mcp-Session-Id} header is kept), then
 * {@code notifications/initialized}, then {@code tools/call} per search. Answers may come as JSON or as a
 * {@code text/event-stream}. If the sidecar forgets the session (HTTP 404 with a session id, e.g. after a restart of
 * the sidecar), the adapter opens a new session once and repeats the call.</p>
 *
 * <p>The endpoint contains the sidecar's session token in its path ({@code http://127.0.0.1:<port>/mcp/browser/<token>},
 * see {@code BrowserMcpSidecarProcess} in AskAI); it never appears in messages or {@code toString()}.</p>
 *
 * <p><strong>Ownership (ADR-017):</strong> the transport is borrowed and never closed here. Starting and stopping the
 * sidecar process is the host's job.</p>
 */
public final class McpArgosWebSearch implements ArgosWebSearch {

    /** Name of the tool in the sidecar. */
    public static final String TOOL_NAME = "web_search";
    /** MCP protocol version sent in {@code initialize}. */
    public static final String PROTOCOL_VERSION = "2025-03-26";
    /** Default upper bound for one search; browser searches across several engines are slow. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(90);

    static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final String ACCEPT = "application/json, text/event-stream";

    private final String endpoint;
    private final String redactedEndpoint;
    private final String token;
    private final HttpTransport transport;
    private final Duration defaultTimeout;
    private final AtomicLong ids = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Call> running = Collections.newSetFromMap(new ConcurrentHashMap<Call, Boolean>());
    private final Object sessionLock = new Object();
    private CompletableFuture<Optional<String>> session;

    private McpArgosWebSearch(Builder builder) {
        this.endpoint = builder.endpoint;
        this.redactedEndpoint = redact(builder.endpoint);
        String redactedPrefix = redactedEndpoint.endsWith("***")
                ? redactedEndpoint.substring(0, redactedEndpoint.length() - 3) : null;
        String path = builder.endpoint.indexOf('?') >= 0
                ? builder.endpoint.substring(0, builder.endpoint.indexOf('?')) : builder.endpoint;
        this.token = redactedPrefix != null && path.startsWith(redactedPrefix)
                ? path.substring(redactedPrefix.length()) : null;
        this.transport = builder.transport;
        this.defaultTimeout = builder.timeout;
    }

    /**
     * @param endpoint  full MCP endpoint of the sidecar, including the token path segment
     * @param transport borrowed HTTP transport
     */
    public static Builder builder(String endpoint, HttpTransport transport) {
        return new Builder(endpoint, transport);
    }

    @Override
    public CompletableFuture<ArgosSearchOutput> search(final String query, Duration timeout) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (closed.get()) {
            throw new IllegalStateException("Argos web search is closed");
        }
        final Duration effective = timeout != null ? timeout : defaultTimeout;
        final long deadlineNanos = System.nanoTime() + effective.toNanos();
        final Call call = new Call();
        running.add(call);
        call.result.whenComplete((output, error) -> {
            running.remove(call);
            call.abort();
        });
        final ScheduledFuture<?> deadlineTask = Deadlines.SCHEDULER.schedule(() -> call.result.completeExceptionally(
                new HttpTransportException(HttpTransportException.Kind.TIMEOUT,
                        "Argos web search exceeded its time budget of " + effective + " (" + redactedEndpoint + ")")),
                effective.toNanos(), TimeUnit.NANOSECONDS);
        call.result.whenComplete((output, error) -> deadlineTask.cancel(false));
        if (closed.get()) {
            call.result.completeExceptionally(closedFailure());
            return call.result;
        }
        callTool(query, deadlineNanos, true, call).whenComplete((text, error) -> {
            if (error != null) {
                call.result.completeExceptionally(unwrap(error));
                return;
            }
            try {
                call.result.complete(ArgosSearchOutputParser.parse(text));
            } catch (RuntimeException e) {
                call.result.completeExceptionally(e);
            }
        });
        return call.result;
    }

    private HttpTransportException closedFailure() {
        return new HttpTransportException(HttpTransportException.Kind.CANCELLED, "Argos web search was closed");
    }

    /**
     * One running search: its result future and the HTTP request it currently waits for. Completing the result in
     * any way (cancel, deadline, close) cancels that request. Requests of the shared MCP initialization are never
     * registered here, so one caller's cancellation does not break the session other callers wait for.
     */
    private static final class Call {
        final CompletableFuture<ArgosSearchOutput> result = new CompletableFuture<ArgosSearchOutput>();
        final AtomicReference<CompletableFuture<HttpResponse>> current =
                new AtomicReference<CompletableFuture<HttpResponse>>();

        void register(CompletableFuture<HttpResponse> request) {
            current.set(request);
            if (result.isDone()) {
                request.cancel(true);
            }
        }

        void abort() {
            CompletableFuture<HttpResponse> request = current.get();
            if (request != null && !request.isDone()) {
                request.cancel(true);
            }
        }
    }

    private CompletableFuture<String> callTool(final String query, final long deadline, final boolean mayRetry,
                                             final Call call) {
        final CompletableFuture<Optional<String>> current = session(deadline);
        return current.thenCompose(new Function<Optional<String>, CompletableFuture<String>>() {
            @Override
            public CompletableFuture<String> apply(Optional<String> sessionId) {
                StructuredValue params = StructuredValue.objectBuilder()
                        .put("name", TOOL_NAME)
                        .put("arguments", StructuredValue.objectBuilder().put("query", query).build())
                        .build();
                return rpc("tools/call", params, sessionId, deadline, call).thenApply(new Function<StructuredValue, String>() {
                    @Override
                    public String apply(StructuredValue result) {
                        return toolText(result);
                    }
                });
            }
        }).handle(new java.util.function.BiFunction<String, Throwable, CompletableFuture<String>>() {
            @Override
            public CompletableFuture<String> apply(String text, Throwable error) {
                if (error == null) {
                    return CompletableFuture.completedFuture(text);
                }
                Throwable cause = unwrap(error);
                if (mayRetry && isSessionLost(cause)) {
                    resetSession(current);
                    return callTool(query, deadline, false, call);
                }
                CompletableFuture<String> failed = new CompletableFuture<String>();
                failed.completeExceptionally(cause);
                return failed;
            }
        }).thenCompose(Function.<CompletableFuture<String>>identity());
    }

    private CompletableFuture<Optional<String>> session(final long deadline) {
        synchronized (sessionLock) {
            if (session == null || session.isCompletedExceptionally()) {
                session = initialize(deadline);
            }
            return session;
        }
    }

    private void resetSession(CompletableFuture<Optional<String>> stale) {
        synchronized (sessionLock) {
            if (session == stale) {
                session = null;
            }
        }
    }

    private CompletableFuture<Optional<String>> initialize(final long deadline) {
        StructuredValue params = StructuredValue.objectBuilder()
                .put("protocolVersion", PROTOCOL_VERSION)
                .put("capabilities", StructuredValue.ofObject(Collections.<String, StructuredValue>emptyMap()))
                .put("clientInfo", StructuredValue.objectBuilder()
                        .put("name", "serp4j-provider-argos").put("version", "1").build())
                .build();
        final long id = ids.incrementAndGet();
        return post(message("initialize", params, id), Optional.<String>empty(), deadline, null)
                .thenCompose(new Function<HttpResponse, CompletableFuture<Optional<String>>>() {
                    @Override
                    public CompletableFuture<Optional<String>> apply(HttpResponse response) {
                        resultOf(response, id, "initialize");
                        final Optional<String> sessionId = response.getHeaders().getFirst(SESSION_HEADER);
                        StructuredValue notification = StructuredValue.objectBuilder()
                                .put("jsonrpc", "2.0").put("method", "notifications/initialized").build();
                        return post(notification, sessionId, deadline, null)
                                .thenApply(new Function<HttpResponse, Optional<String>>() {
                                    @Override
                                    public Optional<String> apply(HttpResponse ack) {
                                        if (!ack.isSuccessful()) {
                                            throw httpError(ack, "notifications/initialized");
                                        }
                                        return sessionId;
                                    }
                                });
                    }
                });
    }

    private CompletableFuture<StructuredValue> rpc(final String method, StructuredValue params,
                                                   Optional<String> sessionId, long deadline, Call call) {
        final long id = ids.incrementAndGet();
        return post(message(method, params, id), sessionId, deadline, call)
                .thenApply(new Function<HttpResponse, StructuredValue>() {
                    @Override
                    public StructuredValue apply(HttpResponse response) {
                        return resultOf(response, id, method);
                    }
                });
    }

    private CompletableFuture<HttpResponse> post(StructuredValue message, Optional<String> sessionId,
                                                 long deadline, Call call) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            CompletableFuture<HttpResponse> expired = new CompletableFuture<HttpResponse>();
            expired.completeExceptionally(new HttpTransportException(HttpTransportException.Kind.TIMEOUT,
                    "Argos web search exceeded its time budget (" + redactedEndpoint + ")"));
            return expired;
        }
        Duration timeout = Duration.ofNanos(remaining);
        HttpRequest.Builder request = HttpRequest.post(endpoint)
                .setHeader("Accept", ACCEPT)
                .jsonBody(JsonTrees.write(message))
                .timeout(timeout);
        if (sessionId.isPresent()) {
            request.setHeader(SESSION_HEADER, sessionId.get());
        }
        CompletableFuture<HttpResponse> sanitized = new CompletableFuture<HttpResponse>();
        CompletableFuture<HttpResponse> transportCall;
        try {
            transportCall = transport.execute(request.build());
        } catch (RuntimeException e) {
            sanitized.completeExceptionally(sanitize(e));
            return sanitized;
        }
        if (call != null) {
            call.register(transportCall);
        }
        transportCall.whenComplete((response, error) -> {
            if (error != null) {
                sanitized.completeExceptionally(sanitize(unwrap(error)));
            } else {
                sanitized.complete(response);
            }
        });
        return sanitized;
    }

    /**
     * Transport messages may contain the endpoint path and thus the token; the failure is replaced by one with a
     * scrubbed message and without cause chain.
     */
    private Throwable sanitize(Throwable error) {
        String message = scrub(String.valueOf(error.getMessage()));
        if (error instanceof HttpTransportException) {
            return new HttpTransportException(((HttpTransportException) error).getKind(), message);
        }
        if (error instanceof IllegalStateException) {
            return new IllegalStateException(message);
        }
        return new HttpTransportException(HttpTransportException.Kind.UNKNOWN,
                error.getClass().getSimpleName() + ": " + message);
    }

    String scrub(String text) {
        String result = text.replace(endpoint, redactedEndpoint);
        if (token != null && !token.isEmpty()) {
            result = result.replace(token, "***");
        }
        return result;
    }

    private static StructuredValue message(String method, StructuredValue params, long id) {
        return StructuredValue.objectBuilder()
                .put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params).build();
    }

    private StructuredValue resultOf(HttpResponse response, long id, String method) {
        if (!response.isSuccessful()) {
            throw httpError(response, method);
        }
        StructuredValue message = findMessage(response, id, method);
        Optional<StructuredValue> error = message.get("error");
        if (error.isPresent() && !error.get().isNull()) {
            String text = error.get().get("message").flatMap(StructuredValue::asText).orElse(error.get().toString());
            throw new ArgosWebSearchException(ArgosWebSearchException.Kind.RPC_ERROR,
                    method + " failed at " + redactedEndpoint + ": " + text);
        }
        Optional<StructuredValue> result = message.get("result");
        if (!result.isPresent() || !result.get().isObject()) {
            throw protocol(method + " answer has no result object");
        }
        return result.get();
    }

    private StructuredValue findMessage(HttpResponse response, long id, String method) {
        String body = response.getBodyAsText();
        String contentType = response.getContentType().orElse("").toLowerCase(java.util.Locale.ROOT);
        if (contentType.startsWith("text/event-stream")) {
            for (String data : ServerSentEvents.dataOf(body)) {
                StructuredValue candidate = readJson(data, method);
                if (candidate.isObject() && hasId(candidate, id)) {
                    return candidate;
                }
            }
            throw protocol(method + " answer stream contains no response with id " + id);
        }
        StructuredValue value = readJson(body, method);
        if (!value.isObject()) {
            throw protocol(method + " answer is not a JSON-RPC object");
        }
        return value;
    }

    private static boolean hasId(StructuredValue message, long id) {
        Optional<StructuredValue> value = message.get("id");
        return value.isPresent() && value.get().asLong().isPresent() && value.get().asLong().getAsLong() == id;
    }

    private StructuredValue readJson(String json, String method) {
        try {
            return JsonTrees.read(json);
        } catch (RuntimeException e) {
            throw new ArgosWebSearchException(ArgosWebSearchException.Kind.PROTOCOL,
                    method + " answer from " + redactedEndpoint + " is not valid JSON", -1, e);
        }
    }

    private String toolText(StructuredValue result) {
        StringBuilder text = new StringBuilder();
        Optional<StructuredValue> content = result.get("content");
        if (content.isPresent() && content.get().isArray()) {
            for (StructuredValue part : content.get().getElements()) {
                if (part.isObject() && "text".equals(part.get("type").flatMap(StructuredValue::asText).orElse(""))) {
                    text.append(part.get("text").flatMap(StructuredValue::asText).orElse(""));
                }
            }
        } else {
            throw protocol("tools/call result has no content array");
        }
        boolean isError = result.get("isError").flatMap(StructuredValue::asBoolean).orElse(Boolean.FALSE);
        if (isError) {
            throw new ArgosWebSearchException(ArgosWebSearchException.Kind.TOOL_ERROR,
                    TOOL_NAME + " reported an error: " + text);
        }
        return text.toString();
    }

    private ArgosWebSearchException httpError(HttpResponse response, String method) {
        return new ArgosWebSearchException(ArgosWebSearchException.Kind.HTTP_STATUS,
                method + " at " + redactedEndpoint + " answered HTTP " + response.getStatus(),
                response.getStatus(), null);
    }

    private ArgosWebSearchException protocol(String message) {
        return new ArgosWebSearchException(ArgosWebSearchException.Kind.PROTOCOL,
                message + " (" + redactedEndpoint + ")");
    }

    private static boolean isSessionLost(Throwable error) {
        return error instanceof ArgosWebSearchException
                && ((ArgosWebSearchException) error).getKind() == ArgosWebSearchException.Kind.HTTP_STATUS
                && ((ArgosWebSearchException) error).getHttpStatus() == 404;
    }

    static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** Replaces the last path segment (the sidecar token) with {@code ***}. */
    static String redact(String endpoint) {
        int query = endpoint.indexOf('?');
        String path = query >= 0 ? endpoint.substring(0, query) : endpoint;
        int schemeEnd = path.indexOf("://");
        int slash = path.lastIndexOf('/');
        if (slash <= (schemeEnd >= 0 ? schemeEnd + 2 : -1) || slash == path.length() - 1) {
            return path;
        }
        return path.substring(0, slash + 1) + "***";
    }

    /**
     * Forgets the MCP session and aborts running searches (their HTTP requests are cancelled, their futures fail
     * with {@code CANCELLED}). The sidecar ends idle sessions itself; the transport is borrowed and stays open.
     * Idempotent.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            synchronized (sessionLock) {
                session = null;
            }
            for (Call call : running) {
                call.result.completeExceptionally(closedFailure());
            }
        }
    }

    @Override
    public String toString() {
        return "McpArgosWebSearch{endpoint=" + redactedEndpoint + '}';
    }

    public static final class Builder {
        private final String endpoint;
        private final HttpTransport transport;
        private Duration timeout = DEFAULT_TIMEOUT;

        private Builder(String endpoint, HttpTransport transport) {
            if (endpoint == null || endpoint.trim().isEmpty()) {
                throw new IllegalArgumentException("endpoint must not be blank");
            }
            String trimmed = endpoint.trim();
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                throw new IllegalArgumentException("endpoint must be an http(s) URL but was " + redact(trimmed));
            }
            if (transport == null) {
                throw new NullPointerException("transport must not be null");
            }
            this.endpoint = trimmed;
            this.transport = transport;
        }

        public Builder timeout(Duration timeout) {
            if (timeout == null || timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive but was " + timeout);
            }
            this.timeout = timeout;
            return this;
        }

        public McpArgosWebSearch build() {
            return new McpArgosWebSearch(this);
        }
    }
}
