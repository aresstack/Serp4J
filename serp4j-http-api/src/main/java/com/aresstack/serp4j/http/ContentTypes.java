package com.aresstack.serp4j.http;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;
import java.util.Optional;

/** Reads the {@code charset} parameter of a media type such as {@code text/html; charset=ISO-8859-1}. */
final class ContentTypes {

    private static final String CHARSET_PARAMETER = "charset=";

    private ContentTypes() {
    }

    /** The declared charset name, unquoted and trimmed; empty when the content type declares none. */
    static Optional<String> charsetName(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        int index = contentType.toLowerCase(Locale.ROOT).indexOf(CHARSET_PARAMETER);
        if (index < 0) {
            return Optional.empty();
        }
        String name = contentType.substring(index + CHARSET_PARAMETER.length()).trim();
        int end = name.indexOf(';');
        if (end >= 0) {
            name = name.substring(0, end).trim();
        }
        if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
            name = name.substring(1, name.length() - 1).trim();
        }
        return name.isEmpty() ? Optional.<String>empty() : Optional.of(name);
    }

    /** The declared charset, or {@code fallback} when none is declared or the name is unknown to this JVM. */
    static Charset charsetOrDefault(String contentType, Charset fallback) {
        Optional<String> name = charsetName(contentType);
        if (!name.isPresent()) {
            return fallback;
        }
        try {
            return Charset.forName(name.get());
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return fallback;
        }
    }

    /**
     * The declared charset, or UTF-8 when none is declared.
     *
     * @throws IllegalArgumentException if the declared charset is unknown to this JVM
     */
    static Charset requireCharsetOrUtf8(String contentType) {
        Optional<String> name = charsetName(contentType);
        if (!name.isPresent()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(name.get());
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new IllegalArgumentException(
                    "unsupported charset '" + name.get() + "' in content type '" + contentType + "'", e);
        }
    }
}
