package com.aresstack.serp4j.engine.google.http;

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
import com.aresstack.serp4j.engine.google.GoogleObstacle;
import com.aresstack.serp4j.engine.google.GoogleObstacleDetector;
import com.aresstack.serp4j.engine.google.GoogleSearchFailures;
import com.aresstack.serp4j.engine.google.GoogleSerpHtmlParser;
import com.aresstack.serp4j.engine.google.GoogleSerpMapper;
import com.aresstack.serp4j.engine.google.GoogleSerpPage;
import com.aresstack.serp4j.engine.google.GoogleUrlBuilder;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.http.resolve.GoogleGotoUrlResolver;
import com.aresstack.serp4j.http.resolve.ResultUrlEnricher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link SearchProvider} {@code google-http} for {@code google/web}: one cookie-free {@code GET /search} without
 * {@code udm=50} (no AI Mode), parsed with the shared {@link GoogleSerpHtmlParser}.
 *
 * <ul>
 *   <li>Redirects are not followed: a redirect to {@code consent.google.com}, a JavaScript-only page
 *       ({@code /httpservice/retry/enablejs}), {@code /sorry/} or a sign-in fail with {@code BLOCKED} and the detail
 *       {@code google_obstacle}. There is no retry, no cookie and no consent click (ADR-016).</li>
 *   <li>429 to {@code RATE_LIMITED}, 403 to {@code BLOCKED}, 5xx to {@code UNAVAILABLE}; a 200 page without results
 *       and without the result container to {@code RESPONSE_INVALID} (layout changed). A recognised page without
 *       results is an empty, successful answer.</li>
 *   <li>Google {@code /goto} result URLs are resolved like in the SerpApi provider ({@link ResultUrlEnricher},
 *       extensions {@code serp4j:original_url}/{@code serp4j:url_resolution}); off via
 *       {@link Builder#resolveGoogleGotoUrls(boolean)}.</li>
 *   <li>{@link #close()} ends running searches with {@code CANCELLED} and never closes the transport (ADR-017).</li>
 * </ul>
 * Experimental, not published in 0.x. Thread-safe.
 */
public final class GoogleHttpSearchProvider implements SearchProvider {

    public static final String PROVIDER_TYPE = "google-http";
    public static final ProviderId DEFAULT_ID = ProviderId.of(PROVIDER_TYPE);
    /** Results per Google page; {@code num} is not honoured any more. */
    public static final int RESULTS_PER_PAGE = 10;
    /** Neutral default User-Agent; the host may set its own. */
    public static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (compatible; Serp4J)";
    public static final Duration DEFAULT_URL_RESOLVE_TIMEOUT = GoogleGotoUrlResolver.DEFAULT_TIMEOUT;

    private static final String PATH = "HTTP";

    private final ProviderDescriptor descriptor;
    private final HttpTransport transport;
    private final GoogleUrlBuilder urlBuilder;
    private final GoogleSerpHtmlParser parser = new GoogleSerpHtmlParser();
    private final GoogleSerpMapper mapper;
    private final String userAgent;
    private final Clock clock;
    private final ResultUrlEnricher urlEnricher;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<CompletableFuture<SearchResponse>> running =
            Collections.newSetFromMap(new ConcurrentHashMap<CompletableFuture<SearchResponse>, Boolean>());

    private GoogleHttpSearchProvider(Builder builder) {
        this.descriptor = ProviderDescriptor.builder(builder.id, PROVIDER_TYPE, ProviderKind.ENGINE_HTTP,
                        capabilities())
                .displayName("Google (HTTP, experimental)")
                .build();
        this.transport = builder.transport;
        this.urlBuilder = new GoogleUrlBuilder(builder.baseUrl, builder.defaultLanguage, builder.defaultCountry);
        this.mapper = new GoogleSerpMapper(builder.id);
        this.userAgent = builder.userAgent;
        this.clock = builder.clock;
        this.urlEnricher = builder.resolveGoogleGotoUrls
                ? new ResultUrlEnricher(builder.id, new GoogleGotoUrlResolver(transport), builder.urlResolveTimeout,
                clock)
                : null;
    }

    public static Builder builder(HttpTransport transport) {
        return new Builder(transport);
    }

    /** {@code google/web}, query, offset, limit up to ten, country and language. */
    public static SearchCapabilities capabilities() {
        return SearchCapabilities.builder()
                .target(SearchEngineId.GOOGLE, SearchVertical.WEB)
                .features(SearchFeature.QUERY_TEXT, SearchFeature.PAGINATION_OFFSET, SearchFeature.MAX_RESULTS,
                        SearchFeature.COUNTRY, SearchFeature.LANGUAGE, SearchFeature.RAW_RESPONSE)
                .responseFeatures(ResponseFeature.ORGANIC_RESULTS, ResponseFeature.RELATED_QUERIES,
                        ResponseFeature.RAW_PAYLOAD)
                .maxResultsPerRequest(RESULTS_PER_PAGE)
                .build();
    }

    @Override
    public ProviderDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public CompletableFuture<SearchResponse> search(final SearchRequest request, final SearchContext context) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        final ProviderId id = descriptor.getId();
        if (closed.get()) {
            return failed(failure(SearchErrorCategory.CANCELLED, "provider " + id + " is closed"));
        }
        final CapabilityCheck check = descriptor.getCapabilities().check(request);
        if (!check.getRequiredViolations().isEmpty()) {
            return failed(failure(SearchErrorCategory.UNSUPPORTED,
                    "google-http cannot serve this request: " + check.getRequiredViolations()));
        }
        if (context.getCancellation().isCancelled()) {
            return failed(failure(SearchErrorCategory.CANCELLED, "search was cancelled"));
        }
        final Instant startedAt = clock.instant();
        if (context.isExpired(startedAt)) {
            return failed(failure(SearchErrorCategory.TIMEOUT, "deadline passed before the search started"));
        }
        final String url;
        final HttpRequest httpRequest;
        try {
            url = urlBuilder.build(request);
            if (GoogleUrlBuilder.isAiModeUrl(url)) {
                throw new IllegalStateException("AI Mode URL must never be requested by google-http");
            }
            HttpRequest.Builder b = HttpRequest.get(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .followRedirects(false);
            if (request.getLocale().isPresent() && request.getLocale().get().getLanguageCode().isPresent()) {
                b.header("Accept-Language", request.getLocale().get().getLanguageCode().get());
            }
            Duration remaining = context.getRemainingTime(startedAt).orElse(null);
            if (remaining != null) {
                b.timeout(remaining);
            }
            httpRequest = b.build();
        } catch (IllegalArgumentException e) {
            return failed(failure(SearchErrorCategory.INVALID_REQUEST, e.getMessage()));
        } catch (RuntimeException e) {
            return failed(failure(SearchErrorCategory.INTERNAL, "could not build the Google request: "
                    + e.getClass().getSimpleName()));
        }

        final CompletableFuture<SearchResponse> result = new CompletableFuture<SearchResponse>();
        final CompletableFuture<HttpResponse> call;
        try {
            call = transport.execute(httpRequest);
        } catch (RuntimeException e) {
            return failed(translate(e));
        }
        running.add(result);
        result.whenComplete((r, e) -> {
            running.remove(result);
            if (!call.isDone()) {
                call.cancel(true);
            }
        });
        context.getCancellation().onCancel(
                () -> result.completeExceptionally(failure(SearchErrorCategory.CANCELLED, "search was cancelled")));
        call.whenComplete((response, error) -> {
            if (result.isDone()) {
                return;
            }
            if (error != null) {
                result.completeExceptionally(translate(error));
                return;
            }
            try {
                SearchResponse mapped = handle(response, request, check, url, startedAt);
                if (urlEnricher == null) {
                    result.complete(mapped);
                } else {
                    urlEnricher.completeWith(result, mapped, context);
                }
            } catch (SearchProviderException e) {
                result.completeExceptionally(e);
            } catch (RuntimeException e) {
                result.completeExceptionally(SearchProviderException.builder(id,
                        SearchErrorCategory.RESPONSE_INVALID, "could not parse the Google page").cause(e).build());
            }
        });
        if (closed.get()) {
            result.completeExceptionally(failure(SearchErrorCategory.CANCELLED, "provider " + id + " is closed"));
        }
        return result;
    }

    private SearchResponse handle(HttpResponse response, SearchRequest request, CapabilityCheck check, String url,
                                  Instant startedAt) {
        ProviderId id = descriptor.getId();
        int status = response.getStatus();
        if (status >= 300 && status < 400) {
            String location = response.getHeaders().getFirst("Location").orElse(null);
            GoogleObstacle obstacle = GoogleObstacleDetector.fromUrl(location);
            if (obstacle != GoogleObstacle.NONE) {
                throw withStatus(GoogleSearchFailures.obstacle(id, obstacle, PATH), status);
            }
            throw SearchProviderException.builder(id, SearchErrorCategory.RESPONSE_INVALID,
                    "unexpected Google redirect (HTTP " + status + ") to " + hostOf(location))
                    .providerStatusCode(status).build();
        }
        SearchProviderException byStatus = GoogleSearchFailures.forStatus(id, status);
        if (byStatus != null) {
            throw byStatus;
        }
        if (status != 200) {
            throw SearchProviderException.builder(id, SearchErrorCategory.RESPONSE_INVALID,
                    "Google answered HTTP " + status).providerStatusCode(status).build();
        }
        String html = response.getBodyAsText();
        GoogleSerpPage page = parser.parse(html);
        GoogleObstacle obstacle = GoogleObstacleDetector.fromPage(url, page);
        if (obstacle != GoogleObstacle.NONE) {
            throw withStatus(GoogleSearchFailures.obstacle(id, obstacle, PATH), status);
        }
        if (page.getOrganicResults().isEmpty() && !page.isStructureRecognized()) {
            throw GoogleSearchFailures.unrecognized(id, PATH);
        }
        List<SearchWarning> warnings = new ArrayList<SearchWarning>();
        for (CapabilityViolation violation : check.getOptionalViolations()) {
            warnings.add(SearchWarning.of(SearchWarning.FEATURE_IGNORED, violation.getMessage(), id));
        }
        return mapper.map(page, request, url, html, startedAt, clock.instant(), warnings);
    }

    private static SearchProviderException withStatus(SearchProviderException e, int status) {
        SearchProviderException.Builder b = SearchProviderException.builder(e.getProviderId(), e.getCategory(),
                e.getMessage()).retryable(e.isRetryable()).providerStatusCode(status);
        for (java.util.Map.Entry<String, String> detail : e.getDetails().entrySet()) {
            b.detail(detail.getKey(), detail.getValue());
        }
        return b.build();
    }

    private static String hostOf(String url) {
        if (url == null) {
            return "(no Location)";
        }
        try {
            String host = new java.net.URI(url).getHost();
            return host == null ? "(relative URL)" : host;
        } catch (java.net.URISyntaxException e) {
            return "(invalid URL)";
        }
    }

    private SearchProviderException translate(Throwable error) {
        ProviderId id = descriptor.getId();
        Throwable cause = error;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof SearchProviderException) {
            return (SearchProviderException) cause;
        }
        if (cause instanceof HttpTransportException) {
            HttpTransportException t = (HttpTransportException) cause;
            SearchErrorCategory category;
            switch (t.getKind()) {
                case TIMEOUT:
                    category = SearchErrorCategory.TIMEOUT;
                    break;
                case CANCELLED:
                    category = SearchErrorCategory.CANCELLED;
                    break;
                default:
                    category = SearchErrorCategory.UNAVAILABLE;
            }
            return SearchProviderException.builder(id, category, "Google not reachable: " + t.getKind())
                    .cause(t).build();
        }
        if (cause instanceof java.util.concurrent.CancellationException) {
            return failure(SearchErrorCategory.CANCELLED, "search was cancelled");
        }
        return SearchProviderException.builder(id, SearchErrorCategory.INTERNAL,
                "Google search failed: " + cause.getClass().getSimpleName()).cause(cause).build();
    }

    private SearchProviderException failure(SearchErrorCategory category, String message) {
        return new SearchProviderException(descriptor.getId(), category, message);
    }

    private static CompletableFuture<SearchResponse> failed(Throwable failure) {
        CompletableFuture<SearchResponse> future = new CompletableFuture<SearchResponse>();
        future.completeExceptionally(failure);
        return future;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (CompletableFuture<SearchResponse> pending : running) {
            pending.completeExceptionally(failure(SearchErrorCategory.CANCELLED,
                    "provider " + descriptor.getId() + " was closed"));
        }
    }

    @Override
    public String toString() {
        return "GoogleHttpSearchProvider{id=" + descriptor.getId() + ", " + urlBuilder + '}';
    }

    public static final class Builder {
        private final HttpTransport transport;
        private ProviderId id = DEFAULT_ID;
        private String baseUrl = GoogleUrlBuilder.DEFAULT_BASE_URL;
        private String defaultLanguage;
        private String defaultCountry;
        private String userAgent = DEFAULT_USER_AGENT;
        private boolean resolveGoogleGotoUrls = true;
        private Duration urlResolveTimeout = DEFAULT_URL_RESOLVE_TIMEOUT;
        private Clock clock = Clock.systemUTC();

        private Builder(HttpTransport transport) {
            this.transport = Objects.requireNonNull(transport, "transport");
        }

        public Builder id(ProviderId id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        /** Search endpoint; only for tests and mirrors. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            return this;
        }

        public Builder defaultLanguage(String language) {
            this.defaultLanguage = language;
            return this;
        }

        public Builder defaultCountry(String country) {
            this.defaultCountry = country;
            return this;
        }

        public Builder userAgent(String userAgent) {
            if (userAgent == null || userAgent.trim().isEmpty()) {
                throw new IllegalArgumentException("userAgent must not be blank");
            }
            this.userAgent = userAgent.trim();
            return this;
        }

        public Builder resolveGoogleGotoUrls(boolean resolve) {
            this.resolveGoogleGotoUrls = resolve;
            return this;
        }

        public Builder urlResolveTimeout(Duration timeout) {
            this.urlResolveTimeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public GoogleHttpSearchProvider build() {
            return new GoogleHttpSearchProvider(this);
        }
    }
}
