package com.aresstack.serp4j.browser;

/**
 * Optional capabilities of a {@link BrowserRuntime} (WP3 §5.2). A provider that needs one declares it in
 * {@link BrowserSessionOptions.Builder#require(BrowserCapability)} or checks {@link BrowserRuntimeInfo} when it is
 * built (fail fast, architecture.md §6).
 */
public enum BrowserCapability {
    /** Page scripts can be evaluated. */
    JAVASCRIPT,
    /** Responses of the session can be observed including bodies ({@link BrowserSession#network()}). */
    NETWORK_OBSERVATION,
    /** Requests can be blocked or rerouted; used for the URL policy of page-initiated navigations. */
    NETWORK_INTERCEPTION,
    /** {@link BrowserSessionOptions#getLocale()} is applied to the page ({@code Accept-Language}, {@code navigator.language}). */
    LOCALE_EMULATION,
    /** The browser runs with a visible window; needed when a human has to solve an obstacle. */
    HEADED_MODE,
    /** Sessions can run on a persistent, exclusively leased profile. */
    PERSISTENT_PROFILE,
    /** More than one session can be open at the same time. */
    PARALLEL_SESSIONS
}
