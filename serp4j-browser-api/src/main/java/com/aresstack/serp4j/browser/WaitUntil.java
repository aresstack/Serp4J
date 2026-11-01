package com.aresstack.serp4j.browser;

/** When {@link BrowserSession#navigate(NavigationRequest)} considers a navigation finished. */
public enum WaitUntil {
    /** The response was received and the document started loading. */
    COMMIT,
    /** The {@code DOMContentLoaded} event fired. */
    DOM_CONTENT_LOADED,
    /** The {@code load} event fired (default). */
    LOAD,
    /** No network connections for at least 500 ms; discouraged for pages that poll. */
    NETWORK_IDLE
}
