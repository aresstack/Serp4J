package com.aresstack.serp4j.mapping;

/** What the elements of a {@link SectionDefinition} become. */
public enum SectionKind {
    /** Every element becomes a {@code SearchResultItem}. */
    ITEMS,
    /** Every element becomes a {@code SerpFeature}. */
    FEATURES,
    /**
     * Polymorphic list: the discriminator decides per element ({@code item:<type>} or {@code feature:<type>}),
     * for example DataForSEO's flat {@code items[]} with {@code type}.
     */
    ELEMENTS
}
