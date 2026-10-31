package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.internal.Codes;

/**
 * Kind of a {@link SearchResultItem}. Open value type (ADR-004): the constants cover the core set, providers may
 * introduce further codes.
 */
public final class ResultType {

    public static final ResultType ORGANIC = of("organic");
    /** Paid results; never part of {@link SearchData#getOrganicResults()}. */
    public static final ResultType PAID = of("paid");
    public static final ResultType NEWS = of("news");
    public static final ResultType VIDEO = of("video");
    public static final ResultType IMAGE = of("image");
    public static final ResultType DISCUSSION = of("discussion");
    public static final ResultType LOCAL = of("local");
    public static final ResultType SHOPPING = of("shopping");

    private final String code;

    private ResultType(String code) {
        this.code = code;
    }

    public static ResultType of(String code) {
        return new ResultType(Codes.normalize(code, "result type"));
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ResultType && code.equals(((ResultType) o).code));
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
