package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.model.data.SearchElement;

/**
 * Any block on the result page that is not a single hit (WP1 D4): knowledge panel, AI answer, featured snippet,
 * people-also-ask, related searches, result groups, answer boxes … Typed implementations exist for the core set,
 * everything else is a {@link GenericSerpFeature}. Use {@link SerpFeatureVisitor} to dispatch over the types
 * without {@code instanceof} chains.
 */
public interface SerpFeature extends SearchElement {

    SerpFeatureType getType();

    <R> R accept(SerpFeatureVisitor<R> visitor);
}
