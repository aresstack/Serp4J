package com.aresstack.serp4j.core.model;

/**
 * Request aspects a provider may or may not support (WP2 §6.1). Used in {@code SearchCapabilities} to declare
 * support and in {@link SearchRequest} to mark aspects as {@link RequirementLevel#REQUIRED}.
 *
 * <p>Placed in the model package (not in {@code core.port.capability} as sketched in WP2) because
 * {@link SearchRequest} refers to it and the model must not depend on the ports.</p>
 */
public enum SearchFeature {
    /** Free-text query; always supported and always required. */
    QUERY_TEXT,
    /** Offset-based pagination ({@link Paging#getOffset()}). */
    PAGINATION_OFFSET,
    /** Page-based pagination; providers translate offsets to pages where needed. */
    PAGINATION_PAGE,
    /** The caller may choose the number of results ({@link Paging#getLimit()}). */
    MAX_RESULTS,
    /** Country restriction ({@link SearchLocale#getCountryCode()}). */
    COUNTRY,
    /** Language restriction ({@link SearchLocale#getLanguageCode()}, {@link SearchLocale#getUiLanguage()}). */
    LANGUAGE,
    /** Geographic location ({@link SearchLocale#getLocation()}). */
    GEO_LOCATION,
    /** Time range / freshness ({@link SearchRequest#getTimeRange()}). */
    FRESHNESS,
    /** Safe-search level. */
    SAFE_SEARCH,
    /** Device emulation. */
    DEVICE,
    /** The provider works with an asynchronous task and polls for the result. */
    ASYNC_TASK,
    /** The raw response can be returned ({@code RawPayloadPolicy}). */
    RAW_RESPONSE
}
