package com.aresstack.serp4j.provider.brave;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
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
import com.aresstack.serp4j.provider.brave.internal.BraveErrors;
import com.aresstack.serp4j.provider.brave.internal.BraveRequestFactory;
import com.aresstack.serp4j.provider.brave.internal.BraveResponseMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
 * {@link SearchProvider} for the Brave Search API, {@code GET /res/v1/web/search} (Paket B, architecture.md §5.5).
 *
 * <pre>{@code
 * SearchProvider brave = BraveSearchProvider.builder()
 *         .transport(transport)                                    // owned by the caller (ADR-017)
 *         .credentials(credentials)                                // resolves "brave.api-key"
 *         .options(BraveSearchOptions.builder().defaultCountry("DE").defaultSearchLanguage("de").build())
 *         .build();
 * }</pre>
 *
 * <ul>
 *   <li>Target {@code brave/web}. {@code count} ≤ 20; Brave's {@code offset} is a page index 0–9, so the request
 *       offset must be a multiple of its limit.</li>
 *   <li>The token is resolved per search from the {@link CredentialProvider} and sent only as
 *       {@code X-Subscription-Token} (sensitive header); it never appears in URLs, messages or {@code toString()}.</li>
 *   <li>Errors: {@code QUOTA_LIMITED} → {@code QUOTA_EXHAUSTED}, {@code RATE_LIMITED}/429 → {@code RATE_LIMITED},
 *       422 → {@code INVALID_REQUEST}, 401/403 → {@code AUTHENTICATION}, 5xx → {@code UNAVAILABLE}.</li>
 *   <li>{@link #close()} completes running searches with {@code CANCELLED} and never closes the transport or the
 *       credential provider.</li>
 * </ul>
 * Thread-safe.
 */
public final class BraveSearchProvider implements SearchProvider {

    public static final String PROVIDER_TYPE = "brave";
    public static final ProviderId DEFAULT_ID = ProviderId.of(PROVIDER_TYPE);
    /** Secret name; the default credential key is {@code <provider id>.api-key}. */
    public static final String API_KEY_SECRET = "api-key";

    private final ProviderDescriptor descriptor;
    private final HttpTransport transport;
    private final CredentialProvider credentials;
    private final CredentialKey credentialKey;
    private final BraveSearchOptions options;
    private final Clock clock;
    private final BraveRequestFactory requestFactory;
    private final BraveResponseMapper responseMapper;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<CompletableFuture<SearchResponse>> running =
            Collections.newSetFromMap(new ConcurrentHashMap<CompletableFuture<SearchResponse>, Boolean>());

    private BraveSearchProvider(Builder builder) {
        ProviderId id = builder.id;
        this.descriptor = ProviderDescriptor.builder(id, PROVIDER_TYPE, ProviderKind.CLOUD_API, capabilities())
                .displayName("Brave Search")
                .build();
        this.transport = builder.transport;
        this.credentials = builder.credentials;
        this.credentialKey = builder.credentialKey != null ? builder.credentialKey
                : CredentialKey.of(id, API_KEY_SECRET);
        this.options = builder.options;
        this.clock = builder.clock;
        this.requestFactory = new BraveRequestFactory(id, options, clock);
        this.responseMapper = new BraveResponseMapper(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** What the Brave web search endpoint can do (Brave reference §3.3). */
    public static SearchCapabilities capabilities() {
        return SearchCapabilities.builder()
                .target(SearchEngineId.BRAVE, SearchVertical.WEB)
                .features(SearchFeature.QUERY_TEXT, SearchFeature.PAGINATION_OFFSET, SearchFeature.PAGINATION_PAGE,
                        SearchFeature.MAX_RESULTS, SearchFeature.COUNTRY, SearchFeature.LANGUAGE,
                        SearchFeature.GEO_LOCATION, SearchFeature.FRESHNESS, SearchFeature.SAFE_SEARCH)
                .responseFeatures(ResponseFeature.ORGANIC_RESULTS, ResponseFeature.SERP_FEATURES,
                        ResponseFeature.KNOWLEDGE_GRAPH)
                .maxResultsPerRequest(BraveRequestFactory.MAX_COUNT)
                .maxOffset(BraveRequestFactory.MAX_PAGE_INDEX * BraveRequestFactory.MAX_COUNT)
                .build();
    }

    @Override
    public ProviderDescriptor getDescriptor() {
        return descriptor;
    }

    public BraveSearchOptions getOptions() {
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
                    "Brave cannot serve this request: " + messages(check.getRequiredViolations())));
        }
        if (context.getCancellation().isCancelled()) {
            return failed(failure(SearchErrorCategory.CANCELLED, "search was cancelled"));
        }
        final Instant startedAt = clock.instant();
        if (context.isExpired(startedAt)) {
            return failed(failure(SearchErrorCategory.TIMEOUT, "deadline passed before the search started"));
        }

        final BraveRequestFactory.Prepared prepared;
        try {
            prepared = requestFactory.prepare(request, resolveToken(), timeout(context, startedAt),
                    ignoredFeatureWarnings(check));
        } catch (SearchProviderException e) {
            return failed(e);
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
                            "deadline passed while waiting for Brave").cause(failure).build();
                }
                result.completeExceptionally(failure);
                return;
            }
            try {
                if (!response.isSuccessful()) {
                    result.completeExceptionally(BraveErrors.fromResponse(id, response));
                } else {
                    result.complete(responseMapper.map(request, prepared, response, startedAt, finishedAt));
                }
            } catch (SearchProviderException e) {
                result.completeExceptionally(e);
            } catch (RuntimeException e) {
                result.completeExceptionally(SearchProviderException.builder(id,
                        SearchErrorCategory.RESPONSE_INVALID, "could not map the Brave response").cause(e)
                        .providerStatusCode(response.getStatus()).build());
            }
        });
        if (closed.get()) {
            result.completeExceptionally(failure(SearchErrorCategory.CANCELLED, "provider " + id + " is closed"));
        }
        return result;
    }

    private String resolveToken() {
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
            return value.use(String::new);
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
            return BraveErrors.fromTransport(descriptor.getId(), (HttpTransportException) cause);
        }
        if (cause instanceof java.util.concurrent.CancellationException) {
            return SearchProviderException.builder(descriptor.getId(), SearchErrorCategory.CANCELLED,
                    "HTTP call was cancelled").cause(cause).build();
        }
        return SearchProviderException.builder(descriptor.getId(), SearchErrorCategory.UNAVAILABLE,
                "HTTP transport failed: " + cause.getClass().getSimpleName()).cause(cause).build();
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
        return "BraveSearchProvider{id=" + descriptor.getId() + ", baseUrl=" + options.getBaseUrl()
                + ", credential=" + credentialKey + ", closed=" + closed.get() + '}';
    }

    public static final class Builder {
        private ProviderId id = DEFAULT_ID;
        private HttpTransport transport;
        private CredentialProvider credentials;
        private CredentialKey credentialKey;
        private BraveSearchOptions options = BraveSearchOptions.defaults();
        private Clock clock = Clock.systemUTC();

        private Builder() {
        }

        /** Instance id; default {@code brave}. Several instances need distinct ids. */
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

        /** Key of the subscription token; default {@code <id>.api-key}. */
        public Builder credentialKey(CredentialKey credentialKey) {
            this.credentialKey = Objects.requireNonNull(credentialKey, "credentialKey");
            return this;
        }

        /** Convenience for tests and simple hosts: a fixed token instead of a {@link CredentialProvider}. */
        public Builder apiKey(String apiKey) {
            Objects.requireNonNull(apiKey, "apiKey");
            if (apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException("apiKey must not be blank");
            }
            final char[] token = apiKey.trim().toCharArray();
            this.credentials = key -> Optional.of(SecretValue.of(token.clone()));
            this.credentialKey = null;
            return this;
        }

        public Builder options(BraveSearchOptions options) {
            this.options = Objects.requireNonNull(options, "options");
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /** @throws IllegalStateException if transport or credentials are missing */
        public BraveSearchProvider build() {
            if (transport == null) {
                throw new IllegalStateException("transport is required");
            }
            if (credentials == null) {
                throw new IllegalStateException("credentials (or apiKey) are required");
            }
            return new BraveSearchProvider(this);
        }
    }
}
