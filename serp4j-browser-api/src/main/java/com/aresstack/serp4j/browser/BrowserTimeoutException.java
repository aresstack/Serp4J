package com.aresstack.serp4j.browser;

/** An operation did not finish within its timeout. The session stays usable. */
public final class BrowserTimeoutException extends BrowserException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message message
     * @param cause   cause, may be {@code null}
     */
    public BrowserTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
