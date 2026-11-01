package com.aresstack.serp4j.provider.argos;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * One daemon timer thread for the deadlines of all Argos searches in the JVM. It only completes futures; it never
 * holds resources of a host, so it needs no owner and no shutdown (ADR-017).
 */
final class Deadlines {

    static final ScheduledThreadPoolExecutor SCHEDULER = create();

    private Deadlines() {
    }

    private static ScheduledThreadPoolExecutor create() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "serp4j-argos-deadlines");
                thread.setDaemon(true);
                return thread;
            }
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
}
