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
 * Callbacks run outside the lock, on the thread that granted the permit.
 */
final class ConnectionAdmission {

    private final int maxTotal;
    private final int maxPerOrigin;
    private final Map<String, Integer> inFlightByOrigin = new HashMap<String, Integer>();
    private final ArrayDeque<Waiter> waiters = new ArrayDeque<Waiter>();
    private int inFlight;

    ConnectionAdmission(int maxTotal, int maxPerOrigin) {
        this.maxTotal = maxTotal;
        this.maxPerOrigin = maxPerOrigin;
    }

    /** Runs {@code onGranted} once a permit for {@code origin} is free – immediately or after a release. */
    void acquire(String origin, Runnable onGranted) {
        boolean granted;
        synchronized (this) {
            granted = !hasWaiterFor(origin) && tryTake(origin);
            if (!granted) {
                waiters.add(new Waiter(origin, onGranted));
            }
        }
        if (granted) {
            onGranted.run();
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
            for (Iterator<Waiter> it = waiters.iterator(); it.hasNext() && inFlight < maxTotal; ) {
                Waiter waiter = it.next();
                if (tryTake(waiter.origin)) {
                    it.remove();
                    admitted.add(waiter.onGranted);
                }
            }
        }
        for (Runnable runnable : admitted) {
            runnable.run();
        }
    }

    synchronized int inFlight() {
        return inFlight;
    }

    synchronized int waiting() {
        return waiters.size();
    }

    private boolean hasWaiterFor(String origin) {
        for (Waiter waiter : waiters) {
            if (waiter.origin.equals(origin)) {
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

    private static final class Waiter {
        final String origin;
        final Runnable onGranted;

        Waiter(String origin, Runnable onGranted) {
            this.origin = origin;
            this.onGranted = onGranted;
        }
    }
}
