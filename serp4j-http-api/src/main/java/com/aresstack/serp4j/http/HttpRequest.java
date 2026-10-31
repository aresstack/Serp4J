package com.aresstack.serp4j.http;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A transport-neutral HTTP request (architecture.md §9.1): method, absolute URL, headers, optional body and an
 * optional per-request timeout. Immutable; the body is copied in and out. {@link #toString()} never shows the body
 * and masks sensitive headers.
 */
public final class HttpRequest {

    private final HttpMethod method;
    private final String url;
    private final HttpHeaders headers;
    private final byte[] body;
    private final Duration timeout;

    private HttpRequest(Builder builder) {
        this.method = builder.method;
        this.url = builder.url;
        this.headers = builder.headers.build();
        this.body = builder.body;
        this.timeout = builder.timeout;
    }

    public static Builder builder(HttpMethod method, String url) {
        return new Builder(method, url);
    }

    public static Builder get(String url) {
        return builder(HttpMethod.GET, url);
    }

    public static Builder post(String url) {
        return builder(HttpMethod.POST, url);
    }

    public HttpMethod getMethod() {
        return method;
    }

    /** Absolute URL including the query string; see {@link UrlQueryBuilder}. */
    public String getUrl() {
        return url;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public boolean hasBody() {
        return body != null;
    }

    /** A copy of the body; empty array when there is none. */
    public byte[] getBody() {
        return body == null ? new byte[0] : body.clone();
    }

    public Optional<String> getContentType() {
        return headers.getFirst("Content-Type");
    }

    /** Timeout for this request; empty = transport default. */
    public Optional<Duration> getTimeout() {
        return Optional.ofNullable(timeout);
    }

    public Builder toBuilder() {
        Builder builder = new Builder(method, url);
        builder.headers.addAll(headers);
        builder.body = body;
        builder.timeout = timeout;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpRequest)) {
            return false;
        }
        HttpRequest that = (HttpRequest) o;
        return method == that.method
                && url.equals(that.url)
                && headers.equals(that.headers)
                && Arrays.equals(body, that.body)
                && Objects.equals(timeout, that.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, url, headers, Arrays.hashCode(body), timeout);
    }

    @Override
    public String toString() {
        return "HttpRequest{" + method + " " + url + ", headers=" + headers
                + ", body=" + (body == null ? "none" : body.length + " bytes") + ", timeout=" + timeout + '}';
    }

    /** Builder for {@link HttpRequest}. */
    public static final class Builder {
        private final HttpMethod method;
        private String url;
        private final HttpHeaders.Builder headers = HttpHeaders.builder();
        private byte[] body;
        private Duration timeout;

        private Builder(HttpMethod method, String url) {
            if (method == null) {
                throw new NullPointerException("method must not be null");
            }
            this.method = method;
            url(url);
        }

        /** Replaces the URL, e.g. after adding query parameters. */
        public Builder url(String url) {
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalArgumentException("url must not be blank");
            }
            this.url = url.trim();
            return this;
        }

        public String getUrl() {
            return url;
        }

        /** Appends a header value. */
        public Builder header(String name, String value) {
            headers.add(name, value);
            return this;
        }

        /** Replaces all values of a header. */
        public Builder setHeader(String name, String value) {
            headers.set(name, value);
            return this;
        }

        public Builder removeHeader(String name) {
            headers.remove(name);
            return this;
        }

        public Builder headers(HttpHeaders headers) {
            this.headers.addAll(headers);
            return this;
        }

        /** Sets the body (copied) and the {@code Content-Type} header. */
        public Builder body(byte[] body, String contentType) {
            if (body == null) {
                throw new NullPointerException("body must not be null");
            }
            if (contentType == null || contentType.trim().isEmpty()) {
                throw new IllegalArgumentException("contentType must not be blank");
            }
            this.body = body.clone();
            headers.set("Content-Type", contentType.trim());
            return this;
        }

        /** UTF-8 text body; {@code charset=UTF-8} is appended to the content type when it names none. */
        public Builder textBody(String text, String contentType) {
            if (text == null) {
                throw new NullPointerException("text must not be null");
            }
            if (contentType == null || contentType.trim().isEmpty()) {
                throw new IllegalArgumentException("contentType must not be blank");
            }
            String type = contentType.trim();
            if (!type.toLowerCase(java.util.Locale.ROOT).contains("charset=")) {
                type = type + "; charset=UTF-8";
            }
            return body(text.getBytes(StandardCharsets.UTF_8), type);
        }

        public Builder jsonBody(String json) {
            return textBody(json, "application/json");
        }

        public Builder timeout(Duration timeout) {
            if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
                throw new IllegalArgumentException("timeout must be positive but was " + timeout);
            }
            this.timeout = timeout;
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest(this);
        }
    }
}
