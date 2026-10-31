package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.model.ext.Extensions;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.List;
import java.util.Optional;

/**
 * Common base of everything in {@link SearchData}: result items and SERP features. Every element has a
 * response-local {@link ElementId}, an optional position, a list of origins for merged responses (ADR-008, empty
 * for single-provider responses) and {@link Extensions}.
 */
public interface SearchElement {

    ElementId getId();

    Optional<SerpPosition> getPosition();

    /** Origins of this element; empty for single-provider responses, one entry per source after a merge. */
    List<ResultProvenance> getProvenance();

    Extensions getExtensions();
}
