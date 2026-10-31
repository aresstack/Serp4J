package com.aresstack.serp4j.core.model;

import com.aresstack.serp4j.core.internal.Preconditions;

/**
 * Offset-based paging of a request: {@code offset >= 0}, {@code limit >= 1}; defaults {@code 0 / 10}.
 * Providers with page-based APIs (Brave: page index 0–9, up to 20 results) translate the offset themselves and
 * declare their limits in their capabilities.
 */
public final class Paging {

    public static final int DEFAULT_LIMIT = 10;
    public static final Paging DEFAULT = new Paging(0, DEFAULT_LIMIT);

    private final int offset;
    private final int limit;

    private Paging(int offset, int limit) {
        this.offset = offset;
        this.limit = limit;
    }

    public static Paging of(int offset, int limit) {
        return new Paging(Preconditions.requireNonNegative(offset, "offset"),
                Preconditions.requirePositive(limit, "limit"));
    }

    /** First page with {@code limit} results. */
    public static Paging firstPage(int limit) {
        return of(0, limit);
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public Paging withOffset(int newOffset) {
        return of(newOffset, limit);
    }

    public Paging withLimit(int newLimit) {
        return of(offset, newLimit);
    }

    /** The paging that follows this one ({@code offset + limit}). */
    public Paging next() {
        return of(offset + limit, limit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Paging)) {
            return false;
        }
        Paging that = (Paging) o;
        return offset == that.offset && limit == that.limit;
    }

    @Override
    public int hashCode() {
        return 31 * offset + limit;
    }

    @Override
    public String toString() {
        return "Paging{offset=" + offset + ", limit=" + limit + '}';
    }
}
