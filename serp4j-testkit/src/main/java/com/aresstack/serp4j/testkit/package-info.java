/**
 * Serp4J testkit (Paket E, architecture.md §10): everything needed to test providers, orchestration and hosts
 * without paid API calls and without network access.
 *
 * <ul>
 * <li>{@link com.aresstack.serp4j.testkit.provider} – {@code MockSearchProvider}, {@code ScriptedSearchProvider},
 * {@code ScriptedConversationalProvider} with failure injection per category and latency.</li>
 * <li>{@link com.aresstack.serp4j.testkit.http} – {@code FakeHttpTransport}: canned responses and fixtures,
 * request recording, status codes, delays, timeouts and response sequences such as pending &#8594; ready.</li>
 * <li>{@link com.aresstack.serp4j.testkit.fixture} – loading of fixtures under {@code fixtures/<provider>/} with
 * mandatory provenance ({@code recorded} or {@code synthetic}, decision E-1) and a secret scan.</li>
 * <li>{@link com.aresstack.serp4j.testkit.contract} – abstract JUnit&#160;5 contract tests every adapter extends.</li>
 * <li>{@link com.aresstack.serp4j.testkit.assertion} – assertions without third-party dependencies.</li>
 * </ul>
 *
 * <p>JUnit Jupiter is a {@code compileOnly} dependency: modules that use the contract tests bring JUnit&#160;5
 * themselves. Everything else in the testkit works without JUnit.</p>
 */
package com.aresstack.serp4j.testkit;
