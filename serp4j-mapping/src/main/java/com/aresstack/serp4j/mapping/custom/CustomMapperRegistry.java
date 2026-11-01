package com.aresstack.serp4j.mapping.custom;

import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.mapping.MappingDefinition;
import com.aresstack.serp4j.mapping.SectionDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The custom mappers of <strong>exactly one</strong> {@link MappingDefinition} (ADR-019 §3, architecture.md §5.6
 * rule 3). A registry is created for a definition, keyed by that definition's section ids, and the engine refuses a
 * registry that belongs to another definition. There is no global registry and no registration by type code alone:
 * a mapper for DataForSEO's {@code knowledge_graph} can never run on Brave's {@code info_box}, even if both map to
 * {@code knowledge_panel} and both definitions use the same section id.
 *
 * <p>Registration is checked against the definition: the section must exist, item mappers need a section that
 * produces items, feature mappers a section that produces the feature type. Instances are immutable and
 * thread-safe.</p>
 *
 * <pre>{@code
 * CustomMapperRegistry mappers = CustomMapperRegistry.builder(definition)
 *         .itemMapper("serp", new AreaMapper())
 *         .featureMapper("serp", SerpFeatureType.KNOWLEDGE_PANEL, new KnowledgeGraphMapper())
 *         .build();
 * }</pre>
 */
public final class CustomMapperRegistry {

    private final MappingDefinition definition;
    private final Map<String, ItemMapper> itemMappers;
    private final Map<String, Map<SerpFeatureType, FeatureMapper>> featureMappers;
    private final List<MetadataMapper> metadataMappers;

    private CustomMapperRegistry(Builder builder) {
        this.definition = builder.definition;
        this.itemMappers = Collections.unmodifiableMap(new LinkedHashMap<String, ItemMapper>(builder.itemMappers));
        Map<String, Map<SerpFeatureType, FeatureMapper>> features =
                new LinkedHashMap<String, Map<SerpFeatureType, FeatureMapper>>();
        for (Map.Entry<String, Map<SerpFeatureType, FeatureMapper>> e : builder.featureMappers.entrySet()) {
            features.put(e.getKey(), Collections.unmodifiableMap(
                    new LinkedHashMap<SerpFeatureType, FeatureMapper>(e.getValue())));
        }
        this.featureMappers = Collections.unmodifiableMap(features);
        this.metadataMappers = Collections.unmodifiableList(new ArrayList<MetadataMapper>(builder.metadataMappers));
    }

    /** A registry without custom mappers for {@code definition}. */
    public static CustomMapperRegistry empty(MappingDefinition definition) {
        return builder(definition).build();
    }

    public static Builder builder(MappingDefinition definition) {
        return new Builder(Objects.requireNonNull(definition, "definition"));
    }

    /** The definition this registry belongs to. */
    public MappingDefinition getDefinition() {
        return definition;
    }

    /** {@code true} if this registry belongs to {@code other} (equal by value). */
    public boolean belongsTo(MappingDefinition other) {
        return definition.equals(other);
    }

    public Optional<ItemMapper> getItemMapper(String sectionId) {
        return Optional.ofNullable(itemMappers.get(Objects.requireNonNull(sectionId, "sectionId")));
    }

    public Optional<FeatureMapper> getFeatureMapper(String sectionId, SerpFeatureType type) {
        Map<SerpFeatureType, FeatureMapper> bySection = featureMappers.get(Objects.requireNonNull(sectionId, "sectionId"));
        return bySection == null ? Optional.<FeatureMapper>empty()
                : Optional.ofNullable(bySection.get(Objects.requireNonNull(type, "type")));
    }

    /** Metadata mappers in registration order. */
    public List<MetadataMapper> getMetadataMappers() {
        return metadataMappers;
    }

    public boolean isEmpty() {
        return itemMappers.isEmpty() && featureMappers.isEmpty() && metadataMappers.isEmpty();
    }

    @Override
    public String toString() {
        return "CustomMapperRegistry{" + definition + ", items=" + itemMappers.keySet() + ", features="
                + featureMappers.keySet() + ", metadata=" + metadataMappers.size() + '}';
    }

    /** Builder; registering twice for the same key is an error. */
    public static final class Builder {
        private final MappingDefinition definition;
        private final Map<String, ItemMapper> itemMappers = new LinkedHashMap<String, ItemMapper>();
        private final Map<String, Map<SerpFeatureType, FeatureMapper>> featureMappers =
                new LinkedHashMap<String, Map<SerpFeatureType, FeatureMapper>>();
        private final List<MetadataMapper> metadataMappers = new ArrayList<MetadataMapper>();

        private Builder(MappingDefinition definition) {
            this.definition = definition;
        }

        /**
         * @throws IllegalArgumentException if the section does not exist, produces no items or already has an item
         *                                  mapper
         */
        public Builder itemMapper(String sectionId, ItemMapper mapper) {
            Objects.requireNonNull(mapper, "mapper");
            SectionDefinition section = section(sectionId);
            if (!section.producesItems()) {
                throw new IllegalArgumentException("section '" + sectionId + "' of " + definition + " produces no items");
            }
            if (itemMappers.containsKey(sectionId)) {
                throw new IllegalArgumentException("section '" + sectionId + "' already has an item mapper");
            }
            itemMappers.put(sectionId, mapper);
            return this;
        }

        /**
         * @throws IllegalArgumentException if the section does not exist, cannot produce {@code type} or already has
         *                                  a mapper for it
         */
        public Builder featureMapper(String sectionId, SerpFeatureType type, FeatureMapper mapper) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(mapper, "mapper");
            SectionDefinition section = section(sectionId);
            if (!section.producesFeature(type)) {
                throw new IllegalArgumentException("section '" + sectionId + "' of " + definition
                        + " produces no feature of type '" + type.getCode() + "' (types: " + section.getElementTypes() + ")");
            }
            Map<SerpFeatureType, FeatureMapper> bySection = featureMappers.get(sectionId);
            if (bySection == null) {
                bySection = new LinkedHashMap<SerpFeatureType, FeatureMapper>();
                featureMappers.put(sectionId, bySection);
            }
            if (bySection.containsKey(type)) {
                throw new IllegalArgumentException("section '" + sectionId + "' already has a feature mapper for '"
                        + type.getCode() + "'");
            }
            bySection.put(type, mapper);
            return this;
        }

        public Builder metadataMapper(MetadataMapper mapper) {
            metadataMappers.add(Objects.requireNonNull(mapper, "mapper"));
            return this;
        }

        private SectionDefinition section(String sectionId) {
            Objects.requireNonNull(sectionId, "sectionId");
            Optional<SectionDefinition> section = definition.getSection(sectionId);
            if (!section.isPresent()) {
                throw new IllegalArgumentException("definition " + definition + " has no section '" + sectionId + "'");
            }
            return section.get();
        }

        public CustomMapperRegistry build() {
            return new CustomMapperRegistry(this);
        }
    }
}
