package com.aresstack.serp4j.core.model.ext;

import com.aresstack.serp4j.core.internal.Codes;
import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Provider- or module-specific additions to a domain object (ADR-006): typed values addressed by
 * {@link ExtensionKey} plus, per namespace, the <em>unmapped</em> remainder of the provider response as a
 * {@link StructuredValue}, so that nothing a provider delivers is lost silently.
 *
 * <p>Extensions exist on {@code SearchMetadata}, every {@code SearchElement} and {@code SearchAnalysis}.
 * Values must be immutable. Instances are immutable; {@link #with(ExtensionKey, Object)} and
 * {@link #toBuilder()} create modified copies.</p>
 */
public final class Extensions {

    private static final Extensions EMPTY = new Extensions(
            Collections.<ExtensionKey<?>, Object>emptyMap(), Collections.<String, StructuredValue>emptyMap());

    private final Map<ExtensionKey<?>, Object> values;
    private final Map<String, StructuredValue> unmapped;

    private Extensions(Map<ExtensionKey<?>, Object> values, Map<String, StructuredValue> unmapped) {
        this.values = values;
        this.unmapped = unmapped;
    }

    public static Extensions empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The value stored under {@code key}, if any. */
    public <T> Optional<T> get(ExtensionKey<T> key) {
        Object value = values.get(Preconditions.requireNonNull(key, "key"));
        return value == null ? Optional.<T>empty() : Optional.of(key.cast(value));
    }

    public boolean contains(ExtensionKey<?> key) {
        return values.containsKey(Preconditions.requireNonNull(key, "key"));
    }

    public Set<ExtensionKey<?>> getKeys() {
        return values.keySet();
    }

    /** Fields of the provider response under {@code namespace} that no mapper consumed. */
    public Optional<StructuredValue> getUnmapped(String namespace) {
        return Optional.ofNullable(unmapped.get(Codes.normalize(namespace, "namespace")));
    }

    public Set<String> getUnmappedNamespaces() {
        return unmapped.keySet();
    }

    public boolean isEmpty() {
        return values.isEmpty() && unmapped.isEmpty();
    }

    /** Returns a copy with {@code value} stored under {@code key} (replacing an existing value). */
    public <T> Extensions with(ExtensionKey<T> key, T value) {
        return toBuilder().put(key, value).build();
    }

    /** Returns a copy with the unmapped remainder for {@code namespace} replaced. */
    public Extensions withUnmapped(String namespace, StructuredValue remainder) {
        return toBuilder().unmapped(namespace, remainder).build();
    }

    /**
     * Combines two extension sets. Keys and namespaces present in {@code this} win; entries only present in
     * {@code other} are added.
     */
    public Extensions merge(Extensions other) {
        Preconditions.requireNonNull(other, "other");
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        Builder builder = other.toBuilder();
        builder.values.putAll(values);
        builder.unmapped.putAll(unmapped);
        return builder.build();
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.values.putAll(values);
        builder.unmapped.putAll(unmapped);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Extensions)) {
            return false;
        }
        Extensions that = (Extensions) o;
        return values.equals(that.values) && unmapped.equals(that.unmapped);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, unmapped);
    }

    @Override
    public String toString() {
        return "Extensions{keys=" + values.keySet() + ", unmappedNamespaces=" + unmapped.keySet() + '}';
    }

    /** Builder; insertion order of keys and namespaces is kept. */
    public static final class Builder {
        private final Map<ExtensionKey<?>, Object> values = new LinkedHashMap<ExtensionKey<?>, Object>();
        private final Map<String, StructuredValue> unmapped = new LinkedHashMap<String, StructuredValue>();

        private Builder() {
        }

        /** @throws IllegalArgumentException if {@code value} is not an instance of the key's type */
        public <T> Builder put(ExtensionKey<T> key, T value) {
            Preconditions.requireNonNull(key, "key");
            Preconditions.requireNonNull(value, "value");
            if (!key.getType().isInstance(value)) {
                throw new IllegalArgumentException("value for " + key + " must be a "
                        + key.getType().getName() + " but was " + value.getClass().getName());
            }
            values.put(key, value);
            return this;
        }

        public Builder remove(ExtensionKey<?> key) {
            values.remove(Preconditions.requireNonNull(key, "key"));
            return this;
        }

        public Builder unmapped(String namespace, StructuredValue remainder) {
            unmapped.put(Codes.normalize(namespace, "namespace"), Preconditions.requireNonNull(remainder, "remainder"));
            return this;
        }

        public Extensions build() {
            if (values.isEmpty() && unmapped.isEmpty()) {
                return EMPTY;
            }
            return new Extensions(
                    Collections.unmodifiableMap(new LinkedHashMap<ExtensionKey<?>, Object>(values)),
                    Collections.unmodifiableMap(new LinkedHashMap<String, StructuredValue>(unmapped)));
        }
    }
}
