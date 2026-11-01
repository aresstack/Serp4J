package com.aresstack.serp4j.mapping.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Concrete location of a value inside a tree: a sequence of member names ({@link String}) and array indices
 * ({@link Integer}), without wildcards. Used to track which parts of a provider response a mapping consumed.
 * Immutable.
 */
public final class PathLocation {

    private static final PathLocation ROOT = new PathLocation(Collections.emptyList());

    private final List<Object> segments;

    private PathLocation(List<Object> segments) {
        this.segments = segments;
    }

    /** The location of the tree itself. */
    public static PathLocation root() {
        return ROOT;
    }

    /** Location of the member {@code name} below this location. */
    public PathLocation child(String name) {
        return append(name);
    }

    /** Location of the array element {@code index} below this location. */
    public PathLocation child(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        return append(Integer.valueOf(index));
    }

    /** Appends all segments of {@code relative} to this location. */
    public PathLocation resolve(PathLocation relative) {
        if (relative.segments.isEmpty()) {
            return this;
        }
        if (segments.isEmpty()) {
            return relative;
        }
        List<Object> all = new ArrayList<Object>(segments.size() + relative.segments.size());
        all.addAll(segments);
        all.addAll(relative.segments);
        return new PathLocation(Collections.unmodifiableList(all));
    }

    private PathLocation append(Object segment) {
        List<Object> all = new ArrayList<Object>(segments.size() + 1);
        all.addAll(segments);
        all.add(segment);
        return new PathLocation(Collections.unmodifiableList(all));
    }

    /** Segments from the root: {@link String} for member names, {@link Integer} for array indices. */
    public List<Object> getSegments() {
        return segments;
    }

    public boolean isRoot() {
        return segments.isEmpty();
    }

    /** {@code true} if this location equals {@code other} or lies above it. */
    public boolean isPrefixOf(PathLocation other) {
        if (other.segments.size() < segments.size()) {
            return false;
        }
        return other.segments.subList(0, segments.size()).equals(segments);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof PathLocation && segments.equals(((PathLocation) o).segments));
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    /** Renders the location in path syntax, for example {@code $.items[3].title}. */
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("$");
        for (Object segment : segments) {
            if (segment instanceof Integer) {
                out.append('[').append(segment).append(']');
            } else {
                String name = (String) segment;
                boolean plain = !name.isEmpty();
                for (int i = 0; i < name.length() && plain; i++) {
                    char c = name.charAt(i);
                    plain = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_'
                            || c == '-';
                }
                out.append('.');
                if (plain) {
                    out.append(name);
                } else {
                    out.append('\'').append(name).append('\'');
                }
            }
        }
        return out.toString();
    }
}
