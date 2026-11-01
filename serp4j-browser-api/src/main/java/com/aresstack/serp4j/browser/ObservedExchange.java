package com.aresstack.serp4j.browser;

import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * A completed request/response exchange observed in a session (WP3 §5.3). Response headers are stored with
 * lower-case names; {@code Cookie}, {@code Set-Cookie}, {@code Authorization} and {@code Proxy-Authorization} are
 * removed on construction and can never be exposed. Immutable; the body is copied in and out.
 */
public final class ObservedExchange {

    private static final Set<String> FORBIDDEN_HEADERS = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("cookie", "set-cookie", "set-cookie2", "authorization", "proxy-authorization")));

    private final String method;
    private final String url;
    private final String resourceType;
    private final int status;
    private final Map<String, String> responseHeaders;
    private final byte[] body;
    private final boolean bodyTruncated;
    private final Instant finishedAt;

    private ObservedExchange(Builder builder) {
        this.method = Args.notBlank(builder.method, "method").toUpperCase(Locale.ROOT);
        this.url = Args.notBlank(builder.url, "url");
        this.resourceType = builder.resourceType == null ? "other" : builder.resourceType.toLowerCase(Locale.ROOT);
        this.status = builder.status;
        this.responseHeaders = Collections.unmodifiableMap(new TreeMap<String, String>(builder.responseHeaders));
        this.body = builder.body;
        this.bodyTruncated = builder.bodyTruncated;
        this.finishedAt = Args.notNull(builder.finishedAt, "finishedAt");
    }

    /**
     * @param method     HTTP method
     * @param url        request URL
     * @param status     response status
     * @param finishedAt completion time
     * @return a builder
     */
    public static Builder builder(String method, String url, int status, Instant finishedAt) {
        return new Builder(method, url, status, finishedAt);
    }

    /**
     * @param headerName header name
     * @return whether the header is never exposed
     */
    public static boolean isForbiddenHeader(String headerName) {
        return headerName != null && FORBIDDEN_HEADERS.contains(headerName.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * @return upper-case HTTP method
     */
    public String getMethod() {
        return method;
    }

    /**
     * @return request URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return lower-case resource type such as {@code document}, {@code xhr}, {@code fetch}; {@code other} if unknown
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * @return response status
     */
    public int getStatus() {
        return status;
    }

    /**
     * @return filtered response headers with lower-case names (unmodifiable)
     */
    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    /**
     * @return the {@code content-type} header
     */
    public Optional<String> getContentType() {
        return Optional.ofNullable(responseHeaders.get("content-type"));
    }

    /**
     * @return a copy of the body if it was captured
     */
    public Optional<byte[]> getBody() {
        return body == null ? Optional.<byte[]>empty() : Optional.of(body.clone());
    }

    /**
     * @param charset charset of the body
     * @return the body as text if it was captured
     */
    public Optional<String> getBodyAsString(Charset charset) {
        Args.notNull(charset, "charset");
        return body == null ? Optional.<String>empty() : Optional.of(new String(body, charset));
    }

    /**
     * @return whether the body was larger than {@link NetworkFilter#getMaxBodyBytes()}: either cut at that limit, or
     *         left out entirely ({@link #getBody()} empty) when the runtime knew its size beforehand and did not load it
     */
    public boolean isBodyTruncated() {
        return bodyTruncated;
    }

    /**
     * @return completion time
     */
    public Instant getFinishedAt() {
        return finishedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ObservedExchange)) {
            return false;
        }
        ObservedExchange that = (ObservedExchange) o;
        return status == that.status && bodyTruncated == that.bodyTruncated && method.equals(that.method)
                && url.equals(that.url) && resourceType.equals(that.resourceType)
                && responseHeaders.equals(that.responseHeaders) && Arrays.equals(body, that.body)
                && finishedAt.equals(that.finishedAt);
    }

    @Override
    public int hashCode() {
        int result = method.hashCode();
        result = 31 * result + url.hashCode();
        result = 31 * result + status;
        result = 31 * result + Arrays.hashCode(body);
        return 31 * result + finishedAt.hashCode();
    }

    /** Shows the body size only, never the body. */
    @Override
    public String toString() {
        return "ObservedExchange{" + method + " " + url + " -> " + status + ", type=" + resourceType
                + ", body=" + (body == null ? "none" : body.length + " bytes" + (bodyTruncated ? " (truncated)" : ""))
                + '}';
    }

    /** Builder for {@link ObservedExchange}. */
    public static final class Builder {
        private final String method;
        private final String url;
        private final int status;
        private final Instant finishedAt;
        private String resourceType;
        private final Map<String, String> responseHeaders = new TreeMap<String, String>();
        private byte[] body;
        private boolean bodyTruncated;

        private Builder(String method, String url, int status, Instant finishedAt) {
            this.method = method;
            this.url = url;
            this.status = status;
            this.finishedAt = finishedAt;
        }

        /**
         * @param resourceType resource type
         * @return this builder
         */
        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        /**
         * Adds a response header; forbidden headers are dropped silently.
         *
         * @param name  header name
         * @param value header value
         * @return this builder
         */
        public Builder responseHeader(String name, String value) {
            if (name != null && value != null && !isForbiddenHeader(name)) {
                responseHeaders.put(name.trim().toLowerCase(Locale.ROOT), value);
            }
            return this;
        }

        /**
         * Adds response headers; forbidden headers are dropped silently.
         *
         * @param headers header names and values
         * @return this builder
         */
        public Builder responseHeaders(Map<String, String> headers) {
            for (Map.Entry<String, String> entry : Args.notNull(headers, "headers").entrySet()) {
                responseHeader(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * Sets the body, cut at {@code maxBytes}.
         *
         * @param body     body, or {@code null} for none
         * @param maxBytes positive limit
         * @return this builder
         */
        public Builder body(byte[] body, int maxBytes) {
            Args.positive(maxBytes, "maxBytes");
            if (body == null) {
                this.body = null;
                this.bodyTruncated = false;
            } else {
                this.bodyTruncated = body.length > maxBytes;
                this.body = bodyTruncated ? Arrays.copyOf(body, maxBytes) : body.clone();
            }
            return this;
        }

        /**
         * Marks the body as too large to capture without loading it: {@link #getBody()} stays empty and
         * {@link #isBodyTruncated()} is {@code true}.
         *
         * @return this builder
         */
        public Builder bodyOmitted() {
            this.body = null;
            this.bodyTruncated = true;
            return this;
        }

        /**
         * @return the exchange
         */
        public ObservedExchange build() {
            return new ObservedExchange(this);
        }
    }
}
