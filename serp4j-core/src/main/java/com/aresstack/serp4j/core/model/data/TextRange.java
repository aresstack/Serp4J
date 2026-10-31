package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.internal.Preconditions;

/**
 * Highlighted range within a snippet: {@code start} inclusive, {@code end} exclusive, as {@code char} indexes.
 * Replaces provider markup in the plain-text snippet.
 */
public final class TextRange {

    private final int start;
    private final int end;

    private TextRange(int start, int end) {
        this.start = start;
        this.end = end;
    }

    /** @throws IllegalArgumentException if {@code start < 0} or {@code end <= start} */
    public static TextRange of(int start, int end) {
        Preconditions.requireNonNegative(start, "start");
        if (end <= start) {
            throw new IllegalArgumentException("end must be greater than start but was " + end + " <= " + start);
        }
        return new TextRange(start, end);
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public int length() {
        return end - start;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TextRange)) {
            return false;
        }
        TextRange that = (TextRange) o;
        return start == that.start && end == that.end;
    }

    @Override
    public int hashCode() {
        return 31 * start + end;
    }

    @Override
    public String toString() {
        return "[" + start + "," + end + ")";
    }
}
