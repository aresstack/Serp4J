package com.aresstack.serp4j.http.resolve;

/**
 * A URL could not be resolved. The message never contains the URL or its token; {@link #getReason()} is a short
 * stable code used to summarise failures in one {@code SearchWarning}.
 */
public final class UrlResolutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The URL is not a supported redirect URL or carries no usable token. */
    public static final String UNSUPPORTED = "unsupported_url";
    /** The response was not a redirect (e.g. 403, 429, 500 or 200). */
    public static final String NOT_A_REDIRECT = "not_a_redirect";
    /** The redirect had no, an empty, a relative or a non-HTTP(S) {@code Location}. */
    public static final String INVALID_LOCATION = "invalid_location";
    /** The request timed out or the search deadline left no time for it. */
    public static final String TIMEOUT = "timeout";
    /** The request failed without a response. */
    public static final String NETWORK = "network";

    private final String reason;

    public UrlResolutionException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public UrlResolutionException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
