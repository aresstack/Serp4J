package com.aresstack.serp4j.http;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A transport-neutral HTTP response: status, headers and body. The transport delivers every response the server
 * sent, including error statuses; interpreting the status is the provider's job. {@link #toString()} never shows
 * the body.
 */
public final class HttpResponse {

    private final int status;
    private final HttpHeaders headers;
    private final byte[] body;

    private HttpResponse(Builder builder) {
        this.status = builder.status;
        this.headers = builder.headers.build();
        this.body = builder.body;
    }

    public static Builder builder(int status) {
        return new Builder(status);
    }

    public int getStatus() {
        return status;
    }

    /** {@code 2xx}. */
    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public Optional<String> getContentType() {
        return headers.getFirst("Content-Type");
    }

    /** A copy of the body; empty array when there is none. */
    public byte[] getBody() {
        return body.clone();
    }

    public int getBodyLength() {
        return body.length;
    }

    /** Charset from the {@code Content-Type} header; UTF-8 when absent or unsupported. */
    public Charset getCharset() {
        return ContentTypes.charsetOrDefault(getContentType().orElse(null), StandardCharsets.UTF_8);
    }

    /** The body decoded with {@link #getCharset()}. */
    public String getBodyAsText() {
        return new String(body, getCharset());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HttpResponse)) {
            return false;
        }
        HttpResponse that = (HttpResponse) o;
        return status == that.status && headers.equals(that.headers) && Arrays.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, headers, Arrays.hashCode(body));
    }

    @Override
    public String toString() {
        return "HttpResponse{status=" + status + ", headers=" + headers + ", body=" + body.length + " bytes}";
    }

    /** Builder for {@link HttpResponse}. */
    public static final class Builder {
        private final int status;
        private final HttpHeaders.Builder headers = HttpHeaders.builder();
        private byte[] body = new byte[0];

        private Builder(int status) {
            if (status < 100 || status > 599) {
                throw new IllegalArgumentException("status must be in 100..599 but was " + status);
            }
            this.status = status;
        }

        public Builder header(String name, String value) {
            headers.add(name, value);
            return this;
        }

        public Builder headers(HttpHeaders headers) {
            this.headers.addAll(headers);
            return this;
        }

        /** Sets the body (copied). */
        public Builder body(byte[] body) {
            if (body == null) {
                throw new NullPointerException("body must not be null");
            }
            this.body = body.clone();
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
            headers.set("Content-Type", type);
            this.body = text.getBytes(charset);
            return this;
        }

        public HttpResponse build() {
            return new HttpResponse(this);
        }
    }
}
