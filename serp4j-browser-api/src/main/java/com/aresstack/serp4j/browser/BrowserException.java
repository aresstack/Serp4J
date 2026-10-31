package com.aresstack.serp4j.browser;

/**
 * Base of all failures of the browser port. Unchecked, like the other transport-level exceptions of Serp4J; engine
 * adapters map it onto their error category (for example {@link BrowserCapacityException} to
 * {@code RATE_LIMITED}, {@link BrowserSessionLostException} to {@code SESSION_LOST}, architecture.md §5.4).
 *
 * <p>Messages never contain cookies, header secrets, page content or response bodies. Driver stack traces stay
 * in the cause chain and in the diagnostic log of the implementation.</p>
 */
public class BrowserException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message message without secrets or page content
     */
    public BrowserException(String message) {
        super(message);
    }

    /**
     * @param message message without secrets or page content
     * @param cause   cause
     */
    public BrowserException(String message, Throwable cause) {
        super(message, cause);
    }
}
