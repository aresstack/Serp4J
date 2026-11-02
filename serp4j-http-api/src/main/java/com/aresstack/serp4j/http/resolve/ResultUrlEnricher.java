package com.aresstack.serp4j.http.resolve;

import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.ext.ExtensionKey;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.SerpFeature;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.port.SearchContext;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Provider-neutral post-processing step after a provider's JSON mapping: finds result URLs a {@link UrlResolver}
 * supports (e.g. Google {@code /goto} redirects via {@link GoogleGotoUrlResolver}), resolves each distinct URL once –
 * at most {@link #MAX_CONCURRENT} at a time – and replaces {@code url} and {@code domain} of the results whose URL
 * was resolved. Nested URLs (site links, sources of answer boxes, featured snippets, generated answers and question
 * answers, knowledge-panel links, see {@code ResponseUrls}) are resolved in the same run – the limits below apply
 * to all URLs of one response together – and traced in {@link #NESTED_URL_RESOLUTIONS}. Everything else of the
 * response (order, ids, positions, provenance, metadata, raw payload) stays as mapped. Used by the DataForSEO and SerpApi providers; no network access happens in a mapper.
 *
 * <p>Traceability on every touched result:</p>
 * <ul>
 *   <li>{@link #ORIGINAL_URL} ({@code serp4j:original_url}) – the URL as delivered by the provider, on resolved
 *       results;</li>
 *   <li>{@link #URL_RESOLUTION} ({@code serp4j:url_resolution}) – {@link #RESOLVED} or the failure reason
 *       ({@link UrlResolutionException#getReason()}, {@code cancelled}) on every result whose URL was attempted.
 *       A result with a failure status still carries the unresolved redirect URL and must not be treated as a
 *       verified source.</li>
 * </ul>
 *
 * <p>Enrichment never fails a search: every failure leaves the affected URL untouched and is summarised in one
 * {@link #WARNING_CODE} warning per response. Each request is bounded by the per-request timeout and by the
 * remaining search deadline; no request is started after the deadline. Cancelling the returned future cancels
 * running resolutions and starts no new ones. No threads, queues or caches beyond the current response.</p>
 */
public final class ResultUrlEnricher {

    /** Maximum number of concurrent resolutions per search. */
    public static final int MAX_CONCURRENT = 4;
    /** Warning code for results whose redirect URL stayed unresolved. */
    public static final String WARNING_CODE = "url_resolution_failed";
    /** The URL as delivered by the provider, kept on results whose URL was replaced. */
    public static final ExtensionKey<String> ORIGINAL_URL = ExtensionKey.of("serp4j", "original_url", String.class);
    /** Resolution status of a result whose URL was attempted: {@link #RESOLVED} or the failure reason. */
    public static final ExtensionKey<String> URL_RESOLUTION =
            ExtensionKey.of("serp4j", "url_resolution", String.class);
    /**
     * Trace of nested URLs (site links, feature sources, knowledge-panel links) on their owning result or feature:
     * an array of objects {@code {field, original_url, url_resolution, url?}} – {@code field} names the location
     * (e.g. {@code siteLinks[0]}, {@code source}, {@code questions[2].source}), {@code url_resolution} is
     * {@link #RESOLVED} or the failure reason and {@code url} the resolved target. A nested URL with a failure
     * status stays the unresolved redirect and must not be treated as a verified source.
     */
    public static final ExtensionKey<StructuredValue> NESTED_URL_RESOLUTIONS =
            ExtensionKey.of("serp4j", "nested_url_resolutions", StructuredValue.class);
    /** {@link #URL_RESOLUTION} value of a successfully resolved result. */
    public static final String RESOLVED = "resolved";

    private final ProviderId provider;
    private final UrlResolver resolver;
    private final Duration requestTimeout;
    private final Clock clock;

    /**
     * @param provider       provider named in the warning
     * @param resolver       decides which URLs are resolved and how
     * @param requestTimeout upper bound of one resolution, positive
     * @param clock          clock the search deadline is measured with
     */
    public ResultUrlEnricher(ProviderId provider, UrlResolver resolver, Duration requestTimeout, Clock clock) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
    }

    /**
     * Completes {@code target} with the enriched {@code mapped} response, or with {@code mapped} itself if the
     * enrichment fails unexpectedly. If {@code target} completes first (cancellation, provider closed), running
     * resolutions are cancelled. This is the whole glue a provider needs after its mapper.
     */
    public void completeWith(final CompletableFuture<SearchResponse> target, final SearchResponse mapped,
                             final SearchContext context) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(mapped, "mapped");
        Objects.requireNonNull(context, "context");
        final CompletableFuture<SearchResponse> enriched;
        try {
            enriched = enrich(mapped, context::getRemainingTime);
        } catch (RuntimeException e) {
            target.complete(mapped);
            return;
        }
        target.whenComplete((r, e) -> {
            if (!enriched.isDone()) {
                enriched.cancel(true);
            }
        });
        enriched.whenComplete((r, e) -> target.complete(e == null ? r : mapped));
    }

    /**
     * Enriches {@code response}; completes normally unless the returned future is cancelled.
     *
     * @param remaining remaining search time at a given instant; empty = no deadline
     */
    public CompletableFuture<SearchResponse> enrich(final SearchResponse response,
                                             final java.util.function.Function<Instant, Optional<Duration>> remaining) {
        List<String> all = new ArrayList<String>();
        for (SearchResultItem item : response.getData().getResults()) {
            all.add(item.getUrl());
            ResponseUrls.collect(item, all);
        }
        for (SerpFeature feature : response.getData().getFeatures()) {
            ResponseUrls.collect(feature, all);
        }
        List<String> distinct = new ArrayList<String>();
        for (String url : all) {
            if (resolver.supports(url) && !distinct.contains(url)) {
                distinct.add(url);
            }
        }
        if (distinct.isEmpty()) {
            return CompletableFuture.completedFuture(response);
        }
        final Run run = new Run(distinct, remaining);
        final CompletableFuture<SearchResponse> result = run.done.thenApply(outcome -> apply(response, outcome));
        result.whenComplete((r, e) -> {
            if (e != null) {
                run.cancel();
            }
        });
        run.start();
        return result;
    }

    private SearchResponse apply(SearchResponse response, final Run run) {
        SearchData.Builder data = SearchData.builder();
        boolean changed = false;
        ResponseUrls.Mapping nested = new ResponseUrls.Mapping() {
            @Override
            public String map(String url, String field, ResponseUrls.Owner owner) {
                String target = run.resolved.get(url);
                String failure = run.failedUrls.get(url);
                if (target != null) {
                    owner.add(field, url, RESOLVED, target);
                } else if (failure != null) {
                    owner.add(field, url, failure, null);
                }
                return target;
            }
        };
        for (SearchResultItem original : response.getData().getResults()) {
            SearchResultItem item = ResponseUrls.rewriteNested(original, nested);
            changed |= item != original;
            String target = run.resolved.get(item.getUrl());
            String failure = run.failedUrls.get(item.getUrl());
            if (target != null) {
                SearchResultItem.Builder b = item.toBuilder().url(target).extensions(item.getExtensions()
                        .with(ORIGINAL_URL, item.getUrl()).with(URL_RESOLUTION, RESOLVED));
                Optional<String> host = host(target);
                if (host.isPresent()) {
                    b.domain(host.get());
                }
                data.result(b.build());
                changed = true;
            } else if (failure != null) {
                data.result(item.toBuilder().extensions(item.getExtensions().with(URL_RESOLUTION, failure)).build());
                changed = true;
            } else {
                data.result(item);
            }
        }
        for (SerpFeature feature : response.getData().getFeatures()) {
            SerpFeature rewritten = ResponseUrls.rewrite(feature, nested);
            changed |= rewritten != feature;
            data.feature(rewritten);
        }
        if (!changed && run.failures.isEmpty()) {
            return response;
        }
        SearchResponse.Builder out = SearchResponse.builder(changed ? data.build() : response.getData());
        if (response.getMetadata().isPresent()) {
            SearchMetadata metadata = response.getMetadata().get();
            out.metadata(run.failures.isEmpty() ? metadata : metadata.toBuilder().warning(warning(run)).build());
        }
        if (response.getAnalysis().isPresent()) {
            out.analysis(response.getAnalysis().get());
        }
        return out.build();
    }

    private SearchWarning warning(Run run) {
        Map<String, Integer> byReason = new TreeMap<String, Integer>(run.failures);
        int total = 0;
        StringBuilder reasons = new StringBuilder();
        for (Map.Entry<String, Integer> e : byReason.entrySet()) {
            total += e.getValue();
            reasons.append(reasons.length() == 0 ? "" : ", ").append(e.getKey()).append('=').append(e.getValue());
        }
        return SearchWarning.of(WARNING_CODE, total + " of " + run.urls.size()
                + " redirect URLs could not be resolved and were kept unchanged (" + reasons + ")", provider);
    }

    static Optional<String> host(String url) {
        try {
            return Optional.ofNullable(new URI(url).getHost());
        } catch (java.net.URISyntaxException e) {
            return Optional.empty();
        }
    }

    /** State of one enrichment: bounded by the number of distinct URLs of one response. */
    private final class Run {
        final List<String> urls;
        final java.util.function.Function<Instant, Optional<Duration>> remaining;
        final Deque<String> pending;
        final Map<String, String> resolved = Collections.synchronizedMap(new LinkedHashMap<String, String>());
        final Map<String, Integer> failures = Collections.synchronizedMap(new LinkedHashMap<String, Integer>());
        final Map<String, String> failedUrls = Collections.synchronizedMap(new LinkedHashMap<String, String>());
        final List<CompletableFuture<String>> inFlight = new ArrayList<CompletableFuture<String>>();
        final CompletableFuture<Run> done = new CompletableFuture<Run>();
        int open;
        boolean cancelled;

        Run(List<String> urls, java.util.function.Function<Instant, Optional<Duration>> remaining) {
            this.urls = urls;
            this.remaining = remaining;
            this.pending = new ArrayDeque<String>(urls);
            this.open = urls.size();
        }

        void start() {
            for (int i = 0; i < MAX_CONCURRENT; i++) {
                next();
            }
        }

        void next() {
            final String url;
            final Duration timeout;
            synchronized (this) {
                if (cancelled || pending.isEmpty()) {
                    return;
                }
                url = pending.poll();
                Optional<Duration> left = remaining.apply(clock.instant());
                timeout = left.isPresent() && left.get().compareTo(requestTimeout) < 0 ? left.get() : requestTimeout;
            }
            if (timeout.isNegative() || timeout.isZero()) {
                finished(url, null, new UrlResolutionException(UrlResolutionException.TIMEOUT, "deadline passed"));
                return;
            }
            CompletableFuture<String> call;
            try {
                call = resolver.resolve(url, timeout);
            } catch (RuntimeException e) {
                finished(url, null, e);
                return;
            }
            synchronized (this) {
                if (cancelled) {
                    call.cancel(true);
                    return;
                }
                inFlight.add(call);
            }
            final CompletableFuture<String> started = call;
            call.whenComplete((target, error) -> {
                synchronized (this) {
                    inFlight.remove(started);
                }
                finished(url, target, error);
            });
        }

        void finished(String url, String target, Throwable error) {
            if (error == null && target != null) {
                resolved.put(url, target);
            } else {
                String reason = reason(error);
                failedUrls.put(url, reason);
                synchronized (failures) {
                    Integer n = failures.get(reason);
                    failures.put(reason, n == null ? 1 : n + 1);
                }
            }
            boolean last;
            synchronized (this) {
                last = --open == 0;
            }
            if (last) {
                done.complete(this);
            } else {
                next();
            }
        }

        void cancel() {
            List<CompletableFuture<String>> running;
            synchronized (this) {
                cancelled = true;
                pending.clear();
                running = new ArrayList<CompletableFuture<String>>(inFlight);
            }
            for (CompletableFuture<String> f : running) {
                f.cancel(true);
            }
            done.cancel(false);
        }
    }

    private static String reason(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof UrlResolutionException) {
            return ((UrlResolutionException) cause).getReason();
        }
        if (cause instanceof java.util.concurrent.CancellationException) {
            return "cancelled";
        }
        return UrlResolutionException.NETWORK;
    }

}
