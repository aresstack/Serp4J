package com.aresstack.serp4j.browser;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * A running recording of completed exchanges (WP3 §5.3).
 *
 * <p>Contract: exchanges are delivered even while no command runs on the session (event delivery in idle state),
 * bodies are available only after the response is complete, captured bodies stay in memory of the session and are
 * discarded with {@link #close()} or with the session, and {@code Cookie}, {@code Set-Cookie},
 * {@code Authorization} and {@code Proxy-Authorization} headers are never exposed.</p>
 */
public interface NetworkCapture extends AutoCloseable {

    /**
     * @return exchanges completed so far, in completion order (immutable snapshot)
     */
    List<ObservedExchange> getExchanges();

    /**
     * Waits for the first completed exchange that matches the filter, including exchanges already recorded.
     *
     * @param filter  additional filter; use {@link NetworkFilter#any()} for the capture's own filter
     * @param timeout maximum wait
     * @return the exchange, or empty after the timeout
     */
    Optional<ObservedExchange> awaitFirst(NetworkFilter filter, Duration timeout);

    /**
     * Waits until no matching request has been in flight for {@code quietPeriod} ("network quiet").
     *
     * @param quietPeriod positive quiet period
     * @param timeout     maximum wait
     * @return the outcome
     */
    WaitOutcome awaitQuiet(Duration quietPeriod, Duration timeout);

    /**
     * Stops the recording and discards recorded bodies. Idempotent.
     */
    @Override
    void close();
}
