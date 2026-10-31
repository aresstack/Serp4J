/**
 * Test support for the browser port (implementation-plan.md Paket F, WP3 §5.4): {@link
 * com.aresstack.serp4j.browser.testkit.FakeBrowserRuntime} serves scripted pages without a browser, and {@link
 * com.aresstack.serp4j.browser.testkit.BrowserRuntimeContractTest} is the TCK every {@code BrowserRuntime}
 * implementation must pass. Java 8; JUnit Jupiter is {@code compileOnly}, consumers bring their own.
 *
 * <p>Engine adapters test their extraction offline against the fake: the page script returns a raw tree, the
 * mapping to search data happens in Java and is tested with fixtures.</p>
 */
package com.aresstack.serp4j.browser.testkit;
