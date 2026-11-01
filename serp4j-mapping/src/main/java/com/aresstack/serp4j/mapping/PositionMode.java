package com.aresstack.serp4j.mapping;

/** How a section derives the {@code SerpPosition} of its elements. */
public enum PositionMode {
    /**
     * {@code rankInGroup} is the 1-based position among the elements of the same type in the section's list (in
     * source order, including elements that are skipped later); position fields may add or override values.
     */
    INDEX,
    /** Only the position fields ({@code rankInGroup}, {@code rankAbsolute}, {@code page}, {@code area}) are used. */
    FIELDS,
    /** No position; position fields are not allowed. */
    NONE
}
