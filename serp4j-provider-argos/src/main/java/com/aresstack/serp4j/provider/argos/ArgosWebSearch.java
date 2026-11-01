package com.aresstack.serp4j.provider.argos;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Driven port of the Argos provider: runs the AskAI/Argos browser web search for one query.
 *
 * <p>Implementations complete the future exceptionally with {@link ArgosWebSearchException} (or the transport's own
 * exception) on failure; they never return {@code null}. An implementation that owns resources releases them in
 * {@link #close()}; injected infrastructure stays with the caller (ADR-017).</p>
 */
public interface ArgosWebSearch extends AutoCloseable {

    /**
     * @param query   the query text, never blank
     * @param timeout upper bound for the whole call, or {@code null} for the implementation's default
     */
    CompletableFuture<ArgosSearchOutput> search(String query, Duration timeout);

    @Override
    void close();
}
