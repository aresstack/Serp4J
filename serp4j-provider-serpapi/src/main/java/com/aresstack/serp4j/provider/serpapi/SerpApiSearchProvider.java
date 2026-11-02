package com.aresstack.serp4j.provider.serpapi;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
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
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.provider.serpapi.internal.SerpApiErrors;
import com.aresstack.serp4j.provider.serpapi.internal.SerpApiRequestFactory;
import com.aresstack.serp4j.provider.serpapi.internal.SerpApiResponseMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link SearchProvider} for SerpApi, {@code GET https://serpapi.com/search.json} with {@code engine=google} or
 * {@code engine=bing}.
 *
 * <pre>{@code
 * SearchProvider serpApi = SerpApiSearchProvider.builder()
 *         .transport(transport)                                    // owned by the caller (ADR-017)
 *         .credentials(credentials)                                // resolves "serpapi.api-key"
 *         .options(SerpApiSearchOptions.builder().defaultCountry("DE").defaultLanguage("de").build())
 *         .build();
 * }</pre>
 *
 * <ul>
 *   <li>Targets {@code google/web} (default when the request names no target) and {@code bing/web}. Google:
 *       {@code num} up to 100, {@code start} = offset. Bing: {@code count} up to 50, {@code first} = offset + 1.</li>
 *   <li>The key is resolved per search from the {@link CredentialProvider} and sent as the URL parameter
 *       {@code api_key}, as SerpApi requires. It is registered as a sensitive query parameter and therefore masked in
 *       {@code getRedactedUrl()}, {@code toString()}, {@code ExecutionInfo.sourceUrl} and error messages.</li>
 *   <li>Errors: 401/403 or "Invalid API key" to {@code AUTHENTICATION}, "run out of searches" to
 *       {@code QUOTA_EXHAUSTED}, other 429 to {@code RATE_LIMITED}, 400 to {@code INVALID_REQUEST}, 5xx and
 *       {@code search_metadata.status=Error} to {@code UNAVAILABLE}. "Google hasn't returned any results" is an empty,
 *       successful answer.</li>
 *   <li>SerpApi's one-hour cache is used by default (cached answers are free); {@code no_cache} is opt-in.</li>
 *   <li>{@link #close()} completes running searches with {@code CANCELLED} and never closes the transport or the
 *       credential provider.</li>
 * </ul>
 * Thread-safe.
 */
public final class SerpApiSearchProvider implements SearchProvider {

    public static final String PROVIDER_TYPE = "serpapi";
    public static final ProviderId DEFAULT_ID = ProviderId.of(PROVIDER_TYPE);
    /** Secret name; the default credential key is {@code <provider id>.api-key}. */
    public static final String API_KEY_SECRET = "api-key";

    private final ProviderDescriptor descriptor;
    private final HttpTransport transport;
    private final CredentialProvider credentials;
    private final CredentialKey credentialKey;
    private final SerpApiSearchOptions options;
    private final Clock clock;
    private final SerpApiRequestFactory requestFactory;
    private final SerpApiResponseMapper responseMapper;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<CompletableFuture<SearchResponse>> running =
            Collections.newSetFromMap(new ConcurrentHashMap<CompletableFuture<SearchResponse>, Boolean>());

    private SerpApiSearchProvider(Builder builder) {
        ProviderId id = builder.id;
        this.descriptor = ProviderDescriptor.builder(id, PROVIDER_TYPE, ProviderKind.CLOUD_API, capabilities())
                .displayName("SerpApi")
                .build();
        this.transport = builder.transport;
        this.credentials = builder.credentials;
        this.credentialKey = builder.credentialKey != null ? builder.credentialKey
                : CredentialKey.of(id, API_KEY_SECRET);
        this.options = builder.options;
        this.clock = builder.clock;
        this.requestFactory = new SerpApiRequestFactory(id, options, clock);
        this.responseMapper = new SerpApiResponseMapper(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** What the SerpApi Google and Bing web search can do. */
    public static SearchCapabilities capabilities() {
        return SearchCapabilities.builder()
                .target(SearchEngineId.GOOGLE, SearchVertical.WEB)
                .target(SearchEngineId.BING, SearchVertical.WEB)
                .features(SearchFeature.QUERY_TEXT, SearchFeature.PAGINATION_OFFSET, SearchFeature.PAGINATION_PAGE,
                        SearchFeature.MAX_RESULTS, SearchFeature.COUNTRY, SearchFeature.LANGUAGE,
                        SearchFeature.GEO_LOCATION, SearchFeature.FRESHNESS, SearchFeature.SAFE_SEARCH,
                        // SerpApiResponseMapper attaches the raw body for ALWAYS/ON_ERROR.
                        SearchFeature.RAW_RESPONSE)
                .responseFeatures(ResponseFeature.ORGANIC_RESULTS, ResponseFeature.SERP_FEATURES,
                        ResponseFeature.KNOWLEDGE_GRAPH, ResponseFeature.RELATED_QUERIES,
                        ResponseFeature.TOTAL_RESULT_COUNT, ResponseFeature.RAW_PAYLOAD)
                .maxResultsPerRequest(SerpApiRequestFactory.MAX_GOOGLE_NUM)
                .build();
    }

    @Override
    public ProviderDescriptor getDescriptor() {
        return descriptor;
    }

    public SerpApiSearchOptions getOptions() {
        return options;
    }

    @Override
    public CompletableFuture<SearchResponse> search(SearchRequest request, SearchContext context) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        ProviderId id = descriptor.getId();
        if (closed.get()) {
            return failed(failure(SearchErrorCategory.CANCELLED, "provider " + id + " is closed"));
        }
        CapabilityCheck check = descriptor.getCapabilities().check(request);
        if (!check.getRequiredViolations().isEmpty()) {
            return failed(failure(SearchErrorCategory.UNSUPPORTED,
                    "SerpApi cannot serve this request: " + messages(check.getRequiredViolations())));
        }
        if (context.getCancellation().isCancelled()) {
            return failed(failure(SearchErrorCategory.CANCELLED, "search was cancelled"));
        }
        final Instant startedAt = clock.instant();
        if (context.isExpired(startedAt)) {
            return failed(failure(SearchErrorCategory.TIMEOUT, "deadline passed before the search started"));
        }

        final SearchEngineId engine = request.getTarget().map(SearchTarget::getEngine).orElse(SearchEngineId.GOOGLE);
        final SerpApiRequestFactory.Prepared prepared;
        char[] key = null;
        try {
            // Invalid requests and options fail before the credential is touched or a search is spent.
            requestFactory.validate(request, engine);
            key = resolveKey();
            // Credential resolution may take time: measure the remaining deadline only now.
            Instant beforeDispatch = clock.instant();
            if (context.isExpired(beforeDispatch)) {
                return failed(failure(SearchErrorCategory.TIMEOUT, "deadline passed before the request was sent"));
            }
            prepared = requestFactory.prepare(request, engine, key, timeout(context, beforeDispatch),
                    ignoredFeatureWarnings(check));
        } catch (SearchProviderException e) {
            return failed(e);
        } finally {
            if (key != null) {
                Arrays.fill(key, '\0');
            }
        }

        final CompletableFuture<SearchResponse> result = new CompletableFuture<SearchResponse>();
        running.add(result);
        final CompletableFuture<HttpResponse> call;
        try {
            call = transport.execute(prepared.getHttpRequest());
        } catch (RuntimeException e) {
            running.remove(result);
            return failed(translate(e));
        }
        result.whenComplete((value, error) -> {
            running.remove(result);
            if (error != null) {
                call.cancel(true);
            }
        });
        context.getCancellation().onCancel(
                () -> result.completeExceptionally(failure(SearchErrorCategory.CANCELLED, "search was cancelled")));
        call.whenComplete((response, error) -> {
            if (result.isDone()) {
                return;
            }
            Instant finishedAt = clock.instant();
            if (error != null) {
                SearchProviderException failure = translate(error);
                if (context.isExpired(finishedAt) && failure.getCategory() != SearchErrorCategory.CANCELLED) {
                    failure = SearchProviderException.builder(id, SearchErrorCategory.TIMEOUT,
                            "deadline passed while waiting for SerpApi").cause(failure).build();
                }
                result.completeExceptionally(failure);
                return;
            }
            if (context.isExpired(finishedAt)) {
                result.completeExceptionally(failure(SearchErrorCategory.TIMEOUT,
                        "deadline passed while waiting for SerpApi"));
                return;
            }
            try {
                if (!response.isSuccessful()) {
                    result.completeExceptionally(SerpApiErrors.fromResponse(id, response));
                } else {
                    result.complete(responseMapper.map(request, prepared, response, startedAt, finishedAt));
                }
            } catch (SearchProviderException e) {
                result.completeExceptionally(e);
            } catch (RuntimeException e) {
                result.completeExceptionally(SearchProviderException.builder(id,
                        SearchErrorCategory.RESPONSE_INVALID, "could not map the SerpApi response").cause(e)
                        .providerStatusCode(response.getStatus()).build());
            }
        });
        if (closed.get()) {
            result.completeExceptionally(failure(SearchErrorCategory.CANCELLED, "provider " + id + " is closed"));
        }
        return result;
    }

    private char[] resolveKey() {
        Optional<SecretValue> secret;
        try {
            secret = credentials.resolve(credentialKey);
        } catch (RuntimeException e) {
            throw SearchProviderException.builder(descriptor.getId(), SearchErrorCategory.CONFIGURATION,
                    "credential '" + credentialKey + "' could not be resolved").cause(e).build();
        }
        if (!secret.isPresent()) {
            throw failure(SearchErrorCategory.CONFIGURATION, "credential '" + credentialKey + "' is not configured");
        }
        try (SecretValue value = secret.get()) {
            if (value.isEmpty()) {
                throw failure(SearchErrorCategory.CONFIGURATION, "credential '" + credentialKey + "' is empty");
            }
            return value.use(chars -> new String(chars).trim().toCharArray());
        }
    }

    private Duration timeout(SearchContext context, Instant now) {
        Optional<Duration> remaining = context.getRemainingTime(now);
        Duration timeout = options.getTimeout();
        if (remaining.isPresent() && remaining.get().compareTo(timeout) < 0) {
            timeout = remaining.get().isZero() || remaining.get().isNegative() ? Duration.ofMillis(1)
                    : remaining.get();
        }
        return timeout;
    }

    private List<SearchWarning> ignoredFeatureWarnings(CapabilityCheck check) {
        List<SearchWarning> warnings = new ArrayList<SearchWarning>();
        for (CapabilityViolation violation : check.getOptionalViolations()) {
            warnings.add(SearchWarning.of(SearchWarning.FEATURE_IGNORED, violation.getMessage(), descriptor.getId()));
        }
        return warnings;
    }

    private SearchProviderException translate(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof SearchProviderException) {
            return (SearchProviderException) cause;
        }
        if (cause instanceof HttpTransportException) {
            return SerpApiErrors.fromTransport(descriptor.getId(), (HttpTransportException) cause);
        }
        if (cause instanceof java.util.concurrent.CancellationException) {
            return new SearchProviderException(descriptor.getId(), SearchErrorCategory.CANCELLED,
                    "HTTP call was cancelled");
        }
        return SearchProviderException.builder(descriptor.getId(), SearchErrorCategory.UNAVAILABLE,
                "HTTP transport failed: " + cause.getClass().getSimpleName()).build();
    }

    private SearchProviderException failure(SearchErrorCategory category, String message) {
        return new SearchProviderException(descriptor.getId(), category, message);
    }

    private static String messages(List<CapabilityViolation> violations) {
        StringBuilder text = new StringBuilder();
        for (CapabilityViolation violation : violations) {
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append(violation.getMessage());
        }
        return text.toString();
    }

    private static <T> CompletableFuture<T> failed(Throwable failure) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(failure);
        return future;
    }

    /** Completes running searches with {@code CANCELLED}; never closes transport or credentials. Idempotent. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (CompletableFuture<SearchResponse> future : new ArrayList<CompletableFuture<SearchResponse>>(running)) {
            future.completeExceptionally(failure(SearchErrorCategory.CANCELLED,
                    "provider " + descriptor.getId() + " was closed"));
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public String toString() {
        return "SerpApiSearchProvider{id=" + descriptor.getId() + ", baseUrl=" + options.getBaseUrl()
                + ", credential=" + credentialKey + ", closed=" + closed.get() + '}';
    }

    public static final class Builder {
        private ProviderId id = DEFAULT_ID;
        private HttpTransport transport;
        private CredentialProvider credentials;
        private CredentialKey credentialKey;
        private SerpApiSearchOptions options = SerpApiSearchOptions.defaults();
        private Clock clock = Clock.systemUTC();

        private Builder() {
        }

        /** Instance id; default {@code serpapi}. Several instances need distinct ids. */
        public Builder id(ProviderId id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        /** Required. Borrowed: the provider never closes it (ADR-017). */
        public Builder transport(HttpTransport transport) {
            this.transport = Objects.requireNonNull(transport, "transport");
            return this;
        }

        /** Required (or {@link #apiKey(String)}). Borrowed and queried per search, so rotated keys take effect. */
        public Builder credentials(CredentialProvider credentials) {
            this.credentials = Objects.requireNonNull(credentials, "credentials");
            return this;
        }

        /** Key of the SerpApi key; default {@code <id>.api-key}. */
        public Builder credentialKey(CredentialKey credentialKey) {
            this.credentialKey = Objects.requireNonNull(credentialKey, "credentialKey");
            return this;
        }

        /** Convenience for tests and simple hosts: a fixed key instead of a {@link CredentialProvider}. */
        public Builder apiKey(String apiKey) {
            Objects.requireNonNull(apiKey, "apiKey");
            if (apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException("apiKey must not be blank");
            }
            final char[] secret = apiKey.trim().toCharArray();
            this.credentials = key -> Optional.of(SecretValue.of(secret.clone()));
            this.credentialKey = null;
            return this;
        }

        public Builder options(SerpApiSearchOptions options) {
            this.options = Objects.requireNonNull(options, "options");
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /** @throws IllegalStateException if transport or credentials are missing */
        public SerpApiSearchProvider build() {
            if (transport == null) {
                throw new IllegalStateException("transport is required");
            }
            if (credentials == null) {
                throw new IllegalStateException("credentials (or apiKey) are required");
            }
            return new SerpApiSearchProvider(this);
        }
    }
}
