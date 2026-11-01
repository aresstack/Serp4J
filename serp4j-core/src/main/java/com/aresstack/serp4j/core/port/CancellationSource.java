package com.aresstack.serp4j.core.port;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe owner side of a {@link CancellationSignal}: the party that may cancel keeps the source and hands the
 * {@link #getSignal() signal} to the providers. Callbacks run on the thread that calls {@link #cancel()}; a
 * callback that throws does not prevent the remaining callbacks from running.
 */
public final class CancellationSource {

    private final Object lock = new Object();
    private final List<Runnable> callbacks = new ArrayList<Runnable>();
    private boolean cancelled;
    private final CancellationSignal signal = new CancellationSignal() {
        @Override
        public boolean isCancelled() {
            return CancellationSource.this.isCancelled();
        }

        @Override
        public void onCancel(Runnable callback) {
            CancellationSource.this.onCancel(callback);
        }

        @Override
        public String toString() {
            return "CancellationSignal{cancelled=" + isCancelled() + '}';
        }
    };

    public CancellationSignal getSignal() {
        return signal;
    }

    public boolean isCancelled() {
        synchronized (lock) {
            return cancelled;
        }
    }

    /**
     * Cancels the signal and runs all registered callbacks once; later calls are no-ops. Every callback runs even
     * if an earlier one throws; the first {@link RuntimeException} or {@link Error} thrown by a callback is rethrown
     * to the caller after all callbacks have run, later ones are dropped. The signal is cancelled either way.
     *
     * @throws RuntimeException the first unchecked exception a callback threw
     * @throws Error the first error a callback threw
     */
    public void cancel() {
        List<Runnable> toRun;
        synchronized (lock) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            toRun = new ArrayList<Runnable>(callbacks);
            callbacks.clear();
        }
        Throwable first = null;
        for (Runnable callback : toRun) {
            try {
                callback.run();
            } catch (RuntimeException | Error e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first instanceof RuntimeException) {
            throw (RuntimeException) first;
        }
        if (first instanceof Error) {
            throw (Error) first;
        }
    }

    private void onCancel(Runnable callback) {
        Preconditions.requireNonNull(callback, "callback");
        boolean runNow;
        synchronized (lock) {
            runNow = cancelled;
            if (!runNow) {
                callbacks.add(callback);
            }
        }
        if (runNow) {
            callback.run();
        }
    }

    @Override
    public String toString() {
        return "CancellationSource{cancelled=" + isCancelled() + '}';
    }
}
