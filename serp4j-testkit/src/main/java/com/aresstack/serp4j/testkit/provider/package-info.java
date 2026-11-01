/**
 * Mock providers for orchestration, registry and host tests (architecture.md §10): {@link
 * com.aresstack.serp4j.testkit.provider.MockSearchProvider} answers every call the same configurable way, {@link
 * com.aresstack.serp4j.testkit.provider.ScriptedSearchProvider} plays a fixed sequence of {@link
 * com.aresstack.serp4j.testkit.provider.Outcome}s, {@link
 * com.aresstack.serp4j.testkit.provider.ScriptedConversationalProvider} simulates a conversational search surface
 * with the full session contract of ADR-009.
 *
 * <p>All mocks follow the port contracts themselves (they pass the contract tests of
 * {@link com.aresstack.serp4j.testkit.contract}): they check capabilities, name themselves in the metadata, honour
 * deadline and cancellation, borrow injected {@link com.aresstack.serp4j.testkit.SharedInfrastructure} without ever
 * closing it, and fail through the future with exactly one {@code SearchProviderException}. Results they generate
 * are synthetic and point to {@code example.invalid}.</p>
 */
package com.aresstack.serp4j.testkit.provider;
