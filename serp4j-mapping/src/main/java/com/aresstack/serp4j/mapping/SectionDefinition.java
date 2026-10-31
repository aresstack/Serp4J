package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.mapping.path.TreePath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One section of a {@link MappingDefinition}: a path that yields elements, how their type is determined, and the
 * field assignments (ADR-019 §2). Created only by {@link MappingDefinitions}. Immutable.
 */
public final class SectionDefinition {

    private final String id;
    private final SectionKind kind;
    private final TreePath path;
    private final ElementType defaultType;
    private final TreePath discriminator;
    private final Map<String, ElementType> discriminatorMap;
    private final Set<String> skipWhen;
    private final PositionMode position;
    private final List<FieldMapping> fields;
    private final Map<String, List<FieldMapping>> fieldsByType;
    private final boolean keepUnmapped;

    SectionDefinition(String id, SectionKind kind, TreePath path, ElementType defaultType, TreePath discriminator,
                      Map<String, ElementType> discriminatorMap, Set<String> skipWhen, PositionMode position,
                      List<FieldMapping> fields, Map<String, List<FieldMapping>> fieldsByType, boolean keepUnmapped) {
        this.id = id;
        this.kind = kind;
        this.path = path;
        this.defaultType = defaultType;
        this.discriminator = discriminator;
        this.discriminatorMap = discriminatorMap;
        this.skipWhen = skipWhen;
        this.position = position;
        this.fields = fields;
        this.fieldsByType = fieldsByType;
        this.keepUnmapped = keepUnmapped;
    }

    /** Identifier, unique within the definition; custom mappers are registered by it. */
    public String getId() {
        return id;
    }

    public SectionKind getKind() {
        return kind;
    }

    /** Path of the elements, relative to the root of the response. */
    public TreePath getPath() {
        return path;
    }

    /** Type of elements without (or with an absent) discriminator value. */
    public Optional<ElementType> getDefaultType() {
        return Optional.ofNullable(defaultType);
    }

    /** Path of the discriminator inside each element. */
    public Optional<TreePath> getDiscriminator() {
        return Optional.ofNullable(discriminator);
    }

    /** Discriminator value → element type. Values not listed (and not in {@link #getSkipWhen()}) are unknown. */
    public Map<String, ElementType> getDiscriminatorMap() {
        return discriminatorMap;
    }

    /** Discriminator values whose elements are deliberately not mapped; they stay in the root's unmapped rest. */
    public Set<String> getSkipWhen() {
        return skipWhen;
    }

    public PositionMode getPosition() {
        return position;
    }

    /** Fields applied to every element type of the section (where the target is supported by the type). */
    public List<FieldMapping> getFields() {
        return fields;
    }

    /** Type-specific fields by type code; they win over {@link #getFields()} for the same target. */
    public Map<String, List<FieldMapping>> getFieldsByType() {
        return fieldsByType;
    }

    /** {@code true} if the unconsumed rest of each element is kept in {@code Extensions.unmapped}. */
    public boolean isKeepUnmapped() {
        return keepUnmapped;
    }

    /** Every type this section can produce (default type and all discriminator targets). */
    public Set<ElementType> getElementTypes() {
        Set<ElementType> types = new LinkedHashSet<ElementType>();
        if (defaultType != null) {
            types.add(defaultType);
        }
        types.addAll(discriminatorMap.values());
        return Collections.unmodifiableSet(types);
    }

    /** {@code true} if the section can produce items. */
    public boolean producesItems() {
        for (ElementType type : getElementTypes()) {
            if (type.isItem()) {
                return true;
            }
        }
        return false;
    }

    /** {@code true} if the section can produce a feature of {@code type} from a known discriminator value or default. */
    public boolean producesFeature(SerpFeatureType type) {
        return getElementTypes().contains(ElementType.feature(type));
    }

    /** Common fields plus the type-specific fields of {@code type} (type-specific win for the same target). */
    public List<FieldMapping> fieldsFor(ElementType type) {
        List<FieldMapping> specific = fieldsByType.get(type.getCode());
        if (specific == null || specific.isEmpty()) {
            return fields;
        }
        Map<String, FieldMapping> merged = new LinkedHashMap<String, FieldMapping>();
        for (FieldMapping f : fields) {
            merged.put(f.getTarget(), f);
        }
        for (FieldMapping f : specific) {
            merged.put(f.getTarget(), f);
        }
        return Collections.unmodifiableList(new ArrayList<FieldMapping>(merged.values()));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SectionDefinition)) {
            return false;
        }
        SectionDefinition s = (SectionDefinition) o;
        return keepUnmapped == s.keepUnmapped && id.equals(s.id) && kind == s.kind && path.equals(s.path)
                && Objects.equals(defaultType, s.defaultType) && Objects.equals(discriminator, s.discriminator)
                && discriminatorMap.equals(s.discriminatorMap) && skipWhen.equals(s.skipWhen)
                && position == s.position && fields.equals(s.fields) && fieldsByType.equals(s.fieldsByType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, kind, path, defaultType, discriminator, discriminatorMap, skipWhen, position, fields,
                fieldsByType, keepUnmapped);
    }

    @Override
    public String toString() {
        return "Section{" + id + ", " + kind + ", " + path + '}';
    }
}
