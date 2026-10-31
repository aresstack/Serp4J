package com.aresstack.serp4j.core.internal;

import java.util.regex.Pattern;

/**
 * Normalisation of the string codes behind the open value types (ADR-004).
 *
 * <p><strong>Internal API.</strong></p>
 */
public final class Codes {

    /** Allowed code format after lower-casing: {@code [a-z0-9][a-z0-9._-]*}. */
    public static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    private Codes() {
    }

    /**
     * Trims and lower-cases {@code raw} and checks it against {@link #CODE_PATTERN}.
     *
     * @throws NullPointerException     if {@code raw} is {@code null}
     * @throws IllegalArgumentException if the normalised code does not match the pattern
     */
    public static String normalize(String raw, String name) {
        String code = Preconditions.requireText(raw, name).toLowerCase(java.util.Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    name + " must match " + CODE_PATTERN.pattern() + " but was '" + raw + "'");
        }
        return code;
    }
}
