package com.aresstack.serp4j.browser.playwright;

/**
 * Factory for a {@link PlaywrightBrowserRuntime} that connects to a remote Playwright server with
 * {@code BrowserType.connect(wsEndpoint)} instead of launching a browser (WP3 B-11). The server can run in its own
 * process or container, for example {@code npx playwright run-server --port 3000}; its Playwright version must match
 * the Playwright Java version of this module. Serp4J itself starts no sidecar.
 *
 * <pre>
 * BrowserRuntime runtime = RemotePlaywrightRuntime.builder("ws://browser-host:3000/").maxConcurrentSessions(4).build();
 * </pre>
 *
 * <p>A driver module is still required: the Playwright Java client always runs its local driver.</p>
 */
public final class RemotePlaywrightRuntime {

    private RemotePlaywrightRuntime() {
    }

    /**
     * @param wsEndpoint WebSocket endpoint of the Playwright server
     * @return a builder; executable path, channel and launch arguments are not allowed
     */
    public static PlaywrightBrowserRuntime.Builder builder(String wsEndpoint) {
        return PlaywrightBrowserRuntime.builder().wsEndpoint(wsEndpoint);
    }
}
