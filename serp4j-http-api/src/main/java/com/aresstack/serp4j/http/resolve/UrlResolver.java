package com.aresstack.serp4j.http.resolve;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves a redirect or tracking URL to the address it points to, without fetching that address.
 *
 * <p>{@link #supports(String)} is a pure check without I/O; only supported URLs are handed to
 * {@link #resolve(String, Duration)}. The future completes with the absolute target URL or fails with
 * {@link UrlResolutionException}; cancelling it aborts the underlying request. Implementations are thread-safe and
 * never close infrastructure they borrowed (ADR-017).</p>
 */
public interface UrlResolver {

    /** Whether this resolver knows how to resolve {@code url}; never performs I/O. */
    boolean supports(String url);

    /**
     * Resolves {@code url}, which {@link #supports(String) is supported}.
     *
     * @param timeout upper bound for the resolution, positive
     */
    CompletableFuture<String> resolve(String url, Duration timeout);
}
