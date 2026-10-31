package com.aresstack.serp4j.core.model.analysis;

import com.aresstack.serp4j.core.internal.Codes;

/** Category of a search intent. Open value type (ADR-004) with the classic constants. */
public final class IntentCategory {

    public static final IntentCategory INFORMATIONAL = of("informational");
    public static final IntentCategory NAVIGATIONAL = of("navigational");
    public static final IntentCategory TRANSACTIONAL = of("transactional");
    public static final IntentCategory COMMERCIAL = of("commercial");
    public static final IntentCategory LOCAL = of("local");

    private final String code;

    private IntentCategory(String code) {
        this.code = code;
    }

    public static IntentCategory of(String code) {
        return new IntentCategory(Codes.normalize(code, "intent category"));
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof IntentCategory && code.equals(((IntentCategory) o).code));
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
