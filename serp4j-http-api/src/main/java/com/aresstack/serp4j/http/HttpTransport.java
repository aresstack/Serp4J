package com.aresstack.serp4j.http;

import java.util.concurrent.CompletableFuture;

/**
 * The one HTTP abstraction providers use (architecture.md §9.1). Implementations live in their own modules
 * ({@code serp4j-http-ahc}, {@code serp4j-http-jdk}); a shared transport for all providers is the normal case.
 *
 * <p><strong>Ownership (ADR-017).</strong> A transport passed to a provider is <em>borrowed</em>: the host that
 * created it closes it, after all providers that use it are closed. A provider never calls {@link #close()} on an
 * injected transport, and closing one provider leaves the transport usable for every other provider.</p>
 *
 * <p>Implementations must be thread-safe, must set a default {@code User-Agent} of the form
 * {@code Serp4J/<version>} when the request names none, and must deliver <em>every</em> server response –
 * including {@code 4xx} and {@code 5xx} – as {@link HttpResponse}; only failures to obtain a response complete the
 * future exceptionally with {@link HttpTransportException}.</p>
 */
public interface HttpTransport extends AutoCloseable {

    /**
     * Executes a request.
     *
     * @return a future that completes with the server's response or fails with {@link HttpTransportException};
     * synchronously only {@link NullPointerException} or {@link IllegalStateException} (transport closed) are thrown
     */
    CompletableFuture<HttpResponse> execute(HttpRequest request);

    /**
     * Releases connections and threads; running requests fail with {@link HttpTransportException.Kind#CANCELLED}.
     * Idempotent. Called only by the owner of the transport, never by a provider it was injected into.
     *
     * <p>Thread-safe: may be called from any thread, concurrently with running calls and with a second
     * {@code close()}; the first call wins, concurrent calls return after the resources are released or at once
     * (ADR-017 rule 8).</p>
     */
    @Override
    void close();
}
