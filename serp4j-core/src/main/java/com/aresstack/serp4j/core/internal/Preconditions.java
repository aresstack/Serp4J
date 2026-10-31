package com.aresstack.serp4j.core.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Argument checks and defensive copies used by the core value classes.
 *
 * <p><strong>Internal API.</strong> Packages named {@code internal} are not part of the public Serp4J API
 * and may change without notice.</p>
 */
public final class Preconditions {

    private Preconditions() {
    }

    /** Returns {@code value} or throws a {@link NullPointerException} naming the parameter. */
    public static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        return value;
    }

    /** Returns the trimmed text or throws if {@code value} is {@code null} or blank. */
    public static String requireText(String value, String name) {
        requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }

    /** Returns the trimmed text, or {@code null} when {@code value} is {@code null} or blank. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0 but was " + value);
        }
        return value;
    }

    public static long requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0 but was " + value);
        }
        return value;
    }

    public static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0 but was " + value);
        }
        return value;
    }

    public static double requireFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number but was " + value);
        }
        return value;
    }

    public static double requireRange(double value, double min, double max, String name) {
        requireFinite(value, name);
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    name + " must be between " + min + " and " + max + " but was " + value);
        }
        return value;
    }

    /** Unmodifiable copy of {@code items} that rejects {@code null} elements; {@code null} input yields an empty list. */
    public static <T> List<T> immutableList(Collection<? extends T> items, String name) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> copy = new ArrayList<T>(items.size());
        for (T item : items) {
            copy.add(requireNonNull(item, name + " element"));
        }
        return Collections.unmodifiableList(copy);
    }

    /** Unmodifiable, insertion-ordered copy of {@code items} without {@code null} elements. */
    public static <T> Set<T> immutableSet(Collection<? extends T> items, String name) {
        if (items == null || items.isEmpty()) {
            return Collections.emptySet();
        }
        Set<T> copy = new LinkedHashSet<T>(items.size());
        for (T item : items) {
            copy.add(requireNonNull(item, name + " element"));
        }
        return Collections.unmodifiableSet(copy);
    }

    /** Unmodifiable, insertion-ordered copy of {@code map} without {@code null} keys or values. */
    public static <K, V> Map<K, V> immutableMap(Map<? extends K, ? extends V> map, String name) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<K, V> copy = new LinkedHashMap<K, V>(map.size());
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            copy.put(requireNonNull(entry.getKey(), name + " key"),
                    requireNonNull(entry.getValue(), name + " value"));
        }
        return Collections.unmodifiableMap(copy);
    }
}
