package com.aresstack.serp4j.http;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * Adds credentials to a request (architecture.md §9): header schemes, bearer tokens, basic authentication or a
 * query parameter. The secret is fetched from the supplier at every call, used, and the supplied array is wiped,
 * so a strategy can be built once per provider while the secret stays with the host's credential provider. The
 * resulting header or URL necessarily holds the secret as a string for the lifetime of the request; transports
 * and providers must never log it ({@link HttpHeaders#isSensitive}).
 */
@FunctionalInterface
public interface RequestAuthentication {

    /** Adds the credentials to the request under construction. */
    void apply(HttpRequest.Builder request);

    /** No authentication. */
    static RequestAuthentication none() {
        return request -> {
            if (request == null) {
                throw new NullPointerException("request must not be null");
            }
        };
    }

    /**
     * Sets {@code <name>: <prefix><secret>}, e.g. {@code X-Subscription-Token: <key>} with an empty prefix or
     * {@code Authorization: Token <key>}.
     */
    static RequestAuthentication header(String name, String prefix, Supplier<char[]> secret) {
        requireText(name, "name");
        if (prefix == null) {
            throw new NullPointerException("prefix must not be null; use an empty string");
        }
        requireNonNull(secret, "secret");
        return request -> {
            requireNonNull(request, "request");
            char[] chars = requireNonNull(secret.get(), "secret value");
            try {
                request.setHeader(name, prefix + new String(chars));
            } finally {
                Arrays.fill(chars, '\0');
            }
        };
    }

    /** {@code Authorization: Bearer <token>}. */
    static RequestAuthentication bearer(Supplier<char[]> token) {
        return header("Authorization", "Bearer ", token);
    }

    /** {@code Authorization: Basic base64(username:password)}. */
    static RequestAuthentication basic(String username, Supplier<char[]> password) {
        requireText(username, "username");
        requireNonNull(password, "password");
        return request -> {
            requireNonNull(request, "request");
            char[] secret = requireNonNull(password.get(), "password value");
            char[] joined = new char[username.length() + 1 + secret.length];
            byte[] bytes = null;
            try {
                username.getChars(0, username.length(), joined, 0);
                joined[username.length()] = ':';
                System.arraycopy(secret, 0, joined, username.length() + 1, secret.length);
                ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(joined));
                bytes = new byte[encoded.remaining()];
                encoded.get(bytes);
                request.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(bytes));
            } finally {
                Arrays.fill(secret, '\0');
                Arrays.fill(joined, '\0');
                if (bytes != null) {
                    Arrays.fill(bytes, (byte) 0);
                }
            }
        };
    }

    /**
     * Appends {@code <name>=<secret>} to the URL's query string (for APIs that only accept keys in the URL) and
     * registers the parameter as sensitive, so {@link HttpRequest#getRedactedUrl()} and
     * {@link HttpRequest#toString()} mask it.
     */
    static RequestAuthentication queryParameter(String name, Supplier<char[]> secret) {
        requireText(name, "name");
        requireNonNull(secret, "secret");
        return request -> {
            requireNonNull(request, "request");
            char[] chars = requireNonNull(secret.get(), "secret value");
            try {
                request.sensitiveQueryParameter(name);
                request.url(UrlQueryBuilder.of(request.getUrl()).param(name, new String(chars)).build());
            } finally {
                Arrays.fill(chars, '\0');
            }
        };
    }

    /** Applies several strategies in order. */
    static RequestAuthentication all(RequestAuthentication... strategies) {
        requireNonNull(strategies, "strategies");
        final RequestAuthentication[] copy = strategies.clone();
        for (RequestAuthentication strategy : copy) {
            requireNonNull(strategy, "strategy");
        }
        return request -> {
            for (RequestAuthentication strategy : copy) {
                strategy.apply(request);
            }
        };
    }

    static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        return value;
    }

    static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
