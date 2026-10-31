package com.aresstack.serp4j.browser;

/**
 * The session is closed: by {@link BrowserSession#close()} or the runtime ({@link BrowserSessionState#CLOSED}), by
 * an exhausted budget ({@link BrowserSessionState#EXPIRED}) or by the user ({@link BrowserSessionState#CLOSED_BY_USER}).
 */
public final class BrowserSessionClosedException extends BrowserException {

    private static final long serialVersionUID = 1L;

    private final BrowserSessionState state;

    /**
     * @param state   terminal state of the session
     * @param message message
     */
    public BrowserSessionClosedException(BrowserSessionState state, String message) {
        super(message);
        this.state = Args.notNull(state, "state");
    }

    /**
     * @return terminal state of the session
     */
    public BrowserSessionState getState() {
        return state;
    }
}
