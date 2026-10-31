package com.aresstack.serp4j.http.ahc;

import com.aresstack.serp4j.http.HttpHeaders;
import com.aresstack.serp4j.http.HttpMethod;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.HttpTransportException;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.uri.Uri;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link HttpTransport} on async-http-client 2.x (Netty). One instance is meant to be shared by all providers of a
 * host; it is thread-safe and multiplexes requests over one connection pool and one event loop.
 *
 * <h2>Ownership (ADR-017)</h2>
 * <ul>
 *   <li>{@link #create()} / {@link #create(AhcHttpTransportOptions)} build their own {@link AsyncHttpClient}; the
 *   transport owns it and {@link #close()} closes it.</li>
 *   <li>{@link #wrap(AsyncHttpClient, AhcHttpTransportOptions)} borrows a client the host created; {@link #close()}
 *   then only stops this transport and leaves the client – and every other transport on it – open.</li>
 * </ul>
 * Whoever created the transport closes it, after the providers using it ({@link #ownsClient()} tells the two cases
 * apart). A provider never closes a transport that was injected into it.
 *
 * <h2>Behaviour</h2>
 * <ul>
 *   <li>Every server response, including {@code 4xx}/{@code 5xx}, completes the future normally. Only failures to
 *   obtain a response complete it with {@link HttpTransportException}, classified by
 *   {@link HttpTransportException.Kind}. Exception messages contain the method and the
 *   {@linkplain HttpRequest#getRedactedUrl() redacted URL}, never bodies or header values; the original exception is
 *   attached only as a message-less copy of its stack trace, because messages of the underlying libraries may
 *   contain URLs with credentials.</li>
 *   <li>A request without {@code User-Agent} gets {@link AhcHttpTransportOptions#getUserAgent()}; the transport
 *   adds no other headers (in particular no {@code Accept}, which is the provider's choice).</li>
 *   <li>The request timeout ({@link HttpRequest#getTimeout()}, else the option) bounds the whole exchange including
 *   redirects.</li>
 *   <li>Redirects are followed by this class, not by the client: {@code 303}, and {@code 301}/{@code 302} after a
 *   {@code POST}, continue as {@code GET} without body; {@code 307}/{@code 308} repeat method and body. When a
 *   redirect leaves the origin (scheme, host or port changes), every
 *   {@linkplain HttpHeaders#isSensitiveHeader(String) sensitive header} – the well-known credential headers and
 *   every header a {@link com.aresstack.serp4j.http.RequestAuthentication} marked – is dropped, so a credential
 *   never reaches a third host.</li>
 *   <li>At most {@link AhcHttpTransportOptions#getMaxConnections()} requests are in flight, at most
 *   {@link AhcHttpTransportOptions#getMaxConnectionsPerHost()} per origin; further requests wait in order instead
 *   of failing (async-http-client alone would reject them). Waiting counts against the request timeout.</li>
 *   <li>{@link #close()} fails running requests with {@link HttpTransportException.Kind#CANCELLED}; cancelling a
 *   returned future aborts the underlying request.</li>
 * </ul>
 */
public final class AhcHttpTransport implements HttpTransport {

    private final AsyncHttpClient client;
    private final boolean ownsClient;
    private final AhcHttpTransportOptions options;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Exchange> running = ConcurrentHashMap.newKeySet();
    private final ConnectionAdmission admission;

    private AhcHttpTransport(AsyncHttpClient client, boolean ownsClient, AhcHttpTransportOptions options) {
        this.client = client;
        this.ownsClient = ownsClient;
        this.options = options;
        this.admission = new ConnectionAdmission(options.getMaxConnections(), options.getMaxConnectionsPerHost());
    }

    /** A transport with its own client and {@linkplain AhcHttpTransportOptions#defaults() default options}. */
    public static AhcHttpTransport create() {
        return create(AhcHttpTransportOptions.defaults());
    }

    /** A transport with its own client, configured from {@code options}; {@link #close()} closes the client. */
    public static AhcHttpTransport create(AhcHttpTransportOptions options) {
        requireNonNull(options, "options");
        DefaultAsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout(toMillis(options.getConnectTimeout()))
                .setReadTimeout(toMillis(options.getReadTimeout()))
                .setRequestTimeout(toMillis(options.getRequestTimeout()))
                // The limits are enforced by ConnectionAdmission, which queues instead of failing.
                .setMaxConnections(-1)
                .setMaxConnectionsPerHost(-1)
                .setFollowRedirect(false)
                .setKeepAlive(options.isKeepAlive())
                .setCompressionEnforced(options.isCompressionEnabled())
                .setUserAgent(options.getUserAgent())
                .setThreadPoolName("serp4j-http-ahc")
                .build();
        return new AhcHttpTransport(new DefaultAsyncHttpClient(config), true, options);
    }

    /**
     * A transport on a client the caller created and keeps owning: {@link #close()} leaves {@code client} open.
     * Only the request-level settings of {@code options} apply (see {@link AhcHttpTransportOptions}); redirects are
     * handled by this transport whatever the client is configured to do.
     */
    public static AhcHttpTransport wrap(AsyncHttpClient client, AhcHttpTransportOptions options) {
        return new AhcHttpTransport(requireNonNull(client, "client"), false, requireNonNull(options, "options"));
    }

    /** Whether {@link #close()} closes the underlying client ({@code create}) or leaves it to its owner ({@code wrap}). */
    public boolean ownsClient() {
        return ownsClient;
    }

    public AhcHttpTransportOptions getOptions() {
        return options;
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public CompletableFuture<HttpResponse> execute(HttpRequest request) {
        requireNonNull(request, "request");
        if (closed.get()) {
            throw new IllegalStateException("transport is closed");
        }
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(
                toMillis(request.getTimeout().orElse(options.getRequestTimeout())));
        Exchange exchange = new Exchange(request, System.nanoTime() + timeoutNanos, timeoutNanos);
        running.add(exchange);
        exchange.result.whenComplete((response, failure) -> {
            running.remove(exchange);
            if (exchange.result.isCancelled()) {
                exchange.abort();
            }
        });
        if (closed.get()) {
            // close() ran between the check above and the registration; it may have missed this exchange.
            exchange.fail(new HttpTransportException(HttpTransportException.Kind.CANCELLED,
                    "transport closed (" + request.getMethod() + " " + request.getRedactedUrl() + ")"));
        } else {
            send(exchange, request, 0);
        }
        return exchange.result;
    }

    private void send(Exchange exchange, HttpRequest request, int redirects) {
        if (exchange.result.isDone()) {
            return;
        }
        String origin;
        try {
            origin = originKey(Uri.create(request.getUrl()));
        } catch (RuntimeException e) {
            exchange.fail(TransportFailures.translate(e, request));
            return;
        }
        admission.acquire(origin, () -> dispatch(exchange, request, redirects, origin));
    }

    /** Runs with an admission permit for {@code origin}, which is returned exactly once. */
    private void dispatch(Exchange exchange, HttpRequest request, int redirects, String origin) {
        AtomicBoolean permitHeld = new AtomicBoolean(true);
        Runnable releasePermit = () -> {
            if (permitHeld.compareAndSet(true, false)) {
                admission.release(origin);
            }
        };
        if (exchange.result.isDone()) {
            releasePermit.run();
            return;
        }
        long remainingMillis = TimeUnit.NANOSECONDS.toMillis(exchange.deadlineNanos - System.nanoTime());
        if (remainingMillis <= 0) {
            releasePermit.run();
            exchange.fail(TransportFailures.timeout(request, exchange.timeoutNanos));
            return;
        }
        if (client.isClosed()) {
            releasePermit.run();
            exchange.fail(new HttpTransportException(HttpTransportException.Kind.CANCELLED,
                    "underlying client is closed (" + request.getMethod() + " " + request.getRedactedUrl() + ")"));
            return;
        }
        ListenableFuture<Response> pending;
        try {
            pending = client.executeRequest(toAhcRequest(request, (int) Math.min(remainingMillis, Integer.MAX_VALUE)));
        } catch (RuntimeException e) {
            releasePermit.run();
            exchange.fail(TransportFailures.translate(e, request));
            return;
        }
        exchange.current = pending;
        pending.toCompletableFuture().whenComplete((response, failure) -> {
            releasePermit.run();
            if (exchange.result.isDone()) {
                return;
            }
            if (failure != null) {
                exchange.fail(TransportFailures.translate(failure, request));
                return;
            }
            try {
                HttpRequest next = redirectTarget(request, response, redirects);
                if (next == null) {
                    exchange.result.complete(toHttpResponse(response));
                } else {
                    send(exchange, next, redirects + 1);
                }
            } catch (HttpTransportException e) {
                exchange.fail(e);
            } catch (RuntimeException e) {
                exchange.fail(TransportFailures.translate(e, request));
            }
        });
        if (exchange.result.isDone()) {
            pending.cancel(true);
        }
    }

    private org.asynchttpclient.Request toAhcRequest(HttpRequest request, int timeoutMillis) {
        RequestBuilder builder = new RequestBuilder(request.getMethod().name())
                .setUrl(request.getUrl())
                .setFollowRedirect(false)
                .setRequestTimeout(timeoutMillis);
        for (Map.Entry<String, List<String>> header : request.getHeaders().asMap().entrySet()) {
            for (String value : header.getValue()) {
                builder.addHeader(header.getKey(), value);
            }
        }
        if (!request.getHeaders().contains("User-Agent")) {
            builder.setHeader("User-Agent", options.getUserAgent());
        }
        if (request.hasBody()) {
            builder.setBody(request.getBody());
        }
        return builder.build();
    }

    private static HttpResponse toHttpResponse(Response response) {
        int status = response.getStatusCode();
        if (status < 100 || status > 599) {
            throw new HttpTransportException(HttpTransportException.Kind.PROTOCOL,
                    "server sent invalid status code " + status);
        }
        HttpResponse.Builder builder = HttpResponse.builder(status);
        for (Map.Entry<String, String> header : response.getHeaders()) {
            builder.header(header.getKey(), header.getValue());
        }
        return builder.body(response.getResponseBodyAsBytes()).build();
    }

    /** The request to send next, or {@code null} when {@code response} is the final response. */
    private HttpRequest redirectTarget(HttpRequest request, Response response, int redirects) {
        int status = response.getStatusCode();
        if (!options.isFollowRedirects() || !isRedirect(status)) {
            return null;
        }
        String location = response.getHeader("Location");
        if (location == null || location.trim().isEmpty()) {
            return null;
        }
        if (redirects >= options.getMaxRedirects()) {
            throw new HttpTransportException(HttpTransportException.Kind.PROTOCOL, "more than "
                    + options.getMaxRedirects() + " redirects (" + request.getMethod() + " "
                    + request.getRedactedUrl() + ")");
        }
        Uri current = Uri.create(request.getUrl());
        Uri target;
        try {
            target = Uri.create(current, location.trim());
        } catch (IllegalArgumentException e) {
            throw new HttpTransportException(HttpTransportException.Kind.PROTOCOL,
                    "invalid redirect location (" + request.getMethod() + " " + request.getRedactedUrl() + ")");
        }
        if (!"http".equalsIgnoreCase(target.getScheme()) && !"https".equalsIgnoreCase(target.getScheme())) {
            throw new HttpTransportException(HttpTransportException.Kind.PROTOCOL, "redirect to unsupported scheme "
                    + target.getScheme() + " (" + request.getMethod() + " " + request.getRedactedUrl() + ")");
        }

        boolean switchToGet = status == 303 && request.getMethod() != HttpMethod.HEAD
                || (status == 301 || status == 302) && request.getMethod() == HttpMethod.POST;
        HttpMethod method = switchToGet ? HttpMethod.GET : request.getMethod();

        HttpHeaders.Builder headers = request.getHeaders().toBuilder();
        if (switchToGet) {
            headers.remove("Content-Type").remove("Content-Length").remove("Content-Encoding");
        }
        if (!sameOrigin(current, target)) {
            for (String name : request.getHeaders().getNames()) {
                if (request.getHeaders().isSensitiveHeader(name)) {
                    headers.remove(name);
                }
            }
        }
        HttpRequest.Builder next = HttpRequest.builder(method, target.toUrl()).headers(headers.build());
        for (String parameter : request.getSensitiveQueryParameters()) {
            next.sensitiveQueryParameter(parameter);
        }
        if (!switchToGet && request.hasBody()) {
            next.body(request.getBody(), request.getContentType().orElse("application/octet-stream"));
        }
        return next.build();
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static boolean sameOrigin(Uri a, Uri b) {
        return originKey(a).equals(originKey(b));
    }

    private static String originKey(Uri uri) {
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT) + ":"
                + uri.getExplicitPort();
    }

    /**
     * Fails running requests with {@link HttpTransportException.Kind#CANCELLED} and, if this transport
     * {@linkplain #ownsClient() owns} its client, closes it. Idempotent and thread-safe.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (Exchange exchange : running) {
            exchange.fail(new HttpTransportException(HttpTransportException.Kind.CANCELLED, "transport closed ("
                    + exchange.request.getMethod() + " " + exchange.request.getRedactedUrl() + ")"));
            exchange.abort();
        }
        if (ownsClient) {
            try {
                client.close();
            } catch (Exception ignored) {
                // Nothing the caller could do: the transport is closed either way and running requests have failed.
            }
        }
    }

    @Override
    public String toString() {
        return "AhcHttpTransport{ownsClient=" + ownsClient + ", closed=" + closed.get() + ", running="
                + running.size() + ", options=" + options + '}';
    }

    private static int toMillis(java.time.Duration duration) {
        return (int) Math.min(duration.toMillis(), Integer.MAX_VALUE);
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        return value;
    }

    /** One call of {@link #execute}, possibly spanning several redirects. */
    private static final class Exchange {
        final HttpRequest request;
        final long deadlineNanos;
        final long timeoutNanos;
        final CompletableFuture<HttpResponse> result = new CompletableFuture<HttpResponse>();
        volatile ListenableFuture<Response> current;

        Exchange(HttpRequest request, long deadlineNanos, long timeoutNanos) {
            this.request = request;
            this.deadlineNanos = deadlineNanos;
            this.timeoutNanos = timeoutNanos;
        }

        void fail(HttpTransportException failure) {
            result.completeExceptionally(failure);
        }

        void abort() {
            ListenableFuture<Response> pending = current;
            if (pending != null && !pending.isDone()) {
                pending.cancel(true);
            }
        }
    }
}
