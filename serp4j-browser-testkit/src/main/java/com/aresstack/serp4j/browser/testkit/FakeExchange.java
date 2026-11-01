package com.aresstack.serp4j.browser.testkit;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A network exchange that a {@link FakePage} triggers after it was loaded, for example a background {@code fetch}.
 * It becomes visible to network captures {@link #getDelay()} after the navigation, also while no command runs.
 * Immutable.
 */
public final class FakeExchange {

    private final String method;
    private final String url;
    private final String resourceType;
    private final int status;
    private final Map<String, String> headers;
    private final byte[] body;
    private final Duration delay;

    private FakeExchange(Builder builder) {
        this.method = builder.method;
        this.url = builder.url;
        this.resourceType = builder.resourceType;
        this.status = builder.status;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.headers));
        this.body = builder.body;
        this.delay = builder.delay;
    }

    /**
     * @param url absolute or page-relative URL
     * @return a builder for a {@code GET} {@code fetch} with status 200
     */
    public static Builder get(String url) {
        return new Builder("GET", url);
    }

    /**
     * @param method HTTP method
     * @param url    absolute or page-relative URL
     * @return a builder
     */
    public static Builder builder(String method, String url) {
        return new Builder(method, url);
    }

    String getMethod() {
        return method;
    }

    String getUrl() {
        return url;
    }

    String getResourceType() {
        return resourceType;
    }

    int getStatus() {
        return status;
    }

    Map<String, String> getHeaders() {
        return headers;
    }

    byte[] getBody() {
        return body == null ? null : body.clone();
    }

    /**
     * @return delay after the navigation
     */
    public Duration getDelay() {
        return delay;
    }

    @Override
    public String toString() {
        return "FakeExchange{" + method + " " + url + " -> " + status + ", delay=" + delay + '}';
    }

    /** Builder for {@link FakeExchange}. */
    public static final class Builder {
        private final String method;
        private final String url;
        private String resourceType = "fetch";
        private int status = 200;
        private final Map<String, String> headers = new LinkedHashMap<String, String>();
        private byte[] body;
        private Duration delay = Duration.ZERO;

        private Builder(String method, String url) {
            if (method == null || url == null) {
                throw new NullPointerException("method and url must not be null");
            }
            this.method = method;
            this.url = url;
        }

        /**
         * @param resourceType resource type, default {@code fetch}
         * @return this builder
         */
        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        /**
         * @param status response status, default 200
         * @return this builder
         */
        public Builder status(int status) {
            this.status = status;
            return this;
        }

        /**
         * @param name  response header name; forbidden headers are dropped when observed
         * @param value value
         * @return this builder
         */
        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /**
         * @param body        UTF-8 body
         * @param contentType content type
         * @return this builder
         */
        public Builder body(String body, String contentType) {
            this.body = body.getBytes(Charset.forName("UTF-8"));
            headers.put("Content-Type", contentType);
            return this;
        }

        /**
         * @param delay delay after the navigation, default zero
         * @return this builder
         */
        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /**
         * @return the exchange
         */
        public FakeExchange build() {
            return new FakeExchange(this);
        }
    }
}
