package com.aresstack.serp4j.browser;

/**
 * No session slot became free within {@link BrowserSessionOptions#getAcquireTimeout()} (WP3 §6.3, rule 5). There is
 * no unbounded queue.
 */
public final class BrowserCapacityException extends BrowserException {

    private static final long serialVersionUID = 1L;

    private final int maxConcurrentSessions;

    /**
     * @param maxConcurrentSessions the limit that was reached
     * @param message               message
     */
    public BrowserCapacityException(int maxConcurrentSessions, String message) {
        super(message);
        this.maxConcurrentSessions = maxConcurrentSessions;
    }

    /**
     * @return the limit that was reached
     */
    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }
}
