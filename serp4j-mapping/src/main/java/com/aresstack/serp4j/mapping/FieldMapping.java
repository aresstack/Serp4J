package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.mapping.path.TreePath;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One declarative field assignment of a {@link MappingDefinition}: the symbolic {@linkplain #getTarget() target}
 * receives the value of the first {@linkplain #getSources() source path} that has a (non-null) value, optionally
 * converted by a named converter; a {@linkplain #getDefault() default} applies if no source has a value. Created
 * only by {@link MappingDefinitions}. Immutable.
 */
public final class FieldMapping {

    private final String target;
    private final List<TreePath> sources;
    private final String converter;
    private final StructuredValue defaultValue;
    private final boolean required;

    FieldMapping(String target, List<TreePath> sources, String converter, StructuredValue defaultValue,
                 boolean required) {
        this.target = target;
        this.sources = sources;
        this.converter = converter;
        this.defaultValue = defaultValue;
        this.required = required;
    }

    /** Symbolic target, for example {@code title}, {@code rankAbsolute} or the extension {@code brave:age}. */
    public String getTarget() {
        return target;
    }

    /** Alternative source paths, relative to the element (or the root for metadata); the first hit wins. */
    public List<TreePath> getSources() {
        return sources;
    }

    /** Name of the converter in the engine's {@code ValueConverters}; empty = default converter of the target. */
    public Optional<String> getConverter() {
        return Optional.ofNullable(converter);
    }

    /** Value used when no source has a value; it is converted like a source value. */
    public Optional<StructuredValue> getDefault() {
        return Optional.ofNullable(defaultValue);
    }

    /** {@code true} if an element without a value for this field is skipped (with a warning). */
    public boolean isRequired() {
        return required;
    }

    /** {@code true} for targets of the form {@code namespace:name}. */
    public boolean isExtension() {
        return target.indexOf(':') > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FieldMapping)) {
            return false;
        }
        FieldMapping that = (FieldMapping) o;
        return required == that.required && target.equals(that.target) && sources.equals(that.sources)
                && Objects.equals(converter, that.converter) && Objects.equals(defaultValue, that.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, sources, converter, defaultValue, required);
    }

    @Override
    public String toString() {
        return target + " <- " + sources + (converter != null ? " | " + converter : "")
                + (defaultValue != null ? " default " + defaultValue : "") + (required ? " required" : "");
    }
}
