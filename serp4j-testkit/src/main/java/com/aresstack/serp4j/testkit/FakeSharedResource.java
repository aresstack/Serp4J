package com.aresstack.serp4j.testkit;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal {@link SharedInfrastructure}: counts uses, records close calls and refuses use after it was closed.
 * Mock providers accept it as borrowed infrastructure; adapter contract tests usually take a
 * {@link com.aresstack.serp4j.testkit.http.FakeHttpTransport} instead.
 */
public final class FakeSharedResource implements SharedInfrastructure {

    private final String name;
    private final AtomicInteger uses = new AtomicInteger();
    private final AtomicInteger closeCalls = new AtomicInteger();

    public FakeSharedResource(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        this.name = name;
    }

    /**
     * Simulates one call through the resource.
     *
     * @throws IllegalStateException if the resource was closed
     */
    public void use() {
        if (isClosed()) {
            throw new IllegalStateException(name + " is closed");
        }
        uses.incrementAndGet();
    }

    public String getName() {
        return name;
    }

    @Override
    public int getUses() {
        return uses.get();
    }

    @Override
    public boolean isClosed() {
        return closeCalls.get() > 0;
    }

    @Override
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
