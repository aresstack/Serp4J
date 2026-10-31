package com.aresstack.serp4j.browser;

/**
 * State of a {@link BrowserSession} (WP3 §6.2).
 *
 * <pre>
 * OPEN  -- command --&gt; BUSY -- result/timeout --&gt; OPEN
 * OPEN  -- close() --&gt; CLOSED
 * OPEN  -- budget exhausted (lifetime, idle, navigations) --&gt; EXPIRED
 * OPEN  -- window closed by the user (headed) --&gt; CLOSED_BY_USER
 * any   -- browser crash / disconnect --&gt; FAILED
 * </pre>
 */
public enum BrowserSessionState {
    /** Ready for commands. */
    OPEN,
    /** A command is running. */
    BUSY,
    /** Closed by {@link BrowserSession#close()} or by closing the runtime. */
    CLOSED,
    /** Closed because a budget ({@code maxLifetime}, {@code idleTimeout}, {@code maxNavigations}) was exhausted. */
    EXPIRED,
    /** The user closed the page or window of a visible browser. */
    CLOSED_BY_USER,
    /** The browser crashed or the connection to it was lost; the session cannot be recovered. */
    FAILED;

    /**
     * @return whether no further commands are possible
     */
    public boolean isTerminal() {
        return this != OPEN && this != BUSY;
    }
}
