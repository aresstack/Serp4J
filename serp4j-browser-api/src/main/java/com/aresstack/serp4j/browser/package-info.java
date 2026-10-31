/**
 * Serp4J browser port (ADR-010, WP3 §5). Java 8, no dependencies, does not know {@code serp4j-core}.
 *
 * <p>The port speaks only in browser terms (URL, page, element, script, network). Engine adapters such as the
 * Google browser adapter depend on it; implementations such as {@code serp4j-browser-playwright} or the
 * {@code FakeBrowserRuntime} of {@code serp4j-browser-testkit} provide it.</p>
 *
 * <p><strong>Threading (WP3 B-6).</strong> The port is synchronous and blocking with timeouts. A
 * {@link com.aresstack.serp4j.browser.BrowserSession} may be called from any thread; implementations serialize the
 * calls onto the thread that owns the session. Asynchrony belongs to the layer above.</p>
 *
 * <p><strong>Ownership (ADR-017).</strong> A {@link com.aresstack.serp4j.browser.BrowserRuntime} belongs to whoever
 * created it. Engine adapters that receive a runtime close only the sessions they opened, never the runtime.</p>
 */
package com.aresstack.serp4j.browser;
