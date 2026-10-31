package com.aresstack.serp4j.core.port;

import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;

import java.util.concurrent.CompletableFuture;

/**
 * A single stateless search adapter (WP2 §5). It owns authentication, endpoints, provider parameters,
 * serialisation, error mapping, rate limits and pagination. It does <em>not</em> decide whether other providers
 * are called, whether a fallback runs or how results of several providers are merged – that is the application
 * layer's job.
 *
 * <p>Implementations must allow concurrent {@link #search} calls and follow the ownership rules documented on
 * {@link ProviderPort}: injected infrastructure is borrowed and never closed by the provider.</p>
 */
public interface SearchProvider extends ProviderPort {

    /**
     * Executes exactly one search.
     *
     * <p>Contract:</p>
     * <ul>
     * <li>Returns a future that completes with a {@link SearchResponse} (possibly with empty data) or fails with
     * exactly one {@link com.aresstack.serp4j.core.error.SearchProviderException}. Synchronously it throws only
     * {@link NullPointerException} or {@link IllegalArgumentException} for programming errors.</li>
     * <li>Checks the request against {@code getDescriptor().getCapabilities()}: an unsupported
     * {@link com.aresstack.serp4j.core.model.RequirementLevel#REQUIRED REQUIRED} aspect fails the future with
     * {@link com.aresstack.serp4j.core.error.SearchErrorCategory#UNSUPPORTED}; an unsupported
     * {@link com.aresstack.serp4j.core.model.RequirementLevel#OPTIONAL OPTIONAL} aspect is ignored and recorded as
     * a warning in the response metadata.</li>
     * <li>Honours {@link SearchContext#getDeadline()} and {@link SearchContext#getCancellation()}.</li>
     * <li>Fills only {@code SearchData} and {@code SearchMetadata}; never {@code SearchAnalysis}. The metadata
     * names this provider, its engine and vertical and the query (architecture.md §5.4).</li>
     * <li>Changes no global state; two equal requests are independent.</li>
     * </ul>
     *
     * @param request what to search
     * @param context how to execute it: deadline, cancellation, correlation, budget
     * @return the future response
     */
    CompletableFuture<SearchResponse> search(SearchRequest request, SearchContext context);
}
