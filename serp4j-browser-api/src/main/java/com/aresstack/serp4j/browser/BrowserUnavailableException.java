package com.aresstack.serp4j.browser;

/**
 * The runtime cannot open sessions: it was closed, or driver or browser are missing or do not start. The readiness
 * tells why.
 */
@SuppressWarnings("serial")
public final class BrowserUnavailableException extends BrowserException {

    private static final long serialVersionUID = 1L;

    private final BrowserReadiness readiness;

    /**
     * @param readiness the reason, not {@link BrowserReadiness.Status#READY}
     * @param cause     cause, may be {@code null}
     */
    public BrowserUnavailableException(BrowserReadiness readiness, Throwable cause) {
        super("browser runtime unavailable: " + readiness, cause);
        this.readiness = Args.notNull(readiness, "readiness");
    }

    /**
     * @return the reason
     */
    public BrowserReadiness getReadiness() {
        return readiness;
    }
}
