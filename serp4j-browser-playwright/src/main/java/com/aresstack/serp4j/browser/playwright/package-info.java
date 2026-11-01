/**
 * {@link com.aresstack.serp4j.browser.BrowserRuntime} over the Playwright Java API (ADR-011, WP3 §6).
 *
 * <p>The module is Java 8 bytecode and contains no JavaScript driver. Exactly one driver module must be on the
 * runtime class path: {@code serp4j-browser-playwright-driver-node} (official Node driver, Java 8) or
 * {@code serp4j-browser-playwright-driver-graal} ({@code playwright4j}, GraalJS, Java 21). The runtime checks this
 * before it starts and reports a typed {@link com.aresstack.serp4j.browser.BrowserReadiness} otherwise.</p>
 *
 * <p><strong>Threading.</strong> Playwright Java is not thread-safe and delivers events only while its owner thread
 * is inside a Playwright call. Each worker of the runtime therefore owns one thread that creates
 * {@code Playwright → Browser → BrowserContext → Page} and executes every command; callers enqueue commands and
 * block on the result. While no command is queued the owner thread pumps Playwright events in short
 * {@code waitForTimeout} slices, so network events, popups and disconnects are delivered in idle state (the actor
 * model of AskAI's browser sidecar, copied with new packages and without a dependency on AskAI).</p>
 *
 * <p>No public signature of this package exposes Playwright types.</p>
 */
package com.aresstack.serp4j.browser.playwright;
