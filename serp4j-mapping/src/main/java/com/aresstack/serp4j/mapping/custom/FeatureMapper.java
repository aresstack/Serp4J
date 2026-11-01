package com.aresstack.serp4j.mapping.custom;

import com.aresstack.serp4j.core.model.feature.SerpFeature;
import com.aresstack.serp4j.mapping.MappingContext;
import com.aresstack.serp4j.mapping.SourceElement;

/**
 * Programmable mapping of one feature type in one section (ADR-019 §3, level 2), for nested structures the
 * declarative level cannot express: knowledge panels with facts, people-also-ask with answer sources, AI answers
 * with source references. It <em>replaces</em> the declarative mapping of the element.
 *
 * <p>The feature must use {@link FeatureSeed#getId()}; position and declared extensions of the seed are added by
 * the engine if the mapper does not set them (use {@link FeatureSeed#applyTo} to set them explicitly). Returning
 * {@code null} hands the element back to the declarative level. If the mapper throws, the engine records a warning
 * and falls back to the declarative level as well. Implementations should be stateless and thread-safe.</p>
 */
@FunctionalInterface
public interface FeatureMapper {

    SerpFeature map(SourceElement element, FeatureSeed seed, MappingContext context);
}
