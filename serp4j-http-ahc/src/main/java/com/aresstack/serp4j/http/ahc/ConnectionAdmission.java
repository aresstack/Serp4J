package com.aresstack.serp4j.http.ahc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Non-blocking admission control for requests in flight: at most {@code maxTotal} overall and {@code maxPerOrigin}
 * per origin. A request over the limit waits in FIFO order instead of failing, which is what async-http-client does
 * on its own ({@code TooManyConnectionsException}) – unacceptable for a transport shared by several providers.
 *
 * <p>Callbacks run outside the lock, on the thread that granted the permit. They are run through a per-thread
 * trampoline: a callback that releases its permit at once (e.g. because its request was cancelled meanwhile) does
 * not recurse into the next callback, so draining any number of waiters needs constant stack depth.</p>
 */
final class ConnectionAdmission {

    private final int maxTotal;
    private final int maxPerOrigin;
    private final Map<String, Integer> inFlightByOrigin = new HashMap<String, Integer>();
    private final ArrayDeque<Ticket> waiters = new ArrayDeque<Ticket>();
    private final ThreadLocal<ArrayDeque<Runnable>> trampoline = new ThreadLocal<ArrayDeque<Runnable>>();
    private int inFlight;

    ConnectionAdmission(int maxTotal, int maxPerOrigin) {
        this.maxTotal = maxTotal;
        this.maxPerOrigin = maxPerOrigin;
    }

    /**
     * Runs {@code onGranted} once a permit for {@code origin} is free – immediately or after a release.
     *
     * @return a ticket for {@link #withdraw(Ticket)} while the request waits; {@code null} if granted at once
     */
    Ticket acquire(String origin, Runnable onGranted) {
        Ticket waiting = null;
        synchronized (this) {
            if (hasWaiterFor(origin) || !tryTake(origin)) {
                waiting = new Ticket(origin, onGranted);
                waiters.add(waiting);
            }
        }
        if (waiting == null) {
            run(java.util.Collections.singletonList(onGranted));
        }
        return waiting;
    }

    /** Removes a waiting request from the queue; does nothing if it has been granted already. */
    synchronized void withdraw(Ticket ticket) {
        if (ticket != null) {
            waiters.remove(ticket);
        }
    }

    /** Returns a permit taken for {@code origin} and admits waiting requests that now fit. */
    void release(String origin) {
        List<Runnable> admitted = new ArrayList<Runnable>(1);
        synchronized (this) {
            inFlight--;
            Integer count = inFlightByOrigin.get(origin);
            if (count == null || count <= 1) {
                inFlightByOrigin.remove(origin);
            } else {
                inFlightByOrigin.put(origin, count - 1);
            }
            for (Iterator<Ticket> it = waiters.iterator(); it.hasNext() && inFlight < maxTotal; ) {
                Ticket ticket = it.next();
                if (tryTake(ticket.origin)) {
                    it.remove();
                    admitted.add(ticket.onGranted);
                }
            }
        }
        run(admitted);
    }

    synchronized int inFlight() {
        return inFlight;
    }

    synchronized int waiting() {
        return waiters.size();
    }

    private void run(List<Runnable> callbacks) {
        if (callbacks.isEmpty()) {
            return;
        }
        ArrayDeque<Runnable> queue = trampoline.get();
        if (queue != null) {
            // Already draining on this thread: the outer loop runs these, keeping the stack flat.
            queue.addAll(callbacks);
            return;
        }
        queue = new ArrayDeque<Runnable>(callbacks);
        trampoline.set(queue);
        try {
            Runnable next;
            while ((next = queue.poll()) != null) {
                try {
                    next.run();
                } catch (RuntimeException ignored) {
                    // A failing callback must not strand the ones behind it; callbacks handle their own errors.
                }
            }
        } finally {
            trampoline.remove();
        }
    }

    private boolean hasWaiterFor(String origin) {
        for (Ticket ticket : waiters) {
            if (ticket.origin.equals(origin)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryTake(String origin) {
        Integer count = inFlightByOrigin.get(origin);
        int current = count == null ? 0 : count;
        if (inFlight >= maxTotal || current >= maxPerOrigin) {
            return false;
        }
        inFlight++;
        inFlightByOrigin.put(origin, current + 1);
        return true;
    }

    /** A request waiting for a permit. */
    static final class Ticket {
        final String origin;
        final Runnable onGranted;

        Ticket(String origin, Runnable onGranted) {
            this.origin = origin;
            this.onGranted = onGranted;
        }
    }
}
