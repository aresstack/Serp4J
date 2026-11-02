/**
 * Cloud adapter for the Brave Search API (architecture.md §5.5, Paket B).
 *
 * <p>{@link com.aresstack.serp4j.provider.brave.BraveSearchProvider} implements the
 * {@link com.aresstack.serp4j.core.port.SearchProvider} port for {@code GET /res/v1/web/search} on the target
 * {@code brave/web}. Transport ({@link com.aresstack.serp4j.http.HttpTransport}) and credentials
 * ({@link com.aresstack.serp4j.core.port.credential.CredentialProvider}) are injected and stay owned by the caller
 * (ADR-017). The subscription token travels only in the {@code X-Subscription-Token} header, never in the URL.
 *
 * <p>Rich Search, Answers and LLM Context are separate Brave capabilities and are not part of this provider.
 */
package com.aresstack.serp4j.provider.brave;
