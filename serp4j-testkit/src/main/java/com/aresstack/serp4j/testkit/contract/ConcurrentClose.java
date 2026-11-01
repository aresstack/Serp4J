package com.aresstack.serp4j.testkit.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Starts several threads at once that call the given close actions; rethrows the first failure (ADR-017 rule 8). */
final class ConcurrentClose {

    private ConcurrentClose() {
    }

    static void run(int callersPerAction, Runnable... closeActions) throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(callersPerAction * closeActions.length);
        try {
            List<Future<?>> closes = new ArrayList<Future<?>>();
            for (final Runnable action : closeActions) {
                for (int i = 0; i < callersPerAction; i++) {
                    closes.add(executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                start.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(e);
                            }
                            action.run();
                        }
                    }));
                }
            }
            start.countDown();
            for (Future<?> close : closes) {
                close.get(10, TimeUnit.SECONDS); // propagates any exception thrown by a concurrent close()
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
