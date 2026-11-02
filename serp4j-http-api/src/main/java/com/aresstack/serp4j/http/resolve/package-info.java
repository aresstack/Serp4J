/**
 * Resolution of redirect URLs (for example Google {@code /goto} links) through the caller's
 * {@link com.aresstack.serp4j.http.HttpTransport}, reusable by every provider. Resolvers read the first redirect
 * response only and never fetch the target page.
 */
package com.aresstack.serp4j.http.resolve;
