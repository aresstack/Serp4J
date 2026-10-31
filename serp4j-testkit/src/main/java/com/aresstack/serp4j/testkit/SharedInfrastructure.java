package com.aresstack.serp4j.testkit;

/**
 * Observable stand-in for injected infrastructure – an HTTP transport, a browser runtime, a scheduler. The
 * ownership contract (ADR-017) uses it to see who uses and who closes what: a provider borrows it, uses it, and
 * must never close it.
 */
public interface SharedInfrastructure extends AutoCloseable {

    /** Number of calls made through this infrastructure so far. */
    int getUses();

    /** Whether {@link #close()} has been called at least once. */
    boolean isClosed();

    /** How often {@link #close()} has been called; must stay {@code 0} while only providers touched it. */
    int getCloseCalls();

    /** Closes the infrastructure; only its owner – the test – calls this. Idempotent. */
    @Override
    void close();
}
