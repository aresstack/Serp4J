package com.aresstack.serp4j.browser;

/**
 * The browser crashed or the connection to it was lost ({@link BrowserSessionState#FAILED}). The session cannot be
 * recovered; a new session can be opened, the runtime restarts its browser if needed.
 */
public final class BrowserSessionLostException extends BrowserException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message message
     * @param cause   cause, may be {@code null}
     */
    public BrowserSessionLostException(String message, Throwable cause) {
        super(message, cause);
    }
}
