package com.aresstack.serp4j.core.port.credential;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Arrays;
import java.util.function.Function;

/**
 * A secret held as {@code char[]} so it can be wiped (WP5). Create a fresh instance per use and {@link #close()}
 * it afterwards; {@link #getChars()} hands out a copy the caller wipes itself, {@link #use(Function)} wipes the copy
 * for the caller. Never appears in {@link #toString()}; equality is constant-time.
 */
public final class SecretValue implements AutoCloseable {

    private final char[] chars;
    private volatile boolean cleared;

    private SecretValue(char[] chars) {
        this.chars = chars;
    }

    /** Copies {@code chars}; the caller keeps ownership of its array and should wipe it. */
    public static SecretValue of(char[] chars) {
        Preconditions.requireNonNull(chars, "chars");
        return new SecretValue(chars.clone());
    }

    /** For configuration sources that only offer strings; the string itself cannot be wiped. */
    public static SecretValue of(String value) {
        return new SecretValue(Preconditions.requireNonNull(value, "value").toCharArray());
    }

    /** A copy of the secret; the caller wipes it after use. */
    public char[] getChars() {
        ensureOpen();
        return chars.clone();
    }

    /** Applies {@code action} to a copy of the secret and wipes the copy afterwards. */
    public <T> T use(Function<char[], T> action) {
        Preconditions.requireNonNull(action, "action");
        char[] copy = getChars();
        try {
            return action.apply(copy);
        } finally {
            Arrays.fill(copy, '\0');
        }
    }

    public int length() {
        ensureOpen();
        return chars.length;
    }

    public boolean isEmpty() {
        return length() == 0;
    }

    public boolean isCleared() {
        return cleared;
    }

    /** Wipes the secret; later reads fail with {@link IllegalStateException}. Idempotent. */
    @Override
    public void close() {
        cleared = true;
        Arrays.fill(chars, '\0');
    }

    private void ensureOpen() {
        if (cleared) {
            throw new IllegalStateException("secret value has been cleared");
        }
    }

    /** Constant-time comparison of two open secrets. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SecretValue)) {
            return false;
        }
        SecretValue that = (SecretValue) o;
        if (cleared || that.cleared) {
            return false;
        }
        int result = chars.length ^ that.chars.length;
        int length = Math.min(chars.length, that.chars.length);
        for (int i = 0; i < length; i++) {
            result |= chars[i] ^ that.chars[i];
        }
        return result == 0;
    }

    @Override
    public int hashCode() {
        return cleared ? 0 : chars.length;
    }

    @Override
    public String toString() {
        return "SecretValue{****}";
    }
}
