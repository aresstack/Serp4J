package com.aresstack.serp4j.core.testsupport;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stand-in for injected infrastructure such as an HTTP transport or a browser runtime: counts uses, knows whether
 * it was closed and refuses use afterwards. Lets the ownership contract (ADR-017) observe who closes what.
 */
public final class FakeSharedResource implements AutoCloseable {

    private final String name;
    private final AtomicInteger uses = new AtomicInteger();
    private final AtomicInteger closeCalls = new AtomicInteger();

    public FakeSharedResource(String name) {
        this.name = name;
    }

    /** Simulates one call through the resource. */
    public void use() {
        if (isClosed()) {
            throw new IllegalStateException(name + " is closed");
        }
        uses.incrementAndGet();
    }

    public int getUses() {
        return uses.get();
    }

    public boolean isClosed() {
        return closeCalls.get() > 0;
    }

    public int getCloseCalls() {
        return closeCalls.get();
    }

    @Override
    public void close() {
        closeCalls.incrementAndGet();
    }

    @Override
    public String toString() {
        return "FakeSharedResource{" + name + ", uses=" + uses + ", closed=" + isClosed() + '}';
    }
}
