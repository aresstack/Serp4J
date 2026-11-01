package com.aresstack.serp4j.browser;

/**
 * Result of {@link BrowserRuntime#probe()}: an explicit readiness status without silent fallbacks (WP3 §1.3, item 6).
 * Immutable.
 */
public final class BrowserReadiness {

    /** Readiness status. */
    public enum Status {
        /** Sessions can be opened. */
        READY,
        /** No driver is available, for example no Playwright driver module on the classpath. */
        DRIVER_NOT_FOUND,
        /** More than one driver is available; exactly one is allowed (ADR-011). */
        MULTIPLE_DRIVERS,
        /** The available driver does not fit this runtime (version, Java version, replaced classes). */
        INCOMPATIBLE_DRIVER,
        /** The configured browser is not installed; the runtime never downloads browsers. */
        BROWSER_NOT_INSTALLED,
        /** The browser is installed but could not be started or connected. */
        BROWSER_START_FAILED,
        /** The runtime has been closed. */
        CLOSED
    }

    private final Status status;
    private final String detail;

    private BrowserReadiness(Status status, String detail) {
        this.status = Args.notNull(status, "status");
        this.detail = detail == null ? "" : detail;
    }

    /**
     * @param detail human-readable detail, for example the browser executable
     * @return a {@link Status#READY} result
     */
    public static BrowserReadiness ready(String detail) {
        return new BrowserReadiness(Status.READY, detail);
    }

    /**
     * @param status status
     * @param detail human-readable reason without secrets
     * @return the result
     */
    public static BrowserReadiness of(Status status, String detail) {
        return new BrowserReadiness(status, detail);
    }

    /**
     * @return the status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return human-readable detail; empty if none
     */
    public String getDetail() {
        return detail;
    }

    /**
     * @return whether the status is {@link Status#READY}
     */
    public boolean isReady() {
        return status == Status.READY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BrowserReadiness)) {
            return false;
        }
        BrowserReadiness that = (BrowserReadiness) o;
        return status == that.status && detail.equals(that.detail);
    }

    @Override
    public int hashCode() {
        return 31 * status.hashCode() + detail.hashCode();
    }

    @Override
    public String toString() {
        return detail.isEmpty() ? status.name() : status + ": " + detail;
    }
}
