package com.aresstack.serp4j.json;

import java.util.OptionalInt;

/**
 * Thrown when a document has no {@code formatVersion}, a non-integer one, or a version this library cannot read
 * (unknown, including versions newer than {@link Serp4jJson#CURRENT_FORMAT_VERSION}). The reader never guesses a
 * version (ADR-013, decision 1).
 */
@SuppressWarnings("serial")
public final class UnsupportedFormatVersionException extends Serp4jJsonException {

    private final Integer formatVersion;

    public UnsupportedFormatVersionException(String message, Integer formatVersion) {
        super(message);
        this.formatVersion = formatVersion;
    }

    /** The version found in the document; empty if it was missing or not an integer. */
    public OptionalInt getFormatVersion() {
        return formatVersion == null ? OptionalInt.empty() : OptionalInt.of(formatVersion);
    }
}
