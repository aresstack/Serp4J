package com.aresstack.serp4j.http;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a URL with a query string, percent-encoding names and values per RFC 3986 (UTF-8; unreserved characters
 * {@code A-Z a-z 0-9 - _ . ~} stay as they are, spaces become {@code %20}). Parameters already present in the base
 * URL are kept.
 */
public final class UrlQueryBuilder {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private final String baseUrl;
    private final List<String[]> params = new ArrayList<String[]>();

    private UrlQueryBuilder(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** @param baseUrl absolute URL, with or without an existing query string */
    public static UrlQueryBuilder of(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        return new UrlQueryBuilder(baseUrl.trim());
    }

    public UrlQueryBuilder param(String name, String value) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("parameter name must not be empty");
        }
        if (value == null) {
            throw new NullPointerException("value of parameter '" + name + "' must not be null");
        }
        params.add(new String[] {name, value});
        return this;
    }

    public UrlQueryBuilder param(String name, int value) {
        return param(name, Integer.toString(value));
    }

    public UrlQueryBuilder param(String name, long value) {
        return param(name, Long.toString(value));
    }

    public UrlQueryBuilder param(String name, boolean value) {
        return param(name, Boolean.toString(value));
    }

    /** Adds the parameter only when the value is present. */
    public UrlQueryBuilder paramIfPresent(String name, Optional<String> value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        if (value.isPresent()) {
            param(name, value.get());
        }
        return this;
    }

    /** Adds the parameter only when the value is neither null nor blank. */
    public UrlQueryBuilder paramIfText(String name, String value) {
        if (value != null && !value.trim().isEmpty()) {
            param(name, value);
        }
        return this;
    }

    public String build() {
        StringBuilder url = new StringBuilder(baseUrl);
        String separator;
        if (baseUrl.indexOf('?') < 0) {
            separator = "?";
        } else if (baseUrl.endsWith("?") || baseUrl.endsWith("&")) {
            separator = "";
        } else {
            separator = "&";
        }
        for (String[] param : params) {
            url.append(separator).append(encode(param[0])).append('=').append(encode(param[1]));
            separator = "&";
        }
        return url.toString();
    }

    /** Percent-encodes a query component. */
    public static String encode(String text) {
        if (text == null) {
            throw new NullPointerException("text must not be null");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length + 8);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                encoded.append((char) c);
            } else {
                encoded.append('%').append(HEX[c >> 4]).append(HEX[c & 0x0F]);
            }
        }
        return encoded.toString();
    }

    @Override
    public String toString() {
        return build();
    }
}
