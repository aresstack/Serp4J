package com.aresstack.serp4j.core.model.analysis;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/** Numeric metrics derived from a response, keyed by {@link MetricKey}. */
public final class SearchMetrics {

    private static final SearchMetrics EMPTY = new SearchMetrics(Collections.<MetricKey, Double>emptyMap());

    private final Map<MetricKey, Double> values;

    private SearchMetrics(Map<MetricKey, Double> values) {
        this.values = values;
    }

    public static SearchMetrics empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public OptionalDouble get(MetricKey key) {
        Double value = values.get(Preconditions.requireNonNull(key, "key"));
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    public boolean contains(MetricKey key) {
        return values.containsKey(Preconditions.requireNonNull(key, "key"));
    }

    public Set<MetricKey> getKeys() {
        return values.keySet();
    }

    public Map<MetricKey, Double> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Union of both metric sets; on conflicts the values of {@code this} win. */
    public SearchMetrics merge(SearchMetrics other) {
        Preconditions.requireNonNull(other, "other");
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        Map<MetricKey, Double> merged = new LinkedHashMap<MetricKey, Double>(other.values);
        merged.putAll(values);
        return new SearchMetrics(Collections.unmodifiableMap(merged));
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.values.putAll(values);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof SearchMetrics && values.equals(((SearchMetrics) o).values));
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "SearchMetrics" + values;
    }

    /** Builder for {@link SearchMetrics}. */
    public static final class Builder {
        private final Map<MetricKey, Double> values = new LinkedHashMap<MetricKey, Double>();

        private Builder() {
        }

        /** @throws IllegalArgumentException if {@code value} is NaN or infinite */
        public Builder put(MetricKey key, double value) {
            values.put(Preconditions.requireNonNull(key, "key"), Preconditions.requireFinite(value, "value"));
            return this;
        }

        public Builder remove(MetricKey key) {
            values.remove(Preconditions.requireNonNull(key, "key"));
            return this;
        }

        public SearchMetrics build() {
            return values.isEmpty() ? EMPTY
                    : new SearchMetrics(Collections.unmodifiableMap(new LinkedHashMap<MetricKey, Double>(values)));
        }
    }
}
