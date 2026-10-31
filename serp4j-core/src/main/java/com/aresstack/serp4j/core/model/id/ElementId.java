package com.aresstack.serp4j.core.model.id;

import com.aresstack.serp4j.core.internal.Preconditions;

/**
 * Response-local identifier of a {@code SearchElement} (result item or SERP feature).
 *
 * <p>Identifiers are unique within one {@code SearchData} (WP1 D9). They are used for references from
 * features to items (result groups), from AI answers to matching results, from {@code SearchAnalysis} to
 * the analysed elements, and for de-duplication when responses are merged. They are not stable across
 * responses; a merger assigns new identifiers and keeps the original in {@code ResultProvenance}.</p>
 */
public final class ElementId {

    private final String value;

    private ElementId(String value) {
        this.value = value;
    }

    /** Creates an identifier from any non-blank text; surrounding whitespace is removed. */
    public static ElementId of(String value) {
        return new ElementId(Preconditions.requireText(value, "element id"));
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ElementId && value.equals(((ElementId) o).value));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
