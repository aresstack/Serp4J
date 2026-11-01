package com.aresstack.serp4j.provider.brightdata;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.MappedMetadata;
import com.aresstack.serp4j.mapping.MappingContext;
import com.aresstack.serp4j.mapping.MappingDefinition;
import com.aresstack.serp4j.mapping.MappingDefinitions;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.mapping.SourceElement;
import com.aresstack.serp4j.mapping.custom.CustomMapperRegistry;
import com.aresstack.serp4j.mapping.custom.MetadataMapper;

import java.util.Objects;
import java.util.Optional;

/**
 * The mapping definition {@code brightdata-google-web} (ADR-019) for the parsed SERP inside a Bright Data
 * {@code /request} response, with its own custom mapper registry. {@code rank} becomes the position within the
 * section ({@code rankInGroup}), {@code global_rank} the position on the whole page ({@code rankAbsolute}).
 */
public final class BrightDataMapping {

    /** Classpath resource of the definition, relative to this class. */
    public static final String WEB_DEFINITION = "mapping/brightdata-google-web-v1.json";

    private static final MappingDefinition DEFINITION =
            MappingDefinitions.from(JsonTrees.readResource(BrightDataMapping.class, WEB_DEFINITION));

    private BrightDataMapping() {
    }

    /** The loaded, immutable definition. */
    public static MappingDefinition webDefinition() {
        return DEFINITION;
    }

    /** A new engine for the definition, with a registry built for exactly this definition. */
    public static MappingEngine webEngine(ProviderId provider) {
        Objects.requireNonNull(provider, "provider");
        return MappingEngine.builder(DEFINITION)
                .customMappers(CustomMapperRegistry.builder(DEFINITION)
                        .metadataMapper(new PaginationMapper())
                        .build())
                .provider(provider)
                .build();
    }

    /** Engine without a provider id, e.g. for mapping contract tests. */
    public static MappingEngine webEngine() {
        return webEngine(ProviderId.of("brightdata"));
    }

    /** "Has more" is the presence of {@code pagination.next_page_start} (or {@code next_page}). */
    static final class PaginationMapper implements MetadataMapper {
        @Override
        public void map(SourceElement root, MappedMetadata.Builder metadata, MappingContext context) {
            // Read without consuming: pagination stays in the unmapped root remainder.
            Optional<StructuredValue> pagination = root.getValue().get("pagination");
            if (!pagination.isPresent() || !pagination.get().isObject()) {
                return;
            }
            boolean hasNext = present(pagination.get(), "next_page_start") || present(pagination.get(), "next_page");
            metadata.hasMore(hasNext);
        }

        private static boolean present(StructuredValue object, String name) {
            Optional<StructuredValue> value = object.get(name);
            return value.isPresent() && !value.get().isNull();
        }
    }
}
