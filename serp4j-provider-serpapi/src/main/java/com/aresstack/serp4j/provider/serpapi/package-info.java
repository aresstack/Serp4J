/**
 * Cloud adapter for SerpApi ({@code GET https://serpapi.com/search.json}).
 *
 * <p>{@link com.aresstack.serp4j.provider.serpapi.SerpApiSearchProvider} implements the
 * {@link com.aresstack.serp4j.core.port.SearchProvider} port for the targets {@code google/web} and {@code bing/web}.
 * Transport ({@link com.aresstack.serp4j.http.HttpTransport}) and credentials
 * ({@link com.aresstack.serp4j.core.port.credential.CredentialProvider}) are injected and stay owned by the caller
 * (ADR-017).
 *
 * <p>SerpApi accepts the key only as the URL parameter {@code api_key}. The adapter adds it through
 * {@code RequestAuthentication.queryParameter}, which registers the parameter as sensitive: it is masked in
 * {@code HttpRequest.getRedactedUrl()}/{@code toString()}, in {@code ExecutionInfo.sourceUrl}, in error messages, and
 * the transport strips it on cross-origin redirects.
 *
 * <p>No SerpApi client library is used: {@code serpapi-java} needs Java 21, {@code serpapi4j} Java 16 plus Jersey,
 * Guava and Log4j. Both are MIT licensed and served only as reference.
 */
package com.aresstack.serp4j.provider.serpapi;
