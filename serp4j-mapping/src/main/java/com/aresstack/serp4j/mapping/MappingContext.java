package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.ext.ExtensionKey;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.mapping.convert.ValueConverter;
import com.aresstack.serp4j.mapping.convert.ValueConverters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Context of one mapping run, handed to custom mappers: the definition, its namespace, the engine's converters
 * and the warning list (ADR-019 §3). Not thread-safe; a new context is created for every
 * {@link MappingEngine#map} call.
 */
public final class MappingContext {

    private final MappingDefinition definition;
    private final ValueConverters converters;
    private final ProviderId provider;
    private final List<SearchWarning> warnings = new ArrayList<SearchWarning>();

    MappingContext(MappingDefinition definition, ValueConverters converters, ProviderId provider) {
        this.definition = definition;
        this.converters = converters;
        this.provider = provider;
    }

    public MappingDefinition getDefinition() {
        return definition;
    }

    /** Namespace for extensions and unmapped data (the definition's namespace). */
    public String getNamespace() {
        return definition.getNamespace();
    }

    /** The provider the warnings are attributed to, if the engine was configured with one. */
    public Optional<ProviderId> getProvider() {
        return Optional.ofNullable(provider);
    }

    public ValueConverters getConverters() {
        return converters;
    }

    /**
     * Applies the converter {@code name} to {@code value}.
     *
     * @return the converted value, or {@code null} for "no value"
     * @throws IllegalArgumentException if no converter of that name is registered
     * @throws com.aresstack.serp4j.mapping.convert.ValueConversionException if the value cannot be converted
     */
    public Object convert(String name, StructuredValue value) {
        Objects.requireNonNull(value, "value");
        Optional<ValueConverter> converter = converters.get(name);
        if (!converter.isPresent()) {
            throw new IllegalArgumentException("unknown converter '" + name + "'");
        }
        return value.isNull() ? null : converter.get().convert(value);
    }

    /** Creates an extension key in the definition's namespace. */
    public <T> ExtensionKey<T> extensionKey(String name, Class<T> type) {
        return ExtensionKey.of(getNamespace(), name, type);
    }

    /** Records a warning; it ends up in {@link MappingResult#getWarnings()}. */
    public void warn(String code, String message) {
        warn(code, message, null);
    }

    /** Records a warning with structured details (for example the element that could not be mapped). */
    public void warn(String code, String message, StructuredValue details) {
        SearchWarning.Builder builder = SearchWarning.builder(code, message);
        if (details != null) {
            builder.details(details);
        }
        if (provider != null) {
            builder.provider(provider);
        }
        warnings.add(builder.build());
    }

    List<SearchWarning> getWarnings() {
        return Collections.unmodifiableList(new ArrayList<SearchWarning>(warnings));
    }
}
