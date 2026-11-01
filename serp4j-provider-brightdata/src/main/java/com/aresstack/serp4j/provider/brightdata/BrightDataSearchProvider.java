package com.aresstack.serp4j.provider.brightdata;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.ext.RawPayload;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.model.metadata.ExecutionInfo;
import com.aresstack.serp4j.core.model.metadata.PageInfo;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.port.ProviderDescriptor;
import com.aresstack.serp4j.core.port.ProviderKind;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.core.port.capability.CapabilityCheck;
import com.aresstack.serp4j.core.port.capability.CapabilityViolation;
import com.aresstack.serp4j.core.port.capability.ResponseFeature;
import com.aresstack.serp4j.core.port.capability.SearchCapabilities;
import com.aresstack.serp4j.core.port.credential.CredentialKey;
import com.aresstack.serp4j.core.port.credential.CredentialProvider;
import com.aresstack.serp4j.core.port.credential.SecretValue;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.MappedMetadata;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.mapping.MappingResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link SearchProvider} for the Bright Data SERP API, synchronous REST request API (reference §3, §10.4).
 *
 * <p>One search is one {@code POST <apiUrl>} with {@code {"zone", "url", "format": "json",
 * "data_format": "parsed"}} and optionally {@code country} (proxy country). {@code url} is a Google search URL
 * built from the request ({@code q}, {@code start}, {@code hl}, {@code gl}, {@code uule}, {@code safe}, {@code tbs},
 * {@code brd_mobile}, plus {@link BrightDataOptions}). The response wrapper is unwrapped and the parsed SERP mapped
 * with {@link BrightDataMapping}.</p>
 *
 * <p>Ownership (ADR-017): the {@link HttpTransport} and the {@link CredentialProvider} are borrowed and never
 * closed. The API key is resolved per search, sent only as sensitive {@code Authorization} header and appears in
 * no URL, {@code toString()}, metadata or exception. Thread-safe.</p>
 */
public final class BrightDataSearchProvider implements SearchProvider {

    /** Provider type used in configurations and descriptors. */
    public static final String PROVIDER_TYPE = "brightdata";
    /** Endpoint of the synchronous request API. */
    public static final String DEFAULT_API_URL = "https://api.brightdata.com/request";
    /** Name of the API key credential relative to the provider id. */
    public static final String API_KEY = "apiKey";

    /** What the adapter can do: Google web search with the parameters it translates into the Google URL. */
    public static final SearchCapabilities CAPABILITIES = SearchCapabilities.builder()
            .target(SearchTarget.web(SearchEngineId.GOOGLE))
            .features(SearchFeature.QUERY_TEXT, SearchFeature.PAGINATION_OFFSET, SearchFeature.COUNTRY,
                    SearchFeature.LANGUAGE, SearchFeature.GEO_LOCATION, SearchFeature.FRESHNESS,
                    SearchFeature.SAFE_SEARCH, SearchFeature.DEVICE, SearchFeature.RAW_RESPONSE)
            .responseFeatures(ResponseFeature.ORGANIC_RESULTS, ResponseFeature.SERP_FEATURES,
                    ResponseFeature.KNOWLEDGE_GRAPH, ResponseFeature.RELATED_QUERIES,
                    ResponseFeature.TOTAL_RESULT_COUNT, ResponseFeature.RAW_PAYLOAD)
            .build();

    private final ProviderId id;
    private final HttpTransport transport;
    private final CredentialProvider credentials;
    private final CredentialKey apiKey;
    private final String zone;
    private final String apiUrl;
    private final Duration requestTimeout;
    private final Clock clock;
    private final GoogleSearchUrlBuilder urlBuilder;
    private final MappingEngine engine;
    private final ProviderDescriptor descriptor;
    private final AtomicBoolean closed = new AtomicBoolean();

    private BrightDataSearchProvider(Builder builder) {
        this.id = builder.id;
        this.transport = Objects.requireNonNull(builder.transport, "transport");
        this.credentials = Objects.requireNonNull(builder.credentials, "credentials");
        this.apiKey = builder.apiKey != null ? builder.apiKey : CredentialKey.of(builder.id, API_KEY);
        this.zone = requireText(builder.zone, "zone");
        this.apiUrl = builder.apiUrl;
        this.requestTimeout = builder.requestTimeout;
        this.clock = builder.clock;
        this.urlBuilder = new GoogleSearchUrlBuilder(builder.googleSearchUrl);
        this.engine = BrightDataMapping.webEngine(builder.id);
        this.descriptor = ProviderDescriptor.builder(builder.id, PROVIDER_TYPE, ProviderKind.CLOUD_API, CAPABILITIES)
                .displayName("Bright Data SERP API")
                .build();
    }

    /** New builder for a provider instance with the given id. */
    public static Builder builder(ProviderId id) {
        return new Builder(id);
    }

    @Override
    public ProviderDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public CompletableFuture<SearchResponse> search(SearchRequest request, SearchContext context) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        if (closed.get()) {
            return failed(SearchErrorCategory.CANCELLED, "provider is closed");
        }
        if (context.getCancellation().isCancelled()) {
            return failed(SearchErrorCategory.CANCELLED, "search was cancelled before it started");
        }
        Instant now = clock.instant();
        Duration timeout = requestTimeout;
        if (context.getDeadline().isPresent()) {
            Duration left = Duration.between(now, context.getDeadline().get());
            if (left.isZero() || left.isNegative()) {
                return failed(SearchErrorCategory.TIMEOUT, "deadline expired before the search started");
            }
            if (timeout == null || left.compareTo(timeout) < 0) {
                timeout = left;
            }
        }
        CapabilityCheck check = CAPABILITIES.check(request);
        if (!check.getRequiredViolations().isEmpty()) {
            StringBuilder message = new StringBuilder("Bright Data adapter cannot serve the request:");
            for (CapabilityViolation violation : check.getRequiredViolations()) {
                message.append(' ').append(violation.getMessage()).append(';');
            }
            return failed(SearchErrorCategory.UNSUPPORTED, message.toString());
        }
        final Optional<BrightDataOptions> options = request.getProviderOptions(id, BrightDataOptions.class);
        final String targetUrl = urlBuilder.build(request, options);

        Optional<SecretValue> key = credentials.resolve(apiKey);
        if (!key.isPresent() || key.get().isEmpty()) {
            return failed(SearchErrorCategory.CONFIGURATION, "no Bright Data API key configured under " + apiKey);
        }
        HttpRequest.Builder http = HttpRequest.post(apiUrl)
                .header("Accept", "application/json")
                .jsonBody(requestBody(options, targetUrl));
        String authorization = key.get().use(chars -> "Bearer " + new String(chars));
        http.setSensitiveHeader("Authorization", authorization);
        if (timeout != null) {
            http.timeout(timeout);
        }

        final Instant startedAt = now;
        final long startNanos = System.nanoTime();
        final CompletableFuture<SearchResponse> result = new CompletableFuture<SearchResponse>();
        final CompletableFuture<HttpResponse> call;
        try {
            call = transport.execute(http.build());
        } catch (RuntimeException e) {
            return failedWith(translate(e));
        }
        context.getCancellation().onCancel(() -> {
            result.completeExceptionally(failure(SearchErrorCategory.CANCELLED, "search was cancelled"));
            call.cancel(true);
        });
        call.whenComplete((response, error) -> {
            if (error != null) {
                result.completeExceptionally(translate(error));
                return;
            }
            try {
                Duration took = Duration.ofNanos(System.nanoTime() - startNanos);
                result.complete(toResponse(request, check, targetUrl, response, startedAt, took));
            } catch (SearchProviderException e) {
                result.completeExceptionally(e);
            } catch (RuntimeException e) {
                result.completeExceptionally(SearchProviderException.builder(id, SearchErrorCategory.INTERNAL,
                        "mapping the Bright Data response failed (" + e.getClass().getSimpleName() + ")")
                        .cause(e).build());
            }
        });
        return result;
    }

    private String requestBody(Optional<BrightDataOptions> options, String targetUrl) {
        StructuredValue.ObjectBuilder body = StructuredValue.objectBuilder()
                .put("zone", options.isPresent() && options.get().getZone().isPresent()
                        ? options.get().getZone().get() : zone)
                .put("url", targetUrl)
                .put("format", "json")
                .put("data_format", "parsed");
        if (options.isPresent() && options.get().getProxyCountry().isPresent()) {
            body.put("country", options.get().getProxyCountry().get().toLowerCase(java.util.Locale.ROOT));
        }
        return JsonTrees.write(body.build());
    }

    private SearchResponse toResponse(SearchRequest request, CapabilityCheck check, String targetUrl,
                                      HttpResponse response, Instant startedAt, Duration took) {
        if (!response.isSuccessful()) {
            throw BrightDataResponses.httpFailure(id, response);
        }
        BrightDataResponses.Unwrapped unwrapped = BrightDataResponses.unwrap(id, response.getBodyAsText());
        MappingResult mapped = engine.map(unwrapped.serp);
        MappedMetadata values = mapped.getMetadata();

        Optional<String> reported = values.getOriginalQuery();
        QueryInfo query = reported.isPresent() && !reported.get().equals(request.getQuery())
                ? QueryInfo.of(request.getQuery(), reported.get())
                : QueryInfo.of(request.getQuery());
        SearchMetadata.Builder metadata = SearchMetadata.forProvider(id, SearchEngineId.GOOGLE, SearchVertical.WEB,
                query);
        mapped.applyTo(metadata);

        ExecutionInfo.Builder execution = ExecutionInfo.builder()
                .startedAt(startedAt)
                .duration(took)
                .sourceUrl(targetUrl)
                .attempts(1);
        if (values.getProviderRequestId().isPresent()) {
            execution.providerRequestId(values.getProviderRequestId().get());
        }
        metadata.execution(execution.build());
        metadata.page(values.applyTo(PageInfo.builder(request.getPaging().getOffset(), request.getPaging().getLimit()))
                .build());
        if (request.getLocale().isPresent()) {
            metadata.locale(request.getLocale().get());
        }
        if (request.getDevice().isPresent()) {
            metadata.device(request.getDevice().get());
        }
        for (CapabilityViolation ignored : check.getOptionalViolations()) {
            metadata.warning(SearchWarning.of(SearchWarning.FEATURE_IGNORED, ignored.getMessage(), id));
        }
        if (request.getRawPayloadPolicy() == RawPayloadPolicy.ALWAYS) {
            metadata.rawPayload(RawPayload.builder(response.getContentType().orElse("application/json"),
                    response.getBody()).httpStatus(response.getStatus()).build());
        }
        return SearchResponse.of(mapped.getData(), metadata.build());
    }

    private SearchProviderException translate(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof SearchProviderException) {
            return (SearchProviderException) cause;
        }
        if (cause instanceof java.util.concurrent.CancellationException) {
            return failure(SearchErrorCategory.CANCELLED, "search was cancelled");
        }
        if (cause instanceof HttpTransportException) {
            HttpTransportException transportError = (HttpTransportException) cause;
            SearchErrorCategory category;
            switch (transportError.getKind()) {
                case TIMEOUT:
                    category = SearchErrorCategory.TIMEOUT;
                    break;
                case CANCELLED:
                    category = SearchErrorCategory.CANCELLED;
                    break;
                case PROTOCOL:
                    category = SearchErrorCategory.RESPONSE_INVALID;
                    break;
                case CONNECT:
                case TLS:
                case IO:
                    category = SearchErrorCategory.UNAVAILABLE;
                    break;
                default:
                    category = SearchErrorCategory.INTERNAL;
                    break;
            }
            return SearchProviderException.builder(id, category,
                    "HTTP transport failed: " + transportError.getKind()).cause(transportError).build();
        }
        return SearchProviderException.builder(id, SearchErrorCategory.INTERNAL,
                "Bright Data search failed (" + cause.getClass().getSimpleName() + ")").cause(cause).build();
    }

    private SearchProviderException failure(SearchErrorCategory category, String message) {
        return SearchProviderException.builder(id, category, message).build();
    }

    private CompletableFuture<SearchResponse> failed(SearchErrorCategory category, String message) {
        return failedWith(failure(category, message));
    }

    private static CompletableFuture<SearchResponse> failedWith(SearchProviderException failure) {
        CompletableFuture<SearchResponse> future = new CompletableFuture<SearchResponse>();
        future.completeExceptionally(failure);
        return future;
    }

    /** Marks the provider closed; idempotent. The borrowed transport and credentials stay open (ADR-017). */
    @Override
    public void close() {
        closed.set(true);
    }

    @Override
    public String toString() {
        return "BrightDataSearchProvider{id=" + id + ", zone=" + zone + ", apiUrl=" + apiUrl + ", closed=" + closed.get()
                + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Bright Data provider needs '" + name + "'");
        }
        return value.trim();
    }

    /** Builder for {@link BrightDataSearchProvider}. Transport, credentials and zone are mandatory. */
    public static final class Builder {

        private final ProviderId id;
        private HttpTransport transport;
        private CredentialProvider credentials;
        private CredentialKey apiKey;
        private String zone;
        private String apiUrl = DEFAULT_API_URL;
        private String googleSearchUrl = GoogleSearchUrlBuilder.DEFAULT_BASE;
        private Duration requestTimeout;
        private Clock clock = Clock.systemUTC();

        private Builder(ProviderId id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        /** Borrowed HTTP transport (ADR-017); never closed by the provider. */
        public Builder transport(HttpTransport transport) {
            this.transport = Objects.requireNonNull(transport, "transport");
            return this;
        }

        /** Source of the API key; resolved on every search. */
        public Builder credentials(CredentialProvider credentials) {
            this.credentials = Objects.requireNonNull(credentials, "credentials");
            return this;
        }

        /** Key of the API key; default {@code CredentialKey.of(id, "apiKey")}. */
        public Builder apiKey(CredentialKey apiKey) {
            this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
            return this;
        }

        /** The SERP zone from the Bright Data account (host configuration, e.g. {@code serp_api1}). */
        public Builder zone(String zone) {
            this.zone = Objects.requireNonNull(zone, "zone");
            return this;
        }

        /** Request API endpoint; default {@link #DEFAULT_API_URL}. Must be {@code https}. */
        public Builder apiUrl(String apiUrl) {
            this.apiUrl = requireHttps(apiUrl, "apiUrl");
            return this;
        }

        /** Base of the Google search URL; default {@code https://www.google.com/search} (e.g. a country domain). */
        public Builder googleSearchUrl(String googleSearchUrl) {
            this.googleSearchUrl = requireHttps(googleSearchUrl, "googleSearchUrl");
            return this;
        }

        /** Upper bound per HTTP request; a shorter context deadline wins. Default: the transport's own limits. */
        public Builder requestTimeout(Duration requestTimeout) {
            Objects.requireNonNull(requestTimeout, "requestTimeout");
            if (requestTimeout.isZero() || requestTimeout.isNegative()) {
                throw new IllegalArgumentException("requestTimeout must be positive but was " + requestTimeout);
            }
            this.requestTimeout = requestTimeout;
            return this;
        }

        /** Clock for deadlines and start times; default UTC system clock. */
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /** Builds the provider; fails fast on missing transport, credentials or zone. Performs no I/O. */
        public BrightDataSearchProvider build() {
            if (transport == null) {
                throw new IllegalStateException("Bright Data provider needs a transport");
            }
            if (credentials == null) {
                throw new IllegalStateException("Bright Data provider needs credentials");
            }
            return new BrightDataSearchProvider(this);
        }

        private static String requireHttps(String url, String name) {
            Objects.requireNonNull(url, name);
            if (!url.startsWith("https://")) {
                throw new IllegalArgumentException(name + " must be an https URL");
            }
            return url;
        }
    }
}
