package com.aresstack.serp4j.browser;

/**
 * A navigation target or the final URL after redirects is not allowed by the session's {@link UrlPolicy}. The page
 * is left on {@code about:blank}; the session stays usable.
 */
public final class UrlPolicyViolationException extends BrowserException {

    private static final long serialVersionUID = 1L;

    private final String url;

    /**
     * @param url     the URL that is not allowed
     * @param message message
     */
    public UrlPolicyViolationException(String url, String message) {
        super(message);
        this.url = url;
    }

    /**
     * @return the URL that is not allowed
     */
    public String getUrl() {
        return url;
    }
}
