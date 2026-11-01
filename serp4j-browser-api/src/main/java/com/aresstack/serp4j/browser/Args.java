package com.aresstack.serp4j.browser;

import java.time.Duration;

/** Argument checks shared by the value types of this package. */
final class Args {

    private Args() {
    }

    static <T> T notNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        return value;
    }

    static String notBlank(String value, String name) {
        notNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static Duration positive(Duration value, String name) {
        notNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
        return value;
    }

    static Duration notNegative(Duration value, String name) {
        notNull(value, name);
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
        return value;
    }

    static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
        return value;
    }
}
