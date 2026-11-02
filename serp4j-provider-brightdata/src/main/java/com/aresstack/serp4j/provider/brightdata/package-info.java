/**
 * Cloud adapter for the Bright Data SERP API (architecture.md §5.3, implementation-plan.md package C).
 *
 * <p>{@link com.aresstack.serp4j.provider.brightdata.BrightDataSearchProvider} sends one synchronous
 * {@code POST https://api.brightdata.com/request} per search with {@code format=json} and
 * {@code data_format=parsed}; the target is a complete Google search URL built from the provider-neutral
 * {@link com.aresstack.serp4j.core.model.SearchRequest}. The response is a wrapper ({@code status_code},
 * {@code headers}, {@code body}) whose {@code body} is a JSON <em>string</em> with the parsed SERP; it is unwrapped
 * and mapped with the declarative mapping definition {@code brightdata-google-web} (ADR-019).</p>
 *
 * <p>The API key travels only in the {@code Authorization} header, never in a URL, a log line or an exception. The
 * zone is host configuration. Provider-specific URL options ({@code uule}, {@code brd_mobile}, {@code brd_browser},
 * {@code brd_json}, proxy country) are set per request with
 * {@link com.aresstack.serp4j.provider.brightdata.BrightDataOptions}.</p>
 */
package com.aresstack.serp4j.provider.brightdata;
