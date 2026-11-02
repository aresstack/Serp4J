/**
 * Provider adapter for the DataForSEO SERP API v3 (cloud adapter, ports and adapters).
 *
 * <p>{@link com.aresstack.serp4j.provider.dataforseo.DataForSeoSearchProvider} implements the core port
 * {@link com.aresstack.serp4j.core.port.SearchProvider} for Google and Bing organic search over the live endpoints
 * {@code /v3/serp/{engine}/organic/live/regular} and {@code .../live/advanced}. HTTP goes through an injected
 * {@link com.aresstack.serp4j.http.HttpTransport} that belongs to the caller (ADR-017); credentials come from a
 * {@link com.aresstack.serp4j.core.port.credential.CredentialProvider} and travel only in the Basic
 * {@code Authorization} header, never in URLs or log messages.</p>
 *
 * <p>The response is mapped by a declarative mapping definition ({@code dataforseo-serp-organic.json}, ADR-019) plus
 * a few custom mappers. The standard task flow ({@code task_post}/{@code tasks_ready}/{@code task_get}), the HTML
 * function and the locations/languages lookups are not part of this first version.</p>
 */
package com.aresstack.serp4j.provider.dataforseo;
