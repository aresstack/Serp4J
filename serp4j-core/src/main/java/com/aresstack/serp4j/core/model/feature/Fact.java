package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A key/value fact of a knowledge panel; each value may link somewhere. */
public final class Fact {

    private final String key;
    private final List<Value> values;

    private Fact(String key, List<Value> values) {
        this.key = key;
        this.values = values;
    }

    public static Builder builder(String key) {
        return new Builder(key);
    }

    public static Fact of(String key, String value) {
        return builder(key).value(value).build();
    }

    /** Returns a builder pre-filled with this instance's values. */
    public Builder toBuilder() {
        Builder builder = new Builder(key);
        builder.values.addAll(values);
        return builder;
    }

    public String getKey() {
        return key;
    }

    /** Never empty. */
    public List<Value> getValues() {
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Fact)) {
            return false;
        }
        Fact that = (Fact) o;
        return key.equals(that.key) && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, values);
    }

    @Override
    public String toString() {
        return "Fact{" + key + "=" + values + '}';
    }

    /** One value of a fact with an optional link. */
    public static final class Value {
        private final String text;
        private final String url;

        private Value(String text, String url) {
            this.text = text;
            this.url = url;
        }

        public static Value of(String text) {
            return new Value(Preconditions.requireText(text, "text"), null);
        }

        public static Value of(String text, String url) {
            return new Value(Preconditions.requireText(text, "text"), Preconditions.trimToNull(url));
        }

        public String getText() {
            return text;
        }

        public Optional<String> getUrl() {
            return Optional.ofNullable(url);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Value)) {
                return false;
            }
            Value that = (Value) o;
            return text.equals(that.text) && Objects.equals(url, that.url);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, url);
        }

        @Override
        public String toString() {
            return url == null ? text : text + " (" + url + ")";
        }
    }

    /** Builder for {@link Fact}; at least one value is required. */
    public static final class Builder {
        private final String key;
        private final List<Value> values = new ArrayList<Value>();

        private Builder(String key) {
            this.key = Preconditions.requireText(key, "key");
        }

        public Builder value(String text) {
            return value(Value.of(text));
        }

        public Builder value(String text, String url) {
            return value(Value.of(text, url));
        }

        public Builder value(Value value) {
            values.add(Preconditions.requireNonNull(value, "value"));
            return this;
        }

        /** @throws IllegalStateException if no value was added */
        public Fact build() {
            if (values.isEmpty()) {
                throw new IllegalStateException("fact '" + key + "' needs at least one value");
            }
            return new Fact(key, Preconditions.immutableList(values, "values"));
        }
    }
}
