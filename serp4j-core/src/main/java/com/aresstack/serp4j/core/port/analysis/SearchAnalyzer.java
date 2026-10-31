package com.aresstack.serp4j.core.port.analysis;

import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.analysis.SearchAnalysis;
import com.aresstack.serp4j.core.port.SearchContext;

import java.util.concurrent.CompletableFuture;

/**
 * Optional port that derives a {@link SearchAnalysis} from a response (ADR-003, WP2 §12): intent, entities, topics,
 * metrics. Search providers never produce analyses; an orchestrator applies analyzers after the merge and attaches
 * the result with {@link SearchResponse#withAnalysis}. Analyzers may be stateless or hold models; they follow the
 * same ownership rules as providers (ADR-017) and must allow concurrent calls.
 */
public interface SearchAnalyzer {

    /** Stable id of this analyzer, format {@code [a-z0-9][a-z0-9._-]*}; also used in the analysis provenance. */
    String getAnalyzerId();

    /**
     * Analyses a response. Fails only through the future; the analysis names this analyzer in its provenance.
     *
     * @param response the response to analyse, never modified
     * @param context  deadline and cancellation
     * @return the future analysis
     */
    CompletableFuture<SearchAnalysis> analyze(SearchResponse response, SearchContext context);
}
