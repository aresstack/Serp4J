package com.aresstack.serp4j.provider.argos;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.ResultProvenance;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.data.SerpPosition;
import com.aresstack.serp4j.core.model.ext.RawPayload;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.ElementId;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.model.metadata.ExecutionInfo;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.port.ProviderDescriptor;
import com.aresstack.serp4j.core.port.ProviderKind;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.core.port.capability.CapabilityCheck;
import com.aresstack.serp4j.core.port.capability.CapabilityViolation;
import com.aresstack.serp4j.core.port.capability.ResponseFeature;
import com.aresstack.serp4j.core.port.capability.SearchCapabilities;
import com.aresstack.serp4j.http.HttpTransportException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link SearchProvider} for the AskAI/Argos browser web search (provider type {@code argos}, kind
 * {@link ProviderKind#BROWSER}).
 *
 * <p>The provider only depends on the port {@link ArgosWebSearch}; how the search reaches Argos (today: MCP to the
 * browser sidecar, {@link McpArgosWebSearch}) is an adapter decision. Argos chooses the engine itself from its own
 * browser configuration (DuckDuckGo, Bing, custom URL template); the declared engines only describe what the host
 * configured. The engine of a response is taken from the host of the first engine attempt that delivered hits.</p>
 *
 * <p>Supported: query text and result limit (web vertical). Argos has no offset, locale, safe search or freshness
 * parameters; requests that use them optionally get a {@code feature_ignored} warning, requests that require them
 * fail with {@link SearchErrorCategory#UNSUPPORTED}.</p>
 */
public final class ArgosSearchProvider implements SearchProvider {

    /** Provider type used by {@link ArgosSearchProviderFactory}. */
    public static final String PROVIDER_TYPE = "argos";
    /** Warning code: one engine attempt of Argos did not deliver hits. */
    public static final String WARNING_ENGINE_ATTEMPT = "engine_attempt";
    /** Warning code: Argos reports a pending manual challenge (CAPTCHA, consent page); never bypassed. */
    public static final String WARNING_MANUAL_CHALLENGE = "manual_challenge";
    /** Engines declared when the builder names none: the engine catalog of AskAI's browser search. */
    public static final List<SearchEngineId> DEFAULT_ENGINES =
            java.util.Collections.unmodifiableList(Arrays.asList(SearchEngineId.DUCKDUCKGO, SearchEngineId.BING));
    /** Upper bound of hits Argos returns per search (its {@code searchResultLimit} is configured on the host). */
    public static final int DEFAULT_MAX_RESULTS = 50;

    private final ProviderDescriptor descriptor;
    private final ArgosWebSearch search;
    private final boolean ownsSearch;
    private final Clock clock;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ArgosSearchProvider(Builder builder) {
        SearchCapabilities.Builder capabilities = SearchCapabilities.builder()
                .features(SearchFeature.QUERY_TEXT, SearchFeature.MAX_RESULTS)
                .responseFeatures(ResponseFeature.ORGANIC_RESULTS)
                .maxResultsPerRequest(builder.maxResults);
        for (SearchEngineId engine : builder.engines) {
            capabilities.target(engine, SearchVertical.WEB);
        }
        this.descriptor = ProviderDescriptor.builder(builder.id, PROVIDER_TYPE, ProviderKind.BROWSER,
                        capabilities.build())
                .displayName(builder.displayName != null ? builder.displayName : "AskAI/Argos web search")
                .build();
        this.search = builder.search;
        this.ownsSearch = builder.ownsSearch;
        this.clock = builder.clock;
    }

    /**
     * @param id     provider id
     * @param search the Argos search port; borrowed unless {@link Builder#ownsSearch(boolean)} is set
     */
    public static Builder builder(ProviderId id, ArgosWebSearch search) {
        return new Builder(id, search);
    }

    @Override
    public ProviderDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public CompletableFuture<SearchResponse> search(final SearchRequest request, SearchContext context) {
        if (request == null) {
            throw new NullPointerException("request must not be null");
        }
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }
        if (closed.get()) {
            return failed(failure(SearchErrorCategory.CANCELLED, "provider " + descriptor.getId() + " is closed"));
        }
        final CapabilityCheck check = descriptor.getCapabilities().check(request);
        if (!check.getRequiredViolations().isEmpty()) {
            return failed(failure(SearchErrorCategory.UNSUPPORTED, "unsupported: " + check.getRequiredViolations()));
        }
        if (context.getCancellation().isCancelled()) {
            return failed(failure(SearchErrorCategory.CANCELLED, "search was cancelled"));
        }
        final Instant startedAt = clock.instant();
        if (context.isExpired(startedAt)) {
            return failed(failure(SearchErrorCategory.TIMEOUT, "deadline passed before the search started"));
        }
        Duration timeout = context.getRemainingTime(startedAt).orElse(null);
        final CompletableFuture<SearchResponse> result = new CompletableFuture<SearchResponse>();
        CompletableFuture<ArgosSearchOutput> call;
        try {
            call = search.search(request.getQuery(), timeout);
        } catch (RuntimeException e) {
            return failed(translate(e));
        }
        call.whenComplete((output, error) -> {
            if (error != null) {
                result.completeExceptionally(translate(McpArgosWebSearch.unwrap(error)));
                return;
            }
            try {
                result.complete(toResponse(output, request, check, startedAt));
            } catch (SearchProviderException e) {
                result.completeExceptionally(e);
            } catch (RuntimeException e) {
                result.completeExceptionally(SearchProviderException.builder(descriptor.getId(),
                        SearchErrorCategory.RESPONSE_INVALID, "cannot map Argos output: " + e.getMessage())
                        .cause(e).build());
            }
        });
        context.getCancellation().onCancel(() -> result.completeExceptionally(
                failure(SearchErrorCategory.CANCELLED, "search was cancelled")));
        return result;
    }

    SearchResponse toResponse(ArgosSearchOutput output, SearchRequest request, CapabilityCheck check,
                              Instant startedAt) {
        ProviderId id = descriptor.getId();
        if (output.getItems().isEmpty() && !output.getChallenges().isEmpty()) {
            throw SearchProviderException.builder(id, SearchErrorCategory.BLOCKED,
                    "Argos reports a manual challenge: " + output.getChallenges().get(0)).build();
        }
        SearchEngineId engine = engineOf(output, request);
        SearchData.Builder data = SearchData.builder();
        int limit = request.getPaging().getLimit();
        int rank = 0;
        for (ArgosSearchOutput.Item item : output.getItems()) {
            if (rank >= limit) {
                break;
            }
            rank++;
            SearchResultItem.Builder hit = SearchResultItem.builder(ElementId.of(id.getCode() + "-" + rank),
                            ResultType.ORGANIC, item.getUrl())
                    .position(SerpPosition.ofRankInGroup(rank))
                    .provenance(ResultProvenance.builder(id, engine)
                            .originalId(ElementId.of(item.getId()))
                            .build());
            if (!item.getTitle().isEmpty()) {
                hit.title(item.getTitle());
            }
            if (!item.getSnippet().isEmpty()) {
                hit.snippet(item.getSnippet());
            }
            data.result(hit.build());
        }
        List<SearchWarning> warnings = new ArrayList<SearchWarning>();
        for (CapabilityViolation violation : check.getOptionalViolations()) {
            warnings.add(SearchWarning.of(SearchWarning.FEATURE_IGNORED, violation.getMessage(), id));
        }
        boolean anyFailedAttempt = false;
        Set<SearchEngineId> deliveringEngines = new LinkedHashSet<SearchEngineId>();
        for (ArgosSearchOutput.Attempt attempt : output.getAttempts()) {
            if (attempt.isOrganicResults()) {
                deliveringEngines.add(engineForHost(attempt.getHost()));
            } else {
                anyFailedAttempt = true;
                warnings.add(SearchWarning.of(WARNING_ENGINE_ATTEMPT,
                        attempt.getHost() + ": " + attempt.getOutcome(), id));
            }
        }
        if (deliveringEngines.size() > 1) {
            warnings.add(SearchWarning.of(SearchWarning.MAPPING_INCOMPLETE, "hits come from several engines "
                    + deliveringEngines + "; Argos does not attribute single hits, all are attributed to " + engine,
                    id));
        }
        for (String challenge : output.getChallenges()) {
            warnings.add(SearchWarning.of(WARNING_MANUAL_CHALLENGE, challenge, id));
        }
        Instant now = clock.instant();
        SearchMetadata.Builder metadata = SearchMetadata
                .forProvider(id, engine, SearchVertical.WEB, QueryInfo.of(request.getQuery()))
                .execution(ExecutionInfo.builder().startedAt(startedAt)
                        .duration(now.isBefore(startedAt) ? Duration.ZERO : Duration.between(startedAt, now))
                        .attempts(1).build())
                .complete(!(output.getItems().isEmpty() && anyFailedAttempt))
                .warnings(warnings);
        if (request.getRawPayloadPolicy() == RawPayloadPolicy.ALWAYS && !output.getRawText().isEmpty()) {
            metadata.rawPayload(RawPayload.ofText("text/plain; charset=UTF-8", output.getRawText()));
        }
        return SearchResponse.of(data.build(), metadata.build());
    }

    private SearchEngineId engineOf(ArgosSearchOutput output, SearchRequest request) {
        for (ArgosSearchOutput.Attempt attempt : output.getAttempts()) {
            if (attempt.isOrganicResults()) {
                return engineForHost(attempt.getHost());
            }
        }
        if (!output.getProviderHosts().isEmpty()) {
            return engineForHost(output.getProviderHosts().get(0));
        }
        if (request.getTarget().isPresent()) {
            return request.getTarget().get().getEngine();
        }
        if (request.getEngine().isPresent()) {
            return request.getEngine().get();
        }
        return descriptor.getCapabilities().getTargets().iterator().next().getEngine();
    }

    /** Maps a search engine host ({@code html.duckduckgo.com}, {@code www.bing.com}, ...) to an engine id. */
    static SearchEngineId engineForHost(String host) {
        String h = host.toLowerCase(Locale.ROOT).trim();
        int port = h.indexOf(':');
        if (port >= 0) {
            h = h.substring(0, port);
        }
        String[][] known = {
                {"duckduckgo.", "duckduckgo"}, {"bing.", "bing"}, {"google.", "google"}, {"brave.", "brave"},
                {"yahoo.", "yahoo"}, {"yandex.", "yandex"}, {"baidu.", "baidu"}, {"naver.", "naver"},
                {"seznam.", "seznam"}, {"startpage.", "startpage"}, {"ecosia.", "ecosia"}, {"qwant.", "qwant"}};
        for (String[] entry : known) {
            if (h.startsWith(entry[0]) || h.contains("." + entry[0])) {
                return SearchEngineId.of(entry[1]);
            }
        }
        if (h.startsWith("www.")) {
            h = h.substring(4);
        }
        String code = h.replaceAll("[^a-z0-9._-]", "-").replaceAll("^[^a-z0-9]+", "");
        return SearchEngineId.of(code.isEmpty() ? "unknown" : code);
    }

    private SearchProviderException translate(Throwable error) {
        ProviderId id = descriptor.getId();
        if (error instanceof SearchProviderException) {
            return (SearchProviderException) error;
        }
        if (error instanceof HttpTransportException) {
            HttpTransportException transportError = (HttpTransportException) error;
            SearchErrorCategory category;
            switch (transportError.getKind()) {
                case TIMEOUT:
                    category = SearchErrorCategory.TIMEOUT;
                    break;
                case CANCELLED:
                    category = SearchErrorCategory.CANCELLED;
                    break;
                default:
                    category = SearchErrorCategory.UNAVAILABLE;
            }
            return SearchProviderException.builder(id, category,
                    "Argos browser sidecar not reachable: " + transportError.getMessage()).cause(error).build();
        }
        if (error instanceof ArgosWebSearchException) {
            ArgosWebSearchException argosError = (ArgosWebSearchException) error;
            SearchProviderException.Builder builder;
            switch (argosError.getKind()) {
                case HTTP_STATUS:
                    int status = argosError.getHttpStatus();
                    builder = SearchProviderException.builder(id, categoryForStatus(status), argosError.getMessage());
                    if (status > 0) {
                        builder.providerStatusCode(status);
                    }
                    break;
                case TOOL_ERROR:
                    builder = SearchProviderException.builder(id, SearchErrorCategory.UNAVAILABLE,
                            argosError.getMessage());
                    break;
                default:
                    builder = SearchProviderException.builder(id, SearchErrorCategory.RESPONSE_INVALID,
                            argosError.getMessage());
            }
            return builder.cause(error).build();
        }
        return SearchProviderException.builder(id, SearchErrorCategory.INTERNAL,
                "Argos search failed: " + error).cause(error).build();
    }

    private static SearchErrorCategory categoryForStatus(int status) {
        if (status == 401 || status == 403) {
            return SearchErrorCategory.AUTHENTICATION;
        }
        if (status == 404) {
            return SearchErrorCategory.CONFIGURATION;
        }
        if (status == 429) {
            return SearchErrorCategory.RATE_LIMITED;
        }
        if (status >= 500) {
            return SearchErrorCategory.UNAVAILABLE;
        }
        return SearchErrorCategory.RESPONSE_INVALID;
    }

    private SearchProviderException failure(SearchErrorCategory category, String message) {
        return new SearchProviderException(descriptor.getId(), category, message);
    }

    private static CompletableFuture<SearchResponse> failed(Throwable failure) {
        CompletableFuture<SearchResponse> future = new CompletableFuture<SearchResponse>();
        future.completeExceptionally(failure);
        return future;
    }

    /** Closes the {@link ArgosWebSearch} only if this provider owns it (ADR-017). Idempotent. */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && ownsSearch) {
            search.close();
        }
    }

    @Override
    public String toString() {
        return "ArgosSearchProvider{id=" + descriptor.getId() + ", search=" + search + '}';
    }

    public static final class Builder {
        private final ProviderId id;
        private final ArgosWebSearch search;
        private String displayName;
        private final Set<SearchEngineId> engines = new LinkedHashSet<SearchEngineId>(DEFAULT_ENGINES);
        private int maxResults = DEFAULT_MAX_RESULTS;
        private boolean ownsSearch;
        private Clock clock = Clock.systemUTC();

        private Builder(ProviderId id, ArgosWebSearch search) {
            if (id == null) {
                throw new NullPointerException("id must not be null");
            }
            if (search == null) {
                throw new NullPointerException("search must not be null");
            }
            this.id = id;
            this.search = search;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** Engines the host configured in Argos; replaces {@link #DEFAULT_ENGINES}. */
        public Builder engines(Collection<SearchEngineId> engines) {
            if (engines == null || engines.isEmpty()) {
                throw new IllegalArgumentException("engines must not be empty");
            }
            this.engines.clear();
            this.engines.addAll(engines);
            return this;
        }

        public Builder maxResults(int maxResults) {
            if (maxResults < 1) {
                throw new IllegalArgumentException("maxResults must be positive but was " + maxResults);
            }
            this.maxResults = maxResults;
            return this;
        }

        /** {@code true}: {@link ArgosSearchProvider#close()} also closes the search port. Default {@code false}. */
        public Builder ownsSearch(boolean ownsSearch) {
            this.ownsSearch = ownsSearch;
            return this;
        }

        public Builder clock(Clock clock) {
            if (clock == null) {
                throw new NullPointerException("clock must not be null");
            }
            this.clock = clock;
            return this;
        }

        public ArgosSearchProvider build() {
            return new ArgosSearchProvider(this);
        }
    }
}
