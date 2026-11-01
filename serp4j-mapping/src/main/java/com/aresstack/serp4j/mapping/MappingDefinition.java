package com.aresstack.serp4j.mapping;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Declarative, versioned mapping of one provider response format onto {@code SearchData} and metadata (ADR-019,
 * level 1). Usually a JSON resource in the provider module ({@code …/mapping/<name>-<vertical>-v<n>.json}), read
 * with {@code serp4j-json} into a {@code StructuredValue} and validated by {@link MappingDefinitions#from}. The
 * definition knows no domain class: targets are symbolic names. Equality is by value. Immutable.
 *
 * <p>The file format is described in {@code docs/mapping/mapping-definition-format.md}.</p>
 */
public final class MappingDefinition {

    private final String id;
    private final int version;
    private final String namespace;
    private final String description;
    private final List<FieldMapping> metadata;
    private final List<SectionDefinition> sections;
    private final boolean keepUnmappedRoot;

    MappingDefinition(String id, int version, String namespace, String description, List<FieldMapping> metadata,
                      List<SectionDefinition> sections, boolean keepUnmappedRoot) {
        this.id = id;
        this.version = version;
        this.namespace = namespace;
        this.description = description;
        this.metadata = metadata;
        this.sections = sections;
        this.keepUnmappedRoot = keepUnmappedRoot;
    }

    /** Identifier, for example {@code brave-web}. */
    public String getId() {
        return id;
    }

    /** Version of this definition (not of the file format), starting at 1. */
    public int getVersion() {
        return version;
    }

    /** Namespace of extensions and unmapped data, usually the provider code, for example {@code brave}. */
    public String getNamespace() {
        return namespace;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /** Field assignments from the root of the response to metadata targets. */
    public List<FieldMapping> getMetadata() {
        return metadata;
    }

    public List<SectionDefinition> getSections() {
        return sections;
    }

    public Optional<SectionDefinition> getSection(String sectionId) {
        Objects.requireNonNull(sectionId, "sectionId");
        for (SectionDefinition section : sections) {
            if (section.getId().equals(sectionId)) {
                return Optional.of(section);
            }
        }
        return Optional.empty();
    }

    /** {@code true} if the unconsumed rest of the root is kept in the metadata's {@code Extensions.unmapped}. */
    public boolean isKeepUnmappedRoot() {
        return keepUnmappedRoot;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MappingDefinition)) {
            return false;
        }
        MappingDefinition d = (MappingDefinition) o;
        return version == d.version && keepUnmappedRoot == d.keepUnmappedRoot && id.equals(d.id)
                && namespace.equals(d.namespace) && Objects.equals(description, d.description)
                && metadata.equals(d.metadata) && sections.equals(d.sections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, namespace, description, metadata, sections, keepUnmappedRoot);
    }

    /** {@code <id> v<version>}. */
    @Override
    public String toString() {
        return id + " v" + version;
    }
}
