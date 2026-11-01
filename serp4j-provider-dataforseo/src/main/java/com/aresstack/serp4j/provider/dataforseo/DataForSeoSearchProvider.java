package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.DeviceType;
import com.aresstack.serp4j.core.model.RequirementLevel;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.port.capability.CapabilityViolation;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.port.ProviderDescriptor;
import com.aresstack.serp4j.core.port.ProviderKind;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.core.port.capability.CapabilityCheck;
import com.aresstack.serp4j.core.port.capability.ResponseFeature;
import com.aresstack.serp4j.core.port.capability.SearchCapabilities;
import com.aresstack.serp4j.core.port.credential.CredentialKey;
import com.aresstack.serp4j.core.port.credential.CredentialProvider;
import com.aresstack.serp4j.core.port.credential.SecretValue;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.http.RequestAuthentication;

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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link SearchProvider} for the DataForSEO SERP API v3, organic live endpoints for Google and Bing.
 *
 * <p>Ownership (ADR-017): the {@link HttpTransport} and the {@link CredentialProvider} belong to the caller;
 * {@link #close()} never closes them. Credentials are resolved per request under the keys {@code login} and
 * {@code password} of the provider id (see {@link #loginKey(ProviderId)}) and sent only as Basic authentication
 * header. Status codes in the response body are evaluated (API reference §10): 40102 is an empty result,
 * 40106 a partial one; every other non-success code fails the future with a {@link SearchProviderException}.</p>
 */
public final class DataForSeoSearchProvider implements SearchProvider {

    /** Provider type used in descriptors and factory configuration. */
    public static final String PROVIDER_TYPE = "dataforseo";
    /** Default API base URL. */
    public static final String DEFAULT_BASE_URL = "https://api.dataforseo.com";
    /** Default location when the request names none: United States (2840). */
    public static final int DEFAULT_LOCATION_CODE = 2840;

    private final ProviderDescriptor descriptor;
    private final HttpTransport transport;
    private final CredentialProvider credentials;
    private final CredentialKey loginKey;
    private final CredentialKey passwordKey;
    private final String baseUrl;
    private final Clock clock;
    private final DataForSeoRequestMapper requestMapper;
    private final DataForSeoResponseMapper responseMapper;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<CompletableFuture<SearchResponse>> running =
            Collections.newSetFromMap(new ConcurrentHashMap<CompletableFuture<SearchResponse>, Boolean>());

    private DataForSeoSearchProvider(Builder b) {
        this.transport = b.transport;
        this.credentials = b.credentials;
        this.loginKey = b.loginKey != null ? b.loginKey : loginKey(b.id);
        this.passwordKey = b.passwordKey != null ? b.passwordKey : passwordKey(b.id);
        this.baseUrl = b.baseUrl.endsWith("/") ? b.baseUrl.substring(0, b.baseUrl.length() - 1) : b.baseUrl;
        this.clock = b.clock;
        this.descriptor = ProviderDescriptor.builder(b.id, PROVIDER_TYPE, ProviderKind.CLOUD_API, capabilities())
                .displayName("DataForSEO SERP API")
                .attribute("function", b.function.name().toLowerCase(java.util.Locale.ROOT))
                .build();
        this.requestMapper = new DataForSeoRequestMapper(b.id, b.function, b.defaultLocationCode, b.defaultLanguageCode);
        this.responseMapper = new DataForSeoResponseMapper(b.id);
    }

    /** Builder with the caller-owned transport and credentials. */
    public static Builder builder(HttpTransport transport, CredentialProvider credentials) {
        return new Builder(transport, credentials);
    }

    /** Default credential key of the API login: {@code <providerId>.login}. */
    public static CredentialKey loginKey(ProviderId id) {
        return CredentialKey.of(id, "login");
    }

    /** Default credential key of the API password: {@code <providerId>.password}. */
    public static CredentialKey passwordKey(ProviderId id) {
        return CredentialKey.of(id, "password");
    }

    /** Capabilities of the organic live endpoints. */
    public static SearchCapabilities capabilities() {
        return SearchCapabilities.builder()
                .target(SearchEngineId.GOOGLE, SearchVertical.WEB)
                .target(SearchEngineId.BING, SearchVertical.WEB)
                .features(SearchFeature.QUERY_TEXT, SearchFeature.PAGINATION_OFFSET, SearchFeature.MAX_RESULTS,
                        SearchFeature.COUNTRY, SearchFeature.LANGUAGE, SearchFeature.GEO_LOCATION,
                        SearchFeature.DEVICE, SearchFeature.RAW_RESPONSE)
                .responseFeatures(ResponseFeature.ORGANIC_RESULTS, ResponseFeature.SERP_FEATURES,
                        ResponseFeature.KNOWLEDGE_GRAPH, ResponseFeature.RELATED_QUERIES,
                        ResponseFeature.TOTAL_RESULT_COUNT, ResponseFeature.PROVIDER_USAGE,
                        ResponseFeature.RAW_PAYLOAD)
                .maxResultsPerRequest(100)
                .maxOffset(100)
                .build();
    }

    @Override
    public ProviderDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public CompletableFuture<SearchResponse> search(SearchRequest request, SearchContext context) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(context, "context must not be null");
        ProviderId id = descriptor.getId();
        if (closed.get()) {
            return failed(fail(SearchErrorCategory.CANCELLED, "provider " + id + " is closed"));
        }
        CapabilityCheck check = descriptor.getCapabilities().check(request);
        if (!check.getRequiredViolations().isEmpty()) {
            return failed(fail(SearchErrorCategory.UNSUPPORTED, "unsupported: " + check.getRequiredViolations()));
        }
        if (request.hasProviderOptions(id)
                && !request.getProviderOptions(id, DataForSeoOptions.class).isPresent()) {
            return failed(fail(SearchErrorCategory.INVALID_REQUEST, "provider options for " + id
                    + " must be DataForSeoOptions"));
        }
        final List<SearchWarning> warnings = new ArrayList<SearchWarning>();
        for (CapabilityViolation v : check.getOptionalViolations()) {
            warnings.add(SearchWarning.of(SearchWarning.FEATURE_IGNORED, v.getMessage(), id));
        }
        if (request.getDevice().isPresent() && request.getDevice().get() == DeviceType.TABLET) {
            if (request.getRequirementLevel(SearchFeature.DEVICE) == RequirementLevel.REQUIRED) {
                return failed(fail(SearchErrorCategory.UNSUPPORTED, "DataForSEO has no tablet device"));
            }
            warnings.add(SearchWarning.of(SearchWarning.FEATURE_IGNORED,
                    "DataForSEO has no tablet device; searched as mobile", id));
        }
        if (context.getCancellation().isCancelled()) {
            return failed(fail(SearchErrorCategory.CANCELLED, "search was cancelled"));
        }
        Instant startedAt = clock.instant();
        if (context.isExpired(startedAt)) {
            return failed(fail(SearchErrorCategory.TIMEOUT, "deadline passed before the search started"));
        }
        final SearchEngineId engine = request.getTarget().map(SearchTarget::getEngine).orElse(SearchEngineId.GOOGLE);
        final HttpRequest httpRequest;
        try {
            httpRequest = httpRequest(request, engine, context, startedAt);
        } catch (SearchProviderException e) {
            return failed(e);
        }

        final CompletableFuture<SearchResponse> future = new CompletableFuture<SearchResponse>();
        running.add(future);
        future.whenComplete((r, e) -> running.remove(future));
        context.getCancellation().onCancel(() ->
                future.completeExceptionally(fail(SearchErrorCategory.CANCELLED, "search was cancelled")));

        CompletableFuture<HttpResponse> call;
        try {
            call = transport.execute(httpRequest);
        } catch (RuntimeException e) {
            future.completeExceptionally(transportFailure(e));
            return future;
        }
        final CompletableFuture<HttpResponse> exchange = call;
        // Cancellation, deadline or close() abort this exchange only; the borrowed transport stays open (ADR-017).
        future.whenComplete((r, e) -> {
            if (e != null && !exchange.isDone()) {
                exchange.cancel(true);
            }
        });
        call.whenComplete((http, error) -> {
            if (future.isDone()) {
                return;
            }
            if (error != null) {
                future.completeExceptionally(transportFailure(error));
                return;
            }
            try {
                if (!http.isSuccessful()) {
                    future.completeExceptionally(responseMapper.errorFor(http));
                    return;
                }
                future.complete(responseMapper.map(http, request, engine, startedAt, clock.instant(), warnings));
            } catch (SearchProviderException e) {
                future.completeExceptionally(e);
            } catch (RuntimeException e) {
                future.completeExceptionally(SearchProviderException.builder(id, SearchErrorCategory.RESPONSE_INVALID,
                        "could not map DataForSEO response: " + e.getClass().getSimpleName()).cause(e).build());
            }
        });
        if (closed.get()) {
            future.completeExceptionally(fail(SearchErrorCategory.CANCELLED, "provider " + id + " is closed"));
        }
        return future;
    }

    private HttpRequest httpRequest(SearchRequest request, SearchEngineId engine, SearchContext context, Instant now) {
        DataForSeoFunction function = requestMapper.function(request);
        String body = requestMapper.body(request, engine);
        HttpRequest.Builder http = HttpRequest.post(baseUrl + requestMapper.path(engine, function))
                .jsonBody(body)
                .setHeader("Accept", "application/json");
        // Secret lifecycle: both SecretValues are closed after the header is built; the password never becomes
        // a String (the login has to, RequestAuthentication.basic takes it as user name).
        try (SecretValue login = resolve(loginKey); SecretValue password = resolve(passwordKey)) {
            RequestAuthentication.basic(login.use(String::new), password::getChars).apply(http);
        }
        Optional<Duration> remaining = context.getRemainingTime(now);
        if (remaining.isPresent()) {
            http.timeout(remaining.get());
        }
        return http.build();
    }

    private SecretValue resolve(CredentialKey key) {
        Optional<SecretValue> secret;
        try {
            secret = credentials.resolve(key);
        } catch (RuntimeException e) {
            throw SearchProviderException.builder(descriptor.getId(), SearchErrorCategory.CONFIGURATION,
                    "credential '" + key.getName() + "' could not be resolved").build();
        }
        if (!secret.isPresent()) {
            throw fail(SearchErrorCategory.CONFIGURATION, "credential '" + key.getName() + "' is not configured");
        }
        if (secret.get().isEmpty()) {
            secret.get().close();
            throw fail(SearchErrorCategory.CONFIGURATION, "credential '" + key.getName() + "' is empty");
        }
        return secret.get();
    }

    private SearchProviderException transportFailure(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        Optional<SearchProviderException> spe = SearchProviderException.unwrap(cause);
        if (spe.isPresent()) {
            return spe.get();
        }
        SearchErrorCategory category = SearchErrorCategory.UNAVAILABLE;
        String message = "HTTP transport failed";
        if (cause instanceof HttpTransportException) {
            HttpTransportException.Kind kind = ((HttpTransportException) cause).getKind();
            message = "HTTP transport failed (" + kind + ")";
            if (kind == HttpTransportException.Kind.TIMEOUT) {
                category = SearchErrorCategory.TIMEOUT;
            } else if (kind == HttpTransportException.Kind.CANCELLED) {
                category = SearchErrorCategory.CANCELLED;
            }
        }
        return SearchProviderException.builder(descriptor.getId(), category, message).cause(cause).build();
    }

    private SearchProviderException fail(SearchErrorCategory category, String message) {
        return new SearchProviderException(descriptor.getId(), category, message);
    }

    private static <T> CompletableFuture<T> failed(Throwable t) {
        CompletableFuture<T> f = new CompletableFuture<T>();
        f.completeExceptionally(t);
        return f;
    }

    /** Fails running searches with {@code CANCELLED}; never closes the injected transport or credentials. Idempotent. */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            for (CompletableFuture<SearchResponse> f : running) {
                f.completeExceptionally(fail(SearchErrorCategory.CANCELLED, "provider " + descriptor.getId() + " is closed"));
            }
        }
    }

    @Override
    public String toString() {
        return "DataForSeoSearchProvider{id=" + descriptor.getId() + ", baseUrl=" + baseUrl + '}';
    }

    /** Builder for {@link DataForSeoSearchProvider}. */
    public static final class Builder {
        private final HttpTransport transport;
        private final CredentialProvider credentials;
        private ProviderId id = ProviderId.of(PROVIDER_TYPE);
        private String baseUrl = DEFAULT_BASE_URL;
        private DataForSeoFunction function = DataForSeoFunction.ADVANCED;
        private int defaultLocationCode = DEFAULT_LOCATION_CODE;
        private String defaultLanguageCode = "en";
        private CredentialKey loginKey;
        private CredentialKey passwordKey;
        private Clock clock = Clock.systemUTC();

        private Builder(HttpTransport transport, CredentialProvider credentials) {
            this.transport = Objects.requireNonNull(transport, "transport");
            this.credentials = Objects.requireNonNull(credentials, "credentials");
        }

        public Builder id(ProviderId id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            String url = Objects.requireNonNull(baseUrl, "baseUrl").trim();
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                throw new IllegalArgumentException("baseUrl must be an http(s) URL");
            }
            if (url.contains("@") || url.contains("?")) {
                throw new IllegalArgumentException("baseUrl must not contain credentials or a query");
            }
            this.baseUrl = url;
            return this;
        }

        /** Default result function; {@link DataForSeoFunction#ADVANCED} unless set. */
        public Builder function(DataForSeoFunction function) {
            this.function = Objects.requireNonNull(function, "function");
            return this;
        }

        public Builder defaultLocationCode(int locationCode) {
            if (locationCode <= 0) {
                throw new IllegalArgumentException("locationCode must be positive");
            }
            this.defaultLocationCode = locationCode;
            return this;
        }

        public Builder defaultLanguageCode(String languageCode) {
            this.defaultLanguageCode = Objects.requireNonNull(languageCode, "languageCode").trim();
            return this;
        }

        public Builder loginKey(CredentialKey key) {
            this.loginKey = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder passwordKey(CredentialKey key) {
            this.passwordKey = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public DataForSeoSearchProvider build() {
            return new DataForSeoSearchProvider(this);
        }
    }
}
