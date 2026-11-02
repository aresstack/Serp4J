package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.ext.ExtensionKey;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.http.resolve.UrlResolutionException;
import com.aresstack.serp4j.http.resolve.UrlResolver;

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
 * Post-processing step after {@link DataForSeoResponseMapper}: finds result URLs a {@link UrlResolver} supports
 * (Google {@code /goto} redirects), resolves each distinct URL once – at most {@link #MAX_CONCURRENT} at a time –
 * and replaces {@code url} and {@code domain} of the results whose URL was resolved. Everything else of the
 * response (order, ids, positions, provenance, features, metadata, raw payload) stays as mapped; the original URL
 * is kept as extension {@link #ORIGINAL_URL}.
 *
 * <p>Enrichment never fails a search: every failure leaves the affected results untouched and is summarised in one
 * {@link #WARNING_CODE} warning per response. Each request is bounded by the per-request timeout and by the
 * remaining search deadline; no request is started after the deadline. Cancelling the returned future cancels
 * running resolutions and starts no new ones. No threads, queues or caches beyond the current response.</p>
 */
final class DataForSeoResultUrlEnricher {

    /** Maximum number of concurrent resolutions per search. */
    static final int MAX_CONCURRENT = 4;
    /** Warning code for results whose redirect URL stayed unresolved. */
    static final String WARNING_CODE = "url_resolution_failed";
    /** The URL as delivered by DataForSEO, kept on results whose URL was replaced. */
    static final ExtensionKey<String> ORIGINAL_URL = ExtensionKey.of("dataforseo", "original_url", String.class);

    private final ProviderId provider;
    private final UrlResolver resolver;
    private final Duration requestTimeout;
    private final Clock clock;

    DataForSeoResultUrlEnricher(ProviderId provider, UrlResolver resolver, Duration requestTimeout, Clock clock) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Enriches {@code response}; completes normally unless the returned future is cancelled.
     *
     * @param remaining remaining search time at a given instant; empty = no deadline
     */
    CompletableFuture<SearchResponse> enrich(final SearchResponse response,
                                             final java.util.function.Function<Instant, Optional<Duration>> remaining) {
        List<String> distinct = new ArrayList<String>();
        for (SearchResultItem item : response.getData().getResults()) {
            if (resolver.supports(item.getUrl()) && !distinct.contains(item.getUrl())) {
                distinct.add(item.getUrl());
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

    private SearchResponse apply(SearchResponse response, Run run) {
        SearchData.Builder data = SearchData.builder();
        boolean changed = false;
        for (SearchResultItem item : response.getData().getResults()) {
            String target = run.resolved.get(item.getUrl());
            if (target == null) {
                data.result(item);
                continue;
            }
            SearchResultItem.Builder b = item.toBuilder().url(target)
                    .extensions(item.getExtensions().with(ORIGINAL_URL, item.getUrl()));
            Optional<String> host = host(target);
            if (host.isPresent()) {
                b.domain(host.get());
            }
            data.result(b.build());
            changed = true;
        }
        data.features(response.getData().getFeatures());
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
                + " Google redirect URLs could not be resolved and were kept unchanged (" + reasons + ")", provider);
    }

    private static Optional<String> host(String url) {
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
