package com.aresstack.serp4j.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A transport-neutral HTTP request (architecture.md §9.1): method, absolute URL, headers, optional body and an
 * optional per-request timeout. Immutable; the body is copied in and out.
 *
 * <p>Logging: {@link #toString()} never shows the body, masks sensitive headers and prints the URL as
 * {@link #getRedactedUrl()}, which masks the values of credential query parameters. {@link #getUrl()} is the URL
 * the transport sends and may contain a secret when {@link RequestAuthentication#queryParameter} is used; it must
 * never be logged.</p>
 */
public final class HttpRequest {

    /** Query parameter names (case-insensitive) whose values are always masked in {@link #getRedactedUrl()}. */
    private static final Set<String> SENSITIVE_QUERY_PARAMETERS = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("key", "api_key", "api-key", "apikey", "x-api-key", "token", "access_token", "access-token",
                    "auth", "auth_token", "authorization", "password", "passwd", "pwd", "secret", "client_secret",
                    "signature", "sig", "session", "session_id", "sessionid", "sid")));

    private static final String MASK = "****";

    private final HttpMethod method;
    private final String url;
    private final HttpHeaders headers;
    private final byte[] body;
    private final Duration timeout;
    private final Set<String> sensitiveQueryParameters;

    private HttpRequest(Builder builder) {
        this.method = builder.method;
        this.url = builder.url;
        this.headers = builder.headers.build();
        this.body = builder.body;
        this.timeout = builder.timeout;
        this.sensitiveQueryParameters = Collections.unmodifiableSet(new TreeSet<String>(builder.sensitiveQueryParameters));
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

    /** Whether a query parameter of this name is treated as a credential and masked in {@link #getRedactedUrl()}. */
    public static boolean isSensitiveQueryParameter(String name) {
        return name != null && SENSITIVE_QUERY_PARAMETERS.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    public HttpMethod getMethod() {
        return method;
    }

    /**
     * Absolute URL including the query string, exactly as the transport sends it; see {@link UrlQueryBuilder}.
     * May contain a credential; use {@link #getRedactedUrl()} for logs.
     */
    public String getUrl() {
        return url;
    }

    /**
     * The URL with the value of every credential query parameter replaced by {@code ****}: the
     * {@link #isSensitiveQueryParameter(String) default names} plus the names registered with
     * {@link Builder#sensitiveQueryParameter(String)}, which {@link RequestAuthentication#queryParameter} does.
     * Other parameters, the path and a fragment stay as they are.
     */
    public String getRedactedUrl() {
        int fragmentStart = url.indexOf('#');
        String fragment = fragmentStart < 0 ? "" : url.substring(fragmentStart);
        String withoutFragment = fragmentStart < 0 ? url : url.substring(0, fragmentStart);
        int queryStart = withoutFragment.indexOf('?');
        if (queryStart < 0 || queryStart == withoutFragment.length() - 1) {
            return url;
        }
        StringBuilder redacted = new StringBuilder(withoutFragment.substring(0, queryStart + 1));
        String[] pairs = withoutFragment.substring(queryStart + 1).split("&", -1);
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                redacted.append('&');
            }
            String pair = pairs[i];
            int equals = pair.indexOf('=');
            if (equals >= 0 && isSensitiveName(pair.substring(0, equals))) {
                redacted.append(pair, 0, equals + 1).append(MASK);
            } else {
                redacted.append(pair);
            }
        }
        return redacted.append(fragment).toString();
    }

    /** Lower-cased names registered as credentials for this request, in addition to the default names. */
    public Set<String> getSensitiveQueryParameters() {
        return sensitiveQueryParameters;
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
        builder.sensitiveQueryParameters.addAll(sensitiveQueryParameters);
        return builder;
    }

    private boolean isSensitiveName(String encodedName) {
        String name = decode(encodedName).trim().toLowerCase(Locale.ROOT);
        return isSensitiveQueryParameter(name) || sensitiveQueryParameters.contains(name);
    }

    private static String decode(String encodedName) {
        try {
            return URLDecoder.decode(encodedName, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return encodedName;
        }
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
                && Objects.equals(timeout, that.timeout)
                && sensitiveQueryParameters.equals(that.sensitiveQueryParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, url, headers, Arrays.hashCode(body), timeout, sensitiveQueryParameters);
    }

    /** Safe for logs: redacted URL, masked headers, no body. */
    @Override
    public String toString() {
        return "HttpRequest{" + method + " " + getRedactedUrl() + ", headers=" + headers
                + ", body=" + (body == null ? "none" : body.length + " bytes") + ", timeout=" + timeout + '}';
    }

    /** Builder for {@link HttpRequest}. */
    public static final class Builder {
        private final HttpMethod method;
        private String url;
        private final HttpHeaders.Builder headers = HttpHeaders.builder();
        private byte[] body;
        private Duration timeout;
        private final Set<String> sensitiveQueryParameters = new TreeSet<String>();

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

        /**
         * Registers a query parameter whose value is a credential, so that {@link HttpRequest#getRedactedUrl()} and
         * {@link HttpRequest#toString()} mask it. Names are matched case-insensitively.
         */
        public Builder sensitiveQueryParameter(String name) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("parameter name must not be blank");
            }
            sensitiveQueryParameters.add(name.trim().toLowerCase(Locale.ROOT));
            return this;
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

        /**
         * Text body encoded with the charset declared in {@code contentType}; when none is declared the text is
         * encoded as UTF-8 and {@code ; charset=UTF-8} is appended to the content type.
         *
         * @throws IllegalArgumentException if the declared charset is unsupported
         */
        public Builder textBody(String text, String contentType) {
            if (text == null) {
                throw new NullPointerException("text must not be null");
            }
            if (contentType == null || contentType.trim().isEmpty()) {
                throw new IllegalArgumentException("contentType must not be blank");
            }
            String type = contentType.trim();
            Charset charset = ContentTypes.requireCharsetOrUtf8(type);
            if (!ContentTypes.charsetName(type).isPresent()) {
                type = type + "; charset=UTF-8";
            }
            return body(text.getBytes(charset), type);
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
