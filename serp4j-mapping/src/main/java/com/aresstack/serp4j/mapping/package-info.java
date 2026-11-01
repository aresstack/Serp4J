/**
 * Response mapping in two levels (ADR-019, architecture.md §5.6): declarative, versioned
 * {@link com.aresstack.serp4j.mapping.MappingDefinition}s (level 1) plus programmable custom mappers (level 2,
 * {@link com.aresstack.serp4j.mapping.custom}), applied by the {@link com.aresstack.serp4j.mapping.MappingEngine}
 * to a provider response as {@link com.aresstack.serp4j.core.model.ext.StructuredValue}. The module depends only
 * on {@code serp4j-core} and on no JSON library; {@code serp4j-json} turns JSON text into the tree.
 *
 * <p>Public API for provider modules and for users with their own providers: {@code MappingDefinitions},
 * {@code MappingEngine}, {@code MappingResult}, {@code MappedMetadata}, {@code MappingContext},
 * {@code SourceElement}, the mapper interfaces and registry, {@code ValueConverters} and {@code TreePath}.</p>
 */
package com.aresstack.serp4j.mapping;
