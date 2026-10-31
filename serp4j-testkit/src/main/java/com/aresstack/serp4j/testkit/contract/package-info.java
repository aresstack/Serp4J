/**
 * Abstract JUnit&#160;5 contract tests (architecture.md §10, ADR-008, ADR-009, ADR-017). An adapter module extends
 * them in its own test sources, creates its provider on shared fake infrastructure and inherits every check:
 *
 * <pre>{@code
 * class BraveContractTest extends SearchProviderContractTest<FakeHttpTransport> {
 *     protected FakeHttpTransport createInfrastructure() {
 *         return FakeHttpTransport.create().onAnyRequest(
 *                 FakeHttpStep.fixture(Fixtures.loadRecorded("brave", "web.json")));
 *     }
 *     protected SearchProvider createProvider(ProviderId id, FakeHttpTransport http) {
 *         return BraveSearchProvider.builder(id).transport(http).credentials(credentials).build();
 *     }
 *     protected SearchRequest supportedRequest() {
 *         return SearchRequest.builder("serp4j").engine(SearchEngineId.BRAVE).build();
 *     }
 * }
 * }</pre>
 *
 * <p>Requires JUnit Jupiter on the test classpath of the using module.</p>
 */
package com.aresstack.serp4j.testkit.contract;
