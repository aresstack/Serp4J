package com.aresstack.serp4j.browser;

import java.time.Duration;
import java.util.Optional;

/** Target and wait strategy of a navigation. Immutable. */
public final class NavigationRequest {

    private final String url;
    private final WaitUntil waitUntil;
    private final Duration timeout;

    private NavigationRequest(String url, WaitUntil waitUntil, Duration timeout) {
        this.url = Args.notBlank(url, "url").trim();
        this.waitUntil = Args.notNull(waitUntil, "waitUntil");
        this.timeout = timeout == null ? null : Args.positive(timeout, "timeout");
    }

    /**
     * @param url absolute target URL
     * @return a request that waits for {@link WaitUntil#LOAD} with the session's default timeout
     */
    public static NavigationRequest to(String url) {
        return new NavigationRequest(url, WaitUntil.LOAD, null);
    }

    /**
     * @param waitUntil wait strategy
     * @return a copy with the wait strategy
     */
    public NavigationRequest waitUntil(WaitUntil waitUntil) {
        return new NavigationRequest(url, waitUntil, timeout);
    }

    /**
     * @param timeout positive timeout
     * @return a copy with the timeout
     */
    public NavigationRequest timeout(Duration timeout) {
        return new NavigationRequest(url, waitUntil, Args.notNull(timeout, "timeout"));
    }

    /**
     * @return the target URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return the wait strategy
     */
    public WaitUntil getWaitUntil() {
        return waitUntil;
    }

    /**
     * @return the timeout; empty = session default
     */
    public Optional<Duration> getTimeout() {
        return Optional.ofNullable(timeout);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NavigationRequest)) {
            return false;
        }
        NavigationRequest that = (NavigationRequest) o;
        return url.equals(that.url) && waitUntil == that.waitUntil
                && (timeout == null ? that.timeout == null : timeout.equals(that.timeout));
    }

    @Override
    public int hashCode() {
        return 31 * (31 * url.hashCode() + waitUntil.hashCode()) + (timeout == null ? 0 : timeout.hashCode());
    }

    @Override
    public String toString() {
        return "NavigationRequest{" + url + ", waitUntil=" + waitUntil + ", timeout=" + timeout + '}';
    }
}
