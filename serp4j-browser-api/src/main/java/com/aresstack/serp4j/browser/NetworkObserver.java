package com.aresstack.serp4j.browser;

/**
 * Network observation of one session (optional capability {@link BrowserCapability#NETWORK_OBSERVATION}, WP3
 * §5.3). Observation is a second extraction source next to the DOM, never the only assumption (WP3 B-5).
 */
public interface NetworkObserver {

    /**
     * Starts recording matching, completed exchanges of the session. Several captures per session are allowed;
     * exchanges completed before the start are not included.
     *
     * @param filter what to record
     * @return the capture; close it when no longer needed
     */
    NetworkCapture start(NetworkFilter filter);
}
