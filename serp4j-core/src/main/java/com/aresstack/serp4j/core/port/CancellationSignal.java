package com.aresstack.serp4j.core.port;

/**
 * Cooperative cancellation (WP2 §5.1). {@code CompletableFuture.cancel()} does not interrupt running I/O on Java 8,
 * so providers poll {@link #isCancelled()} between steps or register a callback with {@link #onCancel(Runnable)}.
 * Obtain a cancellable signal from {@link CancellationSource}.
 */
public interface CancellationSignal {

    /** A signal that is never cancelled. */
    CancellationSignal NONE = new CancellationSignal() {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void onCancel(Runnable callback) {
            // never fires
        }

        @Override
        public String toString() {
            return "CancellationSignal.NONE";
        }
    };

    boolean isCancelled();

    /**
     * Registers a callback that runs exactly once when the signal is cancelled – immediately and on the calling
     * thread if it already is. Callbacks must be quick and must not throw: a callback that throws does not stop the
     * other callbacks from running, but its exception (the first one thrown) reaches the party that cancels, see
     * {@link CancellationSource#cancel()}.
     */
    void onCancel(Runnable callback);
}
