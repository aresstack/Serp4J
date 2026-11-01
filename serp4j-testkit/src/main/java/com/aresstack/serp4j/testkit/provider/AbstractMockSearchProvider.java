package com.aresstack.serp4j.testkit.provider;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
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
import com.aresstack.serp4j.testkit.FakeSharedResource;
import com.aresstack.serp4j.testkit.SharedInfrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Common behaviour of {@link MockSearchProvider} and {@link ScriptedSearchProvider}: the full
 * {@link SearchProvider} contract around an {@link Outcome} chosen per call.
 *
 * <p>Order of checks per call: arguments ({@code NullPointerException}), closed ({@code CANCELLED}), required
 * capabilities ({@code UNSUPPORTED}), context cancelled ({@code CANCELLED}) or expired ({@code TIMEOUT}), use of the
 * borrowed infrastructure ({@code UNAVAILABLE} if it is closed), then the outcome. Unsupported optional aspects
 * become {@code feature_ignored} warnings in the metadata. Calls that are still running when the provider is closed
 * complete with {@code CANCELLED}.</p>
 */
public abstract class AbstractMockSearchProvider implements SearchProvider {

    private final ProviderDescriptor descriptor;
    private final SharedInfrastructure infrastructure;
    private final Duration defaultLatency;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger closeCalls = new AtomicInteger();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<SearchRequest> requests = new CopyOnWriteArrayList<SearchRequest>();
    private final Set<CompletableFuture<SearchResponse>> running =
            Collections.newSetFromMap(new ConcurrentHashMap<CompletableFuture<SearchResponse>, Boolean>());

    protected AbstractMockSearchProvider(AbstractBuilder<?> builder) {
        this.descriptor = ProviderDescriptor.builder(builder.id, builder.providerType, ProviderKind.MOCK,
                        builder.capabilities != null ? builder.capabilities : defaultCapabilities())
                .displayName(builder.displayName != null ? builder.displayName : "Mock provider " + builder.id)
                .build();
        this.infrastructure = builder.infrastructure;
        this.defaultLatency = builder.latency;
    }

    /**
     * Capabilities used when the builder names none: Google web and news, query text, offset paging, result count,
     * country and language, up to 50 organic results per request.
     */
    public static SearchCapabilities defaultCapabilities() {
        return SearchCapabilities.builder()
                .target(SearchEngineId.GOOGLE, SearchVertical.WEB)
                .target(SearchEngineId.GOOGLE, SearchVertical.NEWS)
                .features(SearchFeature.QUERY_TEXT, SearchFeature.PAGINATION_OFFSET, SearchFeature.MAX_RESULTS,
                        SearchFeature.COUNTRY, SearchFeature.LANGUAGE)
                .responseFeatures(ResponseFeature.ORGANIC_RESULTS)
                .maxResultsPerRequest(50)
                .build();
    }

    /**
     * Chooses what to do with a call that passed all checks.
     *
     * @param request   the request
     * @param callIndex 0-based number of this call among all calls that reached the outcome
     */
    protected abstract Outcome nextOutcome(SearchRequest request, int callIndex);

    @Override
    public final ProviderDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public final CompletableFuture<SearchResponse> search(final SearchRequest request, SearchContext context) {
        if (request == null) {
            throw new NullPointerException("request must not be null");
        }
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }
        requests.add(request);
        final ProviderId id = descriptor.getId();
        if (closed.get()) {
            return failed(failure(SearchErrorCategory.CANCELLED, "provider " + id + " is closed"));
        }
        final CapabilityCheck check = descriptor.getCapabilities().check(request);
        if (!check.getRequiredViolations().isEmpty()) {
            return failed(failure(SearchErrorCategory.UNSUPPORTED, "unsupported: " + check.getRequiredViolations()));
        }
        if (context.getCancellation().isCancelled()) {
            return failed(failure(SearchErrorCategory.CANCELLED, "search was cancelled"));
        }
        if (context.isExpired(Instant.now())) {
            return failed(failure(SearchErrorCategory.TIMEOUT, "deadline passed before the search started"));
        }
        if (infrastructure != null) {
            try {
                useInfrastructure();
            } catch (RuntimeException e) {
                return failed(SearchProviderException.builder(id, SearchErrorCategory.UNAVAILABLE,
                        "shared infrastructure unavailable").cause(e).build());
            }
        }
        final Outcome outcome = nextOutcome(request, calls.getAndIncrement());
        if (outcome == null) {
            throw new IllegalStateException("nextOutcome returned null");
        }
        final CompletableFuture<SearchResponse> future = new CompletableFuture<SearchResponse>();
        running.add(future);
        future.whenComplete((value, error) -> running.remove(future));
        final Instant startedAt = Instant.now();
        final Duration latency = outcome.latencyOr(defaultLatency);
        Supplier<Completion.Result<SearchResponse>> result = outcome.getKind() == Outcome.Kind.HANG ? null
                : new Supplier<Completion.Result<SearchResponse>>() {
                    @Override
                    public Completion.Result<SearchResponse> get() {
                        return resolve(outcome, request, check, startedAt);
                    }
                };
        Completion.deliver(future, result, latency, context, new Function<SearchErrorCategory, Throwable>() {
            @Override
            public Throwable apply(SearchErrorCategory category) {
                return failure(category, category == SearchErrorCategory.TIMEOUT
                        ? "deadline passed" : "search was cancelled");
            }
        });
        if (closed.get()) {
            future.completeExceptionally(failure(SearchErrorCategory.CANCELLED, "provider " + id + " is closed"));
        }
        return future;
    }

    private void useInfrastructure() {
        if (infrastructure instanceof FakeSharedResource) {
            ((FakeSharedResource) infrastructure).use();
        } else if (infrastructure.isClosed()) {
            throw new IllegalStateException(infrastructure + " is closed");
        }
    }

    private Completion.Result<SearchResponse> resolve(Outcome outcome, SearchRequest request, CapabilityCheck check,
                                                      Instant startedAt) {
        ProviderId id = descriptor.getId();
        switch (outcome.getKind()) {
            case FAILURE:
                SearchProviderException exception = outcome.getException() != null ? outcome.getException()
                        : failure(outcome.getCategory(), outcome.getMessage());
                return Completion.Result.failure(exception);
            case RESPONSE:
                return Completion.Result.success(outcome.getResponse());
            case DATA:
                return Completion.Result.success(respond(outcome.getData(), request, check, startedAt));
            case SYNTHETIC:
                return Completion.Result.success(respond(
                        SyntheticResults.organic(id, request, outcome.getSyntheticCount()), request, check, startedAt));
            default:
                throw new IllegalStateException("unexpected outcome " + outcome);
        }
    }

    private SearchResponse respond(SearchData data, SearchRequest request, CapabilityCheck check, Instant startedAt) {
        ProviderId id = descriptor.getId();
        SearchTarget target = resolveTarget(descriptor.getCapabilities(), request);
        List<SearchWarning> warnings = new ArrayList<SearchWarning>();
        for (CapabilityViolation violation : check.getOptionalViolations()) {
            warnings.add(SearchWarning.of(SearchWarning.FEATURE_IGNORED, violation.getMessage(), id));
        }
        Instant now = Instant.now();
        SearchMetadata metadata = SearchMetadata
                .forProvider(id, target.getEngine(), target.getVertical(), QueryInfo.of(request.getQuery()))
                .execution(ExecutionInfo.builder().startedAt(startedAt)
                        .duration(now.isBefore(startedAt) ? Duration.ZERO : Duration.between(startedAt, now))
                        .attempts(1).build())
                .warnings(warnings)
                .build();
        return SearchResponse.of(data, metadata);
    }

    /**
     * The target a request is served on: its explicit target, else the first declared target matching its engine
     * (if any) and vertical, else the first declared target with the request's vertical.
     */
    static SearchTarget resolveTarget(SearchCapabilities capabilities, SearchRequest request) {
        if (request.getTarget().isPresent()) {
            return request.getTarget().get();
        }
        SearchTarget fallback = null;
        for (SearchTarget target : capabilities.getTargets()) {
            if (!target.getVertical().equals(request.getVertical())) {
                continue;
            }
            if (!request.getEngine().isPresent() || request.getEngine().get().equals(target.getEngine())) {
                return target;
            }
            if (fallback == null) {
                fallback = target;
            }
        }
        if (fallback != null) {
            return SearchTarget.of(request.getEngine().orElse(fallback.getEngine()), request.getVertical());
        }
        SearchEngineId engine = request.getEngine().orElse(capabilities.getTargets().isEmpty()
                ? SearchEngineId.GOOGLE : capabilities.getTargets().iterator().next().getEngine());
        return SearchTarget.of(engine, request.getVertical());
    }

    private SearchProviderException failure(SearchErrorCategory category, String message) {
        return new SearchProviderException(descriptor.getId(), category, message);
    }

    private static CompletableFuture<SearchResponse> failed(Throwable failure) {
        CompletableFuture<SearchResponse> future = new CompletableFuture<SearchResponse>();
        future.completeExceptionally(failure);
        return future;
    }

    /**
     * Marks the provider closed and completes running calls with {@code CANCELLED}. Never touches the borrowed
     * infrastructure (ADR-017). Idempotent.
     */
    @Override
    public final void close() {
        closeCalls.incrementAndGet();
        if (closed.compareAndSet(false, true)) {
            for (CompletableFuture<SearchResponse> future : new ArrayList<CompletableFuture<SearchResponse>>(running)) {
                future.completeExceptionally(failure(SearchErrorCategory.CANCELLED,
                        "provider " + descriptor.getId() + " was closed"));
            }
        }
    }

    public final boolean isClosed() {
        return closed.get();
    }

    public final int getCloseCalls() {
        return closeCalls.get();
    }

    /** Every request passed to {@link #search}, in call order, including rejected ones. */
    public final List<SearchRequest> getRequests() {
        return Collections.unmodifiableList(new ArrayList<SearchRequest>(requests));
    }

    /** Number of calls passed to {@link #search}, including rejected ones. */
    public final int getSearchCount() {
        return requests.size();
    }

    /** Number of calls that passed all checks and received an outcome. */
    public final int getOutcomeCount() {
        return calls.get();
    }

    /** Number of calls currently waiting for their outcome. */
    public final int getRunningCount() {
        return running.size();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + descriptor.getId() + ", closed=" + closed.get() + '}';
    }

    /**
     * Shared builder options.
     *
     * @param <B> concrete builder type
     */
    public abstract static class AbstractBuilder<B extends AbstractBuilder<B>> {
        private final ProviderId id;
        private String providerType = "mock";
        private String displayName;
        private SearchCapabilities capabilities;
        private SharedInfrastructure infrastructure;
        private Duration latency = Duration.ZERO;

        protected AbstractBuilder(ProviderId id) {
            if (id == null) {
                throw new NullPointerException("id must not be null");
            }
            this.id = id;
        }

        protected abstract B self();

        /** Provider type reported in the descriptor; default {@code "mock"}. */
        public B providerType(String providerType) {
            if (providerType == null) {
                throw new NullPointerException("providerType must not be null");
            }
            this.providerType = providerType;
            return self();
        }

        public B displayName(String displayName) {
            this.displayName = displayName;
            return self();
        }

        /** Declared capabilities; default {@link #defaultCapabilities()}. */
        public B capabilities(SearchCapabilities capabilities) {
            if (capabilities == null) {
                throw new NullPointerException("capabilities must not be null");
            }
            if (capabilities.getConversational().isPresent()) {
                throw new IllegalArgumentException("a SearchProvider mock must not declare conversational capabilities"
                        + " (WP2 §6.2); use ScriptedConversationalProvider");
            }
            this.capabilities = capabilities;
            return self();
        }

        /**
         * Infrastructure the provider borrows (ADR-017): used once per call that passes the capability check,
         * never closed by the provider. A closed infrastructure makes calls fail with {@code UNAVAILABLE}.
         */
        public B infrastructure(SharedInfrastructure infrastructure) {
            this.infrastructure = infrastructure;
            return self();
        }

        /** Latency of outcomes that name none; default zero (complete synchronously). */
        public B latency(Duration latency) {
            if (latency == null) {
                throw new NullPointerException("latency must not be null");
            }
            if (latency.isNegative()) {
                throw new IllegalArgumentException("latency must not be negative: " + latency);
            }
            this.latency = latency;
            return self();
        }
    }
}
