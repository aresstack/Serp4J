package com.aresstack.serp4j.core.error;

/**
 * Classification of a provider failure (ADR-007, WP2 §7.1). Every transport, protocol and parsing error of an
 * adapter is mapped to exactly one category.
 *
 * <p><em>Retryable</em> means the same provider may be asked again later; <em>fallback eligible</em> means a
 * different provider can meaningfully step in. Retries within a provider (polling) are the adapter's business,
 * retries and fallbacks across providers belong to the orchestrator.</p>
 */
public enum SearchErrorCategory {

    /** Credentials missing, invalid or expired. */
    AUTHENTICATION(false, true),
    /** Provider instance misconfigured (zone, endpoint, option combination). */
    CONFIGURATION(false, true),
    /** The request itself is invalid and would fail at every provider. */
    INVALID_REQUEST(false, false),
    /** A required feature, target or operation is not supported by this provider. */
    UNSUPPORTED(false, true),
    /** The provider throttled the call; see {@link SearchProviderException#getRetryAfter()}. */
    RATE_LIMITED(true, true),
    /** The account's quota or credit is exhausted. */
    QUOTA_EXHAUSTED(false, true),
    /** The deadline or a transport timeout was hit. */
    TIMEOUT(true, true),
    /** Provider or network unavailable (5xx, connection failures). */
    UNAVAILABLE(true, true),
    /** CAPTCHA or anti-bot measures blocked the request (mainly browser providers). */
    BLOCKED(false, true),
    /** The provider answered, but the response could not be parsed or violated the expected schema. */
    RESPONSE_INVALID(false, true),
    /** Cancelled through the {@code SearchContext} or by closing the provider. */
    CANCELLED(false, false),
    /** The conversational session expired at the provider (sessions only). */
    SESSION_EXPIRED(false, false),
    /** Another turn of the same session is still running (sessions only). */
    SESSION_BUSY(true, false),
    /** The session was closed (sessions only). */
    SESSION_CLOSED(false, false),
    /** The session's turn limit was reached; the session stays readable (sessions only). */
    TURN_LIMIT_REACHED(false, false),
    /** The session's underlying resource is gone, e.g. browser crash or closed window; not recoverable. */
    SESSION_LOST(false, false),
    /** Programming error inside the adapter or library. */
    INTERNAL(false, false);

    private final boolean retryable;
    private final boolean fallbackEligible;

    SearchErrorCategory(boolean retryable, boolean fallbackEligible) {
        this.retryable = retryable;
        this.fallbackEligible = fallbackEligible;
    }

    /** Default for {@link SearchProviderException#isRetryable()}; an exception may override it. */
    public boolean isRetryable() {
        return retryable;
    }

    /** Whether another provider can meaningfully take over after this failure. */
    public boolean isFallbackEligible() {
        return fallbackEligible;
    }

    /** Whether the category only occurs for conversational sessions. */
    public boolean isSessionCategory() {
        return this == SESSION_EXPIRED || this == SESSION_BUSY || this == SESSION_CLOSED
                || this == TURN_LIMIT_REACHED || this == SESSION_LOST;
    }
}
