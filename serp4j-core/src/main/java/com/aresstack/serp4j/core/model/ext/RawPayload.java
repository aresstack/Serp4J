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
    /** Additional sensitive names registered on the builder; kept so that {@link #toBuilder()} filters alike. */
    private final Set<String> sensitiveHeaders;

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
        Set<String> sensitive = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        sensitive.addAll(builder.sensitiveHeaders);
        this.sensitiveHeaders = Collections.unmodifiableSet(sensitive);
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

    /**
     * Returns a builder pre-filled with this payload (body and headers are copied, registered sensitive header
     * names are carried over).
     */
    public Builder toBuilder() {
        Builder builder = new Builder(contentType, body);
        builder.charset = charset;
        builder.httpStatus = httpStatus;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            builder.headers.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
        }
        builder.sensitiveHeaders.addAll(sensitiveHeaders);
        return builder;
    }

    /** Returns whether {@code headerName} (trimmed, case-insensitive) is filtered out of raw payloads. */
    public static boolean isSensitiveHeader(String headerName) {
        return headerName != null && SENSITIVE_HEADERS.contains(headerName.trim());
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

    /** Consistent with {@link #equals(Object)}: header names are hashed case-insensitively. */
    @Override
    public int hashCode() {
        int hash = Objects.hash(contentType, Arrays.hashCode(body), charset, httpStatus);
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            hash += Objects.hash(foldCase(entry.getKey()), entry.getValue());
        }
        return hash;
    }

    /** Folds a header name the way {@link String#CASE_INSENSITIVE_ORDER} compares: per char, upper then lower case. */
    private static String foldCase(String name) {
        char[] chars = name.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = Character.toLowerCase(Character.toUpperCase(chars[i]));
        }
        return new String(chars);
    }

    /** Never includes the body. */
    @Override
    public String toString() {
        return "RawPayload{contentType='" + contentType + "', bytes=" + body.length
                + (httpStatus == null ? "" : ", httpStatus=" + httpStatus)
                + ", headers=" + headers.keySet() + '}';
    }

    /**
     * Builder; sensitive headers are dropped silently: the well-known names in {@link #SENSITIVE_HEADERS} and every
     * name registered with {@link #sensitiveHeader(String)} (for provider-specific credential headers).
     */
    public static final class Builder {
        private final String contentType;
        private final byte[] body;
        private Charset charset;
        private Integer httpStatus;
        private final Map<String, List<String>> headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        private final Set<String> sensitiveHeaders = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

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

        /**
         * Registers an additional header name (case-insensitive) that must never be stored, e.g. a provider's
         * custom credential header; values already added under that name are removed.
         */
        public Builder sensitiveHeader(String name) {
            String trimmed = Preconditions.requireText(name, "header name").trim();
            sensitiveHeaders.add(trimmed);
            headers.remove(trimmed);
            return this;
        }

        /** Adds a header value unless the header is sensitive; the name is trimmed before it is checked and stored. */
        public Builder header(String name, String value) {
            String trimmed = Preconditions.requireText(name, "header name").trim();
            Preconditions.requireNonNull(value, "header value");
            if (isSensitiveHeader(trimmed) || sensitiveHeaders.contains(trimmed)) {
                return this;
            }
            List<String> values = headers.get(trimmed);
            if (values == null) {
                values = new ArrayList<String>(1);
                headers.put(trimmed, values);
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
