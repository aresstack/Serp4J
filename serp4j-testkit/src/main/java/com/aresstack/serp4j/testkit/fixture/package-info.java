/**
 * Fixtures: recorded provider answers (and, clearly marked, synthetic test data) on the classpath under
 * {@code fixtures/<provider>/<name>}, each with a mandatory sidecar {@code <name>.fixture.properties}.
 *
 * <h2>Sidecar format</h2>
 * <p>Plain {@link java.util.Properties} syntax: comments only on their own {@code #} lines, never after a value.</p>
 * <pre>
 * # fixtures/dataforseo/organic-advanced.json.fixture.properties
 * # required: recorded | synthetic
 * provenance=recorded
 * # required for recorded
 * source=DataForSEO playground, POST /v3/serp/google/organic/live/advanced
 * # optional
 * recordedAt=2026-10-12
 * # required and true for recorded
 * anonymized=true
 * # required for synthetic: why it is not recorded
 * description=Organic results with people_also_ask
 * # optional, otherwise derived from the file extension
 * contentType=application/json
 * # optional HTTP status for FakeHttpStep.fixture(..), default 200
 * status=200
 * </pre>
 *
 * <p>Decision E-1: response mappers are tested against <em>recorded</em> answers only;
 * {@link com.aresstack.serp4j.testkit.fixture.Fixtures#loadRecorded} refuses synthetic data. Synthetic fixtures
 * exist for the testkit's own tests and for transport behaviour, never as a stand-in for a provider answer.
 * {@link com.aresstack.serp4j.testkit.fixture.FixtureSecretScanner} keeps credentials out of checked-in fixtures.</p>
 */
package com.aresstack.serp4j.testkit.fixture;
