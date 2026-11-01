package com.aresstack.serp4j.browser;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.OptionalInt;

/** Outcome of a navigation. Immutable. */
public final class NavigationResult {

    private final String requestedUrl;
    private final String finalUrl;
    private final Integer httpStatus;
    private final Duration elapsed;

    private NavigationResult(String requestedUrl, String finalUrl, Integer httpStatus, Duration elapsed) {
        this.requestedUrl = Args.notBlank(requestedUrl, "requestedUrl");
        this.finalUrl = Args.notBlank(finalUrl, "finalUrl");
        this.httpStatus = httpStatus;
        this.elapsed = Args.notNegative(elapsed, "elapsed");
    }

    /**
     * @param requestedUrl URL that was requested
     * @param finalUrl     URL after redirects
     * @param httpStatus   status of the main document, or {@code null} if there was no response (same-document
     *                     navigation, {@code about:blank})
     * @param elapsed      duration of the navigation
     * @return the result
     */
    public static NavigationResult of(String requestedUrl, String finalUrl, Integer httpStatus, Duration elapsed) {
        return new NavigationResult(requestedUrl, finalUrl, httpStatus, elapsed);
    }

    /**
     * @return the requested URL
     */
    public String getRequestedUrl() {
        return requestedUrl;
    }

    /**
     * @return the URL after redirects
     */
    public String getFinalUrl() {
        return finalUrl;
    }

    /**
     * @return HTTP status of the main document; empty if there was no response
     */
    public OptionalInt getHttpStatus() {
        return httpStatus == null ? OptionalInt.empty() : OptionalInt.of(httpStatus);
    }

    /**
     * @return duration of the navigation
     */
    public Duration getElapsed() {
        return elapsed;
    }

    /**
     * @return whether the final URL differs from the requested one
     */
    public boolean isRedirected() {
        return !requestedUrl.equals(finalUrl);
    }

    /**
     * @return the generic obstacle signal: {@link PageObstacle#HTTP_ERROR} wins over
     * {@link PageObstacle#FOREIGN_HOST_REDIRECT}
     */
    public PageObstacle getObstacle() {
        if (httpStatus != null && httpStatus >= 400) {
            return PageObstacle.HTTP_ERROR;
        }
        String requestedHost = host(requestedUrl);
        String finalHost = host(finalUrl);
        if (requestedHost != null && finalHost != null && !requestedHost.equals(finalHost)) {
            return PageObstacle.FOREIGN_HOST_REDIRECT;
        }
        return PageObstacle.NONE;
    }

    private static String host(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NavigationResult)) {
            return false;
        }
        NavigationResult that = (NavigationResult) o;
        return requestedUrl.equals(that.requestedUrl) && finalUrl.equals(that.finalUrl)
                && (httpStatus == null ? that.httpStatus == null : httpStatus.equals(that.httpStatus))
                && elapsed.equals(that.elapsed);
    }

    @Override
    public int hashCode() {
        int result = requestedUrl.hashCode();
        result = 31 * result + finalUrl.hashCode();
        result = 31 * result + (httpStatus == null ? 0 : httpStatus);
        return 31 * result + elapsed.hashCode();
    }

    @Override
    public String toString() {
        return "NavigationResult{" + requestedUrl + " -> " + finalUrl + ", status=" + httpStatus
                + ", elapsed=" + elapsed + ", obstacle=" + getObstacle() + '}';
    }
}
