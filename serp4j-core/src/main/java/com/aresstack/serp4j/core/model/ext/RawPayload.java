package com.aresstack.serp4j.core.model.ext;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

/**
 * The raw provider response as received (ADR-006): content type, body bytes, optional charset, HTTP status
 * and <em>filtered</em> headers.
 *
 * <p>Security rule: headers that carry credentials or cookies ({@link #SENSITIVE_HEADERS}) are removed
 * when the payload is built, so a raw payload can never leak an API key. Mappers must additionally make sure
 * that the body itself contains no secret (a request echo, for example). The body is copied on construction
 * and on every {@link #getBody()} call.</p>
 */
public final class RawPayload {

    /** Header names (case-insensitive) that are never stored. */
    public static final Set<String> SENSITIVE_HEADERS;

    static {
        Set<String> names = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(Arrays.asList(
                "Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie",
                "X-API-Key", "Api-Key", "X-Auth-Token", "X-Access-Token", "X-Subscription-Token",
                "X-Amz-Security-Token", "X-Goog-Api-Key"));
        SENSITIVE_HEADERS = Collections.unmodifiableSet(names);
    }

    private final String contentType;
    private final byte[] body;
    private final Charset charset;
    private final Integer httpStatus;
    private final Map<String, List<String>> headers;

    private RawPayload(Builder builder) {
        this.contentType = builder.contentType;
        this.body = builder.body;
        this.charset = builder.charset;
        this.httpStatus = builder.httpStatus;
        Map<String, List<String>> copy = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<String>> entry : builder.headers.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<String>(entry.getValue())));
        }
        this.headers = Collections.unmodifiableMap(copy);
    }

    /** Starts a payload; {@code body} is copied. */
    public static Builder builder(String contentType, byte[] body) {
        return new Builder(contentType, body);
    }

    /** Convenience for textual payloads encoded as UTF-8. */
    public static RawPayload ofText(String contentType, String text) {
        return builder(contentType, Preconditions.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8))
                .charset(StandardCharsets.UTF_8)
                .build();
    }

    /** Returns whether {@code headerName} is filtered out of raw payloads. */
    public static boolean isSensitiveHeader(String headerName) {
        return headerName != null && SENSITIVE_HEADERS.contains(headerName);
    }

    /** Media type such as {@code application/json}, {@code text/html} or {@code image/png}. */
    public String getContentType() {
        return contentType;
    }

    /** A copy of the body. */
    public byte[] getBody() {
        return body.clone();
    }

    public int getBodyLength() {
        return body.length;
    }

    public Optional<Charset> getCharset() {
        return Optional.ofNullable(charset);
    }

    public OptionalInt getHttpStatus() {
        return httpStatus == null ? OptionalInt.empty() : OptionalInt.of(httpStatus);
    }

    /** Filtered headers, keyed case-insensitively; values are unmodifiable lists. */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /** First value of {@code name}, if present (case-insensitive). */
    public Optional<String> getHeader(String name) {
        List<String> values = headers.get(Preconditions.requireNonNull(name, "name"));
        return values == null || values.isEmpty() ? Optional.<String>empty() : Optional.of(values.get(0));
    }

    /** Decodes the body with the declared charset, falling back to UTF-8. */
    public String getBodyAsText() {
        return new String(body, charset == null ? StandardCharsets.UTF_8 : charset);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RawPayload)) {
            return false;
        }
        RawPayload that = (RawPayload) o;
        return contentType.equals(that.contentType)
                && Arrays.equals(body, that.body)
                && Objects.equals(charset, that.charset)
                && Objects.equals(httpStatus, that.httpStatus)
                && headers.equals(that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentType, Arrays.hashCode(body), charset, httpStatus, headers);
    }

    /** Never includes the body. */
    @Override
    public String toString() {
        return "RawPayload{contentType='" + contentType + "', bytes=" + body.length
                + (httpStatus == null ? "" : ", httpStatus=" + httpStatus)
                + ", headers=" + headers.keySet() + '}';
    }

    /** Builder; sensitive headers are dropped silently. */
    public static final class Builder {
        private final String contentType;
        private final byte[] body;
        private Charset charset;
        private Integer httpStatus;
        private final Map<String, List<String>> headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);

        private Builder(String contentType, byte[] body) {
            this.contentType = Preconditions.requireText(contentType, "contentType").toLowerCase(Locale.ROOT);
            this.body = Preconditions.requireNonNull(body, "body").clone();
        }

        public Builder charset(Charset charset) {
            this.charset = charset;
            return this;
        }

        /** @throws IllegalArgumentException if the status is not in 100..599 */
        public Builder httpStatus(int httpStatus) {
            if (httpStatus < 100 || httpStatus > 599) {
                throw new IllegalArgumentException("httpStatus must be between 100 and 599 but was " + httpStatus);
            }
            this.httpStatus = httpStatus;
            return this;
        }

        /** Adds a header value unless the header is sensitive. */
        public Builder header(String name, String value) {
            Preconditions.requireText(name, "header name");
            Preconditions.requireNonNull(value, "header value");
            if (isSensitiveHeader(name)) {
                return this;
            }
            List<String> values = headers.get(name);
            if (values == null) {
                values = new ArrayList<String>(1);
                headers.put(name, values);
            }
            values.add(value);
            return this;
        }

        /** Adds all header values unless the header is sensitive. */
        public Builder headers(Map<String, ? extends Collection<String>> headers) {
            for (Map.Entry<String, ? extends Collection<String>> entry : Preconditions.requireNonNull(headers, "headers").entrySet()) {
                for (String value : Preconditions.requireNonNull(entry.getValue(), "header values")) {
                    header(entry.getKey(), value);
                }
            }
            return this;
        }

        /** Builds a payload with its own copy of the headers; the builder stays usable. */
        public RawPayload build() {
            return new RawPayload(this);
        }
    }
}
