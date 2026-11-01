package com.aresstack.serp4j.mapping.custom;

import com.aresstack.serp4j.mapping.MappedMetadata;
import com.aresstack.serp4j.mapping.MappingContext;
import com.aresstack.serp4j.mapping.SourceElement;

/**
 * Programmable metadata mapping (ADR-019 §3, level 2) for values that are not a single field path, for example
 * cost plus time into usage. Runs after the declarative metadata fields on the root of the response and may
 * overwrite them. If it throws, the engine records a warning and keeps the declarative values.
 */
@FunctionalInterface
public interface MetadataMapper {

    void map(SourceElement root, MappedMetadata.Builder metadata, MappingContext context);
}
