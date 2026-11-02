package com.aresstack.serp4j.provider.serpapi;

import com.aresstack.serp4j.core.model.ext.ExtensionKey;

import java.math.BigDecimal;

/**
 * Extension keys the SerpApi adapter sets (namespace {@value #NAMESPACE}). Everything else SerpApi returns stays
 * available as {@code Extensions.getUnmapped("serpapi")} (ADR-019, principle 5).
 */
public final class SerpApiExtensions {

    public static final String NAMESPACE = "serpapi";

    // Response metadata
    /** {@code search_metadata.status}: {@code Success}, {@code Processing} or {@code Error}. */
    public static final ExtensionKey<String> STATUS = ExtensionKey.of(NAMESPACE, "status", String.class);
    /** {@code search_metadata.created_at}, SerpApi's own format, e.g. {@code 2026-11-02 04:00:00 UTC}. */
    public static final ExtensionKey<String> CREATED_AT = ExtensionKey.of(NAMESPACE, "createdAt", String.class);
    /** {@code search_metadata.total_time_taken} in seconds. */
    public static final ExtensionKey<BigDecimal> TOTAL_TIME_TAKEN = ExtensionKey.of(NAMESPACE, "totalTimeTaken",
            BigDecimal.class);
    /** URL of the engine page SerpApi fetched ({@code google_url} / {@code bing_url}); never carries the key. */
    public static final ExtensionKey<String> ENGINE_URL = ExtensionKey.of(NAMESPACE, "engineUrl", String.class);
    /** {@code search_information.organic_results_state}, e.g. {@code Results for exact spelling}. */
    public static final ExtensionKey<String> ORGANIC_RESULTS_STATE = ExtensionKey.of(NAMESPACE,
            "organicResultsState", String.class);
    /** {@code search_parameters.location_used}. */
    public static final ExtensionKey<String> LOCATION_USED = ExtensionKey.of(NAMESPACE, "locationUsed",
            String.class);

    // Result items
    /** Date as shown on the SERP, e.g. {@code Oct 1, 2026} or {@code 3 days ago}. */
    public static final ExtensionKey<String> DATE = ExtensionKey.of(NAMESPACE, "date", String.class);
    /** Ad block, e.g. {@code top} or {@code bottom}. */
    public static final ExtensionKey<String> BLOCK_POSITION = ExtensionKey.of(NAMESPACE, "blockPosition",
            String.class);
    public static final ExtensionKey<String> FAVICON = ExtensionKey.of(NAMESPACE, "favicon", String.class);

    private SerpApiExtensions() {
    }
}
