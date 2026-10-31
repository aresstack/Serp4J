package com.aresstack.serp4j.browser.testkit;

import com.aresstack.serp4j.browser.BrowserCapability;
import com.aresstack.serp4j.browser.BrowserCapacityException;
import com.aresstack.serp4j.browser.BrowserReadiness;
import com.aresstack.serp4j.browser.BrowserRuntime;
import com.aresstack.serp4j.browser.BrowserRuntimeInfo;
import com.aresstack.serp4j.browser.BrowserSession;
import com.aresstack.serp4j.browser.BrowserSessionOptions;
import com.aresstack.serp4j.browser.BrowserUnavailableException;
import com.aresstack.serp4j.browser.EngineFamily;
import com.aresstack.serp4j.browser.IsolationLevel;
import com.aresstack.serp4j.browser.UnsupportedBrowserCapabilityException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link BrowserRuntime} without a browser: sessions serve the pages of a {@link FakeWeb}. It enforces the same
 * contract as a real runtime (capacity limit, URL policy, budgets, states, isolated cookies, network capture of
 * delayed background exchanges) and passes {@link BrowserRuntimeContractTest}. Thread-safe.
 *
 * <pre>
 * FakeBrowserRuntime runtime = FakeBrowserRuntime.builder()
 *         .web(FakeWeb.builder().page("https://www.example.test/", FakePage.builder().title("Home").build()).build())
 *         .build();
 * </pre>
 */
public final class FakeBrowserRuntime implements BrowserRuntime {

    private final FakeWeb web;
    private final BrowserRuntimeInfo info;
    private final BrowserReadiness readiness;
    private final Semaphore slots;
    private final List<FakeBrowserSession> openSessions = new CopyOnWriteArrayList<FakeBrowserSession>();
    private final List<FakeBrowserSession> allSessions = new CopyOnWriteArrayList<FakeBrowserSession>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger probes = new AtomicInteger();

    private FakeBrowserRuntime(Builder builder) {
        this.web = builder.web;
        BrowserRuntimeInfo.Builder infoBuilder = BrowserRuntimeInfo.builder("fake", EngineFamily.NONE)
                .browserVersion("fake")
                .isolationLevel(IsolationLevel.CONTEXT)
                .maxConcurrentSessions(builder.maxConcurrentSessions)
                .headless(true)
                .capabilities(builder.capabilities);
        if (builder.maxConcurrentSessions > 1) {
            infoBuilder.capability(BrowserCapability.PARALLEL_SESSIONS);
        }
        this.info = infoBuilder.build();
        this.readiness = builder.readiness;
        this.slots = new Semaphore(builder.maxConcurrentSessions, true);
    }

    /**
     * @return a builder with an empty web, 4 sessions and the capabilities {@code JAVASCRIPT},
     * {@code NETWORK_OBSERVATION}, {@code NETWORK_INTERCEPTION} and {@code LOCALE_EMULATION}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BrowserRuntimeInfo getInfo() {
        return info;
    }

    @Override
    public BrowserReadiness probe() {
        probes.incrementAndGet();
        if (closed.get()) {
            return BrowserReadiness.of(BrowserReadiness.Status.CLOSED, "fake runtime closed");
        }
        return readiness;
    }

    @Override
    public BrowserSession openSession(BrowserSessionOptions options) {
        if (options == null) {
            throw new NullPointerException("options must not be null");
        }
        ensureOpen();
        if (!readiness.isReady()) {
            throw new BrowserUnavailableException(readiness, null);
        }
        Set<BrowserCapability> missing = info.missing(options.getRequiredCapabilities());
        if (!missing.isEmpty()) {
            throw new UnsupportedBrowserCapabilityException(missing, "fake runtime does not support " + missing);
        }
        boolean acquired;
        try {
            acquired = slots.tryAcquire(options.getAcquireTimeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrowserCapacityException(info.getMaxConcurrentSessions(), "interrupted while waiting for a slot");
        }
        if (!acquired) {
            throw new BrowserCapacityException(info.getMaxConcurrentSessions(), "all "
                    + info.getMaxConcurrentSessions() + " session slots are in use (waited "
                    + options.getAcquireTimeout() + ")");
        }
        if (closed.get()) {
            slots.release();
            ensureOpen();
        }
        FakeBrowserSession session = new FakeBrowserSession(this, web, options);
        openSessions.add(session);
        allSessions.add(session);
        return session;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new BrowserUnavailableException(
                    BrowserReadiness.of(BrowserReadiness.Status.CLOSED, "fake runtime closed"), null);
        }
    }

    /** Called exactly once per session when it reaches a terminal state. */
    void released(FakeBrowserSession session) {
        if (openSessions.remove(session)) {
            slots.release();
        }
    }

    boolean supports(BrowserCapability capability) {
        return info.supports(capability);
    }

    /**
     * Simulates a browser crash: every open session becomes {@code FAILED}. New sessions can be opened afterwards,
     * like a runtime that restarts its browser.
     */
    public void simulateCrash() {
        for (FakeBrowserSession session : new ArrayList<FakeBrowserSession>(openSessions)) {
            session.crash();
        }
    }

    /**
     * @return number of sessions that are currently open
     */
    public int getOpenSessionCount() {
        return openSessions.size();
    }

    /**
     * @return every session opened so far, in opening order
     */
    public List<FakeBrowserSession> getSessions() {
        return Collections.unmodifiableList(new ArrayList<FakeBrowserSession>(allSessions));
    }

    /**
     * @return number of {@link #probe()} calls
     */
    public int getProbeCount() {
        return probes.get();
    }

    /**
     * @return whether {@link #close()} was called
     */
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            for (FakeBrowserSession session : new ArrayList<FakeBrowserSession>(openSessions)) {
                session.close();
            }
        }
    }

    @Override
    public String toString() {
        return "FakeBrowserRuntime{" + info + ", open=" + openSessions.size() + ", closed=" + closed.get() + '}';
    }

    /** Builder for {@link FakeBrowserRuntime}. */
    public static final class Builder {
        private FakeWeb web = FakeWeb.empty();
        private int maxConcurrentSessions = 4;
        private final Set<BrowserCapability> capabilities = EnumSet.of(BrowserCapability.JAVASCRIPT,
                BrowserCapability.NETWORK_OBSERVATION, BrowserCapability.NETWORK_INTERCEPTION,
                BrowserCapability.LOCALE_EMULATION);
        private BrowserReadiness readiness = BrowserReadiness.ready("fake");

        private Builder() {
        }

        /**
         * @param web pages served by the sessions
         * @return this builder
         */
        public Builder web(FakeWeb web) {
            if (web == null) {
                throw new NullPointerException("web must not be null");
            }
            this.web = web;
            return this;
        }

        /**
         * @param maxConcurrentSessions positive session limit, default 4
         * @return this builder
         */
        public Builder maxConcurrentSessions(int maxConcurrentSessions) {
            if (maxConcurrentSessions <= 0) {
                throw new IllegalArgumentException("maxConcurrentSessions must be positive");
            }
            this.maxConcurrentSessions = maxConcurrentSessions;
            return this;
        }

        /**
         * Replaces the declared capabilities; {@code PARALLEL_SESSIONS} is added automatically for more than one
         * session.
         *
         * @param capabilities capabilities
         * @return this builder
         */
        public Builder capabilities(Set<BrowserCapability> capabilities) {
            this.capabilities.clear();
            this.capabilities.addAll(capabilities);
            return this;
        }

        /**
         * @param readiness what {@link #probe()} reports; a non-ready value also makes {@code openSession} fail
         * @return this builder
         */
        public Builder readiness(BrowserReadiness readiness) {
            if (readiness == null) {
                throw new NullPointerException("readiness must not be null");
            }
            this.readiness = readiness;
            return this;
        }

        /**
         * @return the runtime
         */
        public FakeBrowserRuntime build() {
            return new FakeBrowserRuntime(this);
        }
    }
}
