package com.aresstack.serp4j.engine.google.browser;

import com.aresstack.serp4j.browser.BrowserException;
import com.aresstack.serp4j.browser.BrowserRuntime;
import com.aresstack.serp4j.browser.BrowserSession;
import com.aresstack.serp4j.browser.BrowserSessionOptions;
import com.aresstack.serp4j.browser.BrowserTimeoutException;
import com.aresstack.serp4j.browser.NavigationRequest;
import com.aresstack.serp4j.browser.NavigationResult;
import com.aresstack.serp4j.browser.ObstaclePolicy;
import com.aresstack.serp4j.browser.PageSnapshot;
import com.aresstack.serp4j.browser.ProfileMode;
import com.aresstack.serp4j.browser.SnapshotOptions;
import com.aresstack.serp4j.browser.UrlPolicy;
import com.aresstack.serp4j.browser.UrlPolicyViolationException;
import com.aresstack.serp4j.browser.WaitUntil;
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
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.resolve.GoogleGotoUrlResolver;
import com.aresstack.serp4j.http.resolve.ResultUrlEnricher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link SearchProvider} {@code google-browser} for {@code google/web}: opens one ephemeral session on the host's
 * {@link BrowserRuntime}, loads {@code https://www.google.com/search?q=…} (never {@code udm=50}), takes an HTML
 * snapshot and parses it with the shared {@link GoogleSerpHtmlParser}. Same {@code SearchData} as
 * {@code google-http} (ADR-018).
 *
 * <ul>
 *   <li>Session per search: ephemeral profile, URL policy {@code google.com}/{@code *.google.com}, obstacle policy
 *       {@link ObstaclePolicy#failFast()}; the session is always closed. Nothing is clicked: consent, challenge and
 *       sign-in pages without results fail with {@code BLOCKED} and the detail {@code google_obstacle}
 *       (ADR-016). A consent overlay above rendered results does not hinder the search.</li>
 *   <li>Browser errors: timeout to {@code TIMEOUT}, runtime unavailable or no capacity to {@code UNAVAILABLE},
 *       URL policy violation to {@code BLOCKED}.</li>
 *   <li>Optional {@link HttpTransport}: resolves Google {@code /goto} result URLs with {@link ResultUrlEnricher}
 *       (extensions {@code serp4j:original_url}/{@code serp4j:url_resolution}). Without it the {@code /goto} URLs stay
 *       as they are, AskAI treats them as unresolved.</li>
 *   <li>The runtime and the transport belong to the host; {@link #close()} cancels running searches, closes their
 *       sessions and stops the provider's worker threads (ADR-017).</li>
 * </ul>
 * Experimental, not published in 0.x. Thread-safe.
 */
public final class GoogleBrowserSearchProvider implements SearchProvider {

    public static final String PROVIDER_TYPE = "google-browser";
    public static final ProviderId DEFAULT_ID = ProviderId.of(PROVIDER_TYPE);
    public static final int RESULTS_PER_PAGE = 10;
    public static final Duration DEFAULT_NAVIGATION_TIMEOUT = Duration.ofSeconds(30);
    /** Rendered Google pages are far below this; a larger page is reported as truncated. */
    public static final int MAX_HTML_CHARS = 8 * 1024 * 1024;
    /** Warning: the HTML snapshot was truncated, later results may be missing. */
    public static final String WARNING_SNAPSHOT_TRUNCATED = SearchWarning.PARTIAL_RESULTS;

    private static final String PATH = "browser";

    private final ProviderDescriptor descriptor;
    private final BrowserRuntime runtime;
    private final GoogleUrlBuilder urlBuilder;
    private final GoogleSerpHtmlParser parser = new GoogleSerpHtmlParser();
    private final GoogleSerpMapper mapper;
    private final Duration navigationTimeout;
    private final Clock clock;
    private final ResultUrlEnricher urlEnricher;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Running> running = Collections.newSetFromMap(new ConcurrentHashMap<Running, Boolean>());

    private GoogleBrowserSearchProvider(Builder builder) {
        this.descriptor = ProviderDescriptor.builder(builder.id, PROVIDER_TYPE, ProviderKind.BROWSER, capabilities())
                .displayName("Google (browser, experimental)")
                .build();
        this.runtime = builder.runtime;
        this.urlBuilder = new GoogleUrlBuilder(GoogleUrlBuilder.DEFAULT_BASE_URL, builder.defaultLanguage,
                builder.defaultCountry);
        this.mapper = new GoogleSerpMapper(builder.id);
        this.navigationTimeout = builder.navigationTimeout;
        this.clock = builder.clock;
        this.urlEnricher = builder.transport == null ? null : new ResultUrlEnricher(builder.id,
                new GoogleGotoUrlResolver(builder.transport), GoogleGotoUrlResolver.DEFAULT_TIMEOUT, clock);
        final String threadPrefix = "serp4j-" + builder.id.getCode() + "-";
        this.executor = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, threadPrefix + count.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    public static Builder builder(BrowserRuntime runtime) {
        return new Builder(runtime);
    }

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
        ProviderId id = descriptor.getId();
        if (closed.get()) {
            return failed(failure(SearchErrorCategory.CANCELLED, "provider " + id + " is closed"));
        }
        final CapabilityCheck check = descriptor.getCapabilities().check(request);
        if (!check.getRequiredViolations().isEmpty()) {
            return failed(failure(SearchErrorCategory.UNSUPPORTED,
                    "google-browser cannot serve this request: " + check.getRequiredViolations()));
        }
        if (context.getCancellation().isCancelled()) {
            return failed(failure(SearchErrorCategory.CANCELLED, "search was cancelled"));
        }
        final Instant startedAt = clock.instant();
        if (context.isExpired(startedAt)) {
            return failed(failure(SearchErrorCategory.TIMEOUT, "deadline passed before the search started"));
        }
        final String url;
        try {
            url = urlBuilder.build(request);
        } catch (IllegalArgumentException e) {
            return failed(failure(SearchErrorCategory.INVALID_REQUEST, e.getMessage()));
        }
        if (GoogleUrlBuilder.isAiModeUrl(url)) {
            return failed(failure(SearchErrorCategory.INTERNAL, "AI Mode URL must never be requested"));
        }
        final Running job = new Running();
        running.add(job);
        job.result.whenComplete((r, e) -> {
            running.remove(job);
            job.closeSession();
        });
        context.getCancellation().onCancel(() -> job.result.completeExceptionally(
                failure(SearchErrorCategory.CANCELLED, "search was cancelled")));
        try {
            executor.execute(() -> job.run(request, context, check, url, startedAt));
        } catch (RejectedExecutionException e) {
            job.result.completeExceptionally(failure(SearchErrorCategory.CANCELLED, "provider " + id + " is closed"));
        }
        return job.result;
    }

    private final class Running {
        final CompletableFuture<SearchResponse> result = new CompletableFuture<SearchResponse>();
        final AtomicReference<BrowserSession> session = new AtomicReference<BrowserSession>();

        void run(SearchRequest request, SearchContext context, CapabilityCheck check, String url, Instant startedAt) {
            if (result.isDone()) {
                return;
            }
            ProviderId id = descriptor.getId();
            try {
                BrowserSessionOptions.Builder options = BrowserSessionOptions.builder()
                        .profileMode(ProfileMode.ephemeral())
                        .urlPolicy(UrlPolicy.allowHosts("google.com", "*.google.com"))
                        .obstaclePolicy(ObstaclePolicy.failFast())
                        .defaultTimeout(timeout(context))
                        .maxNavigations(1);
                Locale locale = locale(request);
                if (locale != null) {
                    options.locale(locale);
                }
                BrowserSession opened = runtime.openSession(options.build());
                session.set(opened);
                if (result.isDone()) {
                    closeSession();
                    return;
                }
                NavigationResult navigation = opened.navigate(NavigationRequest.to(url)
                        .waitUntil(WaitUntil.DOM_CONTENT_LOADED).timeout(timeout(context)));
                PageSnapshot snapshot = opened.snapshot(SnapshotOptions.urlAndTitle().withHtml()
                        .maxChars(MAX_HTML_CHARS));
                closeSession();
                String html = snapshot.getHtml().orElse("");
                GoogleSerpPage page = parser.parse(html);
                GoogleObstacle obstacle = GoogleObstacleDetector.fromPage(navigation.getFinalUrl(), page);
                if (obstacle != GoogleObstacle.NONE) {
                    throw GoogleSearchFailures.obstacle(id, obstacle, PATH);
                }
                if (navigation.getHttpStatus().isPresent() && navigation.getHttpStatus().getAsInt() == 429) {
                    throw SearchProviderException.builder(id, SearchErrorCategory.RATE_LIMITED,
                            "Google answered HTTP 429").providerStatusCode(429).build();
                }
                if (page.getOrganicResults().isEmpty() && !page.isStructureRecognized()) {
                    throw GoogleSearchFailures.unrecognized(id, PATH);
                }
                List<SearchWarning> warnings = new ArrayList<SearchWarning>();
                for (CapabilityViolation violation : check.getOptionalViolations()) {
                    warnings.add(SearchWarning.of(SearchWarning.FEATURE_IGNORED, violation.getMessage(), id));
                }
                if (snapshot.isHtmlTruncated()) {
                    warnings.add(SearchWarning.of(WARNING_SNAPSHOT_TRUNCATED,
                            "HTML snapshot truncated at " + MAX_HTML_CHARS + " characters", id));
                }
                SearchResponse mapped = mapper.map(page, request, url, html, startedAt, clock.instant(), warnings);
                if (urlEnricher == null) {
                    result.complete(mapped);
                } else {
                    urlEnricher.completeWith(result, mapped, context);
                }
            } catch (SearchProviderException e) {
                result.completeExceptionally(e);
            } catch (BrowserException e) {
                result.completeExceptionally(translate(e));
            } catch (RuntimeException e) {
                result.completeExceptionally(SearchProviderException.builder(id, SearchErrorCategory.INTERNAL,
                        "Google browser search failed: " + e.getClass().getSimpleName()).cause(e).build());
            } finally {
                closeSession();
            }
        }

        void closeSession() {
            BrowserSession s = session.getAndSet(null);
            if (s != null) {
                try {
                    s.close();
                } catch (RuntimeException ignored) {
                    // closing is best effort; the runtime reclaims lost sessions
                }
            }
        }
    }

    private Duration timeout(SearchContext context) {
        Duration remaining = context.getRemainingTime(clock.instant()).orElse(navigationTimeout);
        if (remaining.isNegative() || remaining.isZero()) {
            return Duration.ofMillis(1);
        }
        return remaining.compareTo(navigationTimeout) < 0 ? remaining : navigationTimeout;
    }

    private static Locale locale(SearchRequest request) {
        if (!request.getLocale().isPresent() || !request.getLocale().get().getLanguageCode().isPresent()) {
            return null;
        }
        String language = request.getLocale().get().getLanguageCode().get();
        String country = request.getLocale().get().getCountryCode().orElse("");
        return new Locale(language, country);
    }

    private SearchProviderException translate(BrowserException e) {
        ProviderId id = descriptor.getId();
        SearchErrorCategory category;
        if (e instanceof BrowserTimeoutException) {
            category = SearchErrorCategory.TIMEOUT;
        } else if (e instanceof UrlPolicyViolationException) {
            category = SearchErrorCategory.BLOCKED;
        } else {
            // BrowserUnavailableException, BrowserCapacityException, lost sessions and the rest
            category = SearchErrorCategory.UNAVAILABLE;
        }
        return SearchProviderException.builder(id, category, "browser: " + e.getClass().getSimpleName() + ": "
                + e.getMessage()).cause(e).build();
    }

    private SearchProviderException failure(SearchErrorCategory category, String message) {
        return new SearchProviderException(descriptor.getId(), category, message);
    }

    private static CompletableFuture<SearchResponse> failed(Throwable failure) {
        CompletableFuture<SearchResponse> future = new CompletableFuture<SearchResponse>();
        future.completeExceptionally(failure);
        return future;
    }

    /** Cancels running searches, closes their sessions and stops the worker threads; never closes the runtime. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (Running job : running) {
            job.result.completeExceptionally(failure(SearchErrorCategory.CANCELLED,
                    "provider " + descriptor.getId() + " was closed"));
        }
        executor.shutdown();
    }

    @Override
    public String toString() {
        return "GoogleBrowserSearchProvider{id=" + descriptor.getId() + ", runtime=" + runtime + '}';
    }

    public static final class Builder {
        private final BrowserRuntime runtime;
        private ProviderId id = DEFAULT_ID;
        private String defaultLanguage;
        private String defaultCountry;
        private HttpTransport transport;
        private Duration navigationTimeout = DEFAULT_NAVIGATION_TIMEOUT;
        private Clock clock = Clock.systemUTC();

        private Builder(BrowserRuntime runtime) {
            this.runtime = Objects.requireNonNull(runtime, "runtime");
        }

        public Builder id(ProviderId id) {
            this.id = Objects.requireNonNull(id, "id");
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

        /** Transport for resolving {@code /goto} result URLs; {@code null} (default) leaves them unresolved. */
        public Builder gotoResolutionTransport(HttpTransport transport) {
            this.transport = transport;
            return this;
        }

        public Builder navigationTimeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.navigationTimeout = timeout;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public GoogleBrowserSearchProvider build() {
            return new GoogleBrowserSearchProvider(this);
        }
    }
}
