package com.aresstack.serp4j.browser;

/**
 * Heavy, long-lived browser infrastructure: one driver and one or more browser processes (WP3 §5.1, §6.1).
 * Typically one instance per application, shared by all engine adapters that need a browser.
 *
 * <p><strong>Lifecycle.</strong> Implementations start lazily: neither construction nor {@link #getInfo()} starts a
 * driver or browser; the first {@link #openSession(BrowserSessionOptions)} or an explicit {@link #probe()} does.
 * {@link #close()} closes every open session and ends browser and driver processes. A runtime never downloads
 * browsers implicitly.</p>
 *
 * <p><strong>Ownership (ADR-017).</strong> The runtime belongs to its creator. A provider or engine adapter that
 * receives a runtime closes only the sessions it opened and never calls {@link #close()} on the runtime. The
 * recommended teardown order of the owner is: providers, then browser runtimes, then transports, then executors.
 * Using a runtime after it was closed fails with {@link BrowserUnavailableException}, never with a hang.</p>
 *
 * <p>Implementations are thread-safe.</p>
 */
public interface BrowserRuntime extends AutoCloseable {

    /**
     * Static description of the runtime: backend, engine family, isolation, session limit and capabilities. Never
     * starts a browser. The browser version is known only after a browser has been started.
     *
     * @return the runtime description
     */
    BrowserRuntimeInfo getInfo();

    /**
     * Explicit readiness check without silent fallbacks. May start the driver and browser to verify that they work;
     * never downloads anything.
     *
     * @return the readiness; {@link BrowserReadiness#isReady()} is {@code true} only if sessions can be opened
     */
    BrowserReadiness probe();

    /**
     * Opens an isolated session (own cookies, storage and history) with exactly one active page.
     *
     * <p>If {@link BrowserRuntimeInfo#getMaxConcurrentSessions()} sessions are already open, the call waits at most
     * {@link BrowserSessionOptions#getAcquireTimeout()} for a free slot.</p>
     *
     * @param options session options
     * @return the open session; the caller owns it and must close it
     * @throws BrowserCapacityException if no slot became free within the acquire timeout
     * @throws UnsupportedBrowserCapabilityException if a required capability is not supported
     * @throws BrowserUnavailableException if the runtime is closed or the browser cannot be started
     */
    BrowserSession openSession(BrowserSessionOptions options);

    /**
     * Closes all open sessions and ends browser and driver. Idempotent and thread-safe.
     */
    @Override
    void close();
}
