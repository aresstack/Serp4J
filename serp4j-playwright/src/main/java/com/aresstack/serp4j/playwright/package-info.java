/**
 * Serp4J Playwright extension (ADR-020): the integration for browser-based search providers that a host implements
 * locally. The host initialises Playwright, starts and owns the {@link com.microsoft.playwright.Browser} and passes
 * the current instance with every search call; Serp4J uses it and never starts, restarts or closes it. This module
 * contains no search engine: a Google provider, for example, lives with the host.
 *
 * <p>{@link com.aresstack.serp4j.playwright.PlaywrightSearchProvider} is the interface such a provider implements;
 * {@link com.aresstack.serp4j.playwright.PlaywrightSearchSupport} and
 * {@link com.aresstack.serp4j.playwright.PlaywrightSearchFailures} hold the shared pieces (timeouts, error
 * translation, metadata, obstacle failures) so that providers only write what is engine-specific.</p>
 */
package com.aresstack.serp4j.playwright;
