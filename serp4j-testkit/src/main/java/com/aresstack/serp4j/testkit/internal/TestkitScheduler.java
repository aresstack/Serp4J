package com.aresstack.serp4j.testkit.internal;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One process-wide scheduler of daemon threads for simulated latency, delays and deadlines. It belongs to the
 * testkit, not to any fake, so closing a fake never shuts it down (ADR-017) and tests never leak threads that keep
 * the JVM alive.
 */
public final class TestkitScheduler {

    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "serp4j-testkit-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    private TestkitScheduler() {
    }

    /** Runs {@code task} after {@code delay}; a zero or negative delay still runs asynchronously. */
    public static ScheduledFuture<?> schedule(Runnable task, Duration delay) {
        long nanos = Math.max(0L, delay.toNanos());
        return SCHEDULER.schedule(task, nanos, TimeUnit.NANOSECONDS);
    }
}
