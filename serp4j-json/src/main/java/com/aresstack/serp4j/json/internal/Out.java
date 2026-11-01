package com.aresstack.serp4j.json.internal;

import com.aresstack.serp4j.core.model.ext.StructuredValue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Builds one JSON object of the model format. Absent values ({@code null}, empty optionals, empty lists) are left
 * out, so a document only contains what the model contains.
 */
final class Out {

    private final StructuredValue.ObjectBuilder builder = StructuredValue.objectBuilder();

    static Out object() {
        return new Out();
    }

    Out put(String name, String value) {
        if (value != null) {
            builder.put(name, value);
        }
        return this;
    }

    Out put(String name, Optional<?> value) {
        if (value.isPresent()) {
            Object v = value.get();
            if (v instanceof String) {
                builder.put(name, (String) v);
            } else if (v instanceof StructuredValue) {
                builder.put(name, (StructuredValue) v);
            } else if (v instanceof BigDecimal) {
                builder.put(name, StructuredValue.of((BigDecimal) v));
            } else if (v instanceof Boolean) {
                builder.put(name, (Boolean) v);
            } else if (v instanceof Instant || v instanceof Duration) {
                builder.put(name, v.toString());
            } else {
                throw new IllegalArgumentException("unsupported optional value type " + v.getClass().getName());
            }
        }
        return this;
    }

    Out put(String name, long value) {
        builder.put(name, value);
        return this;
    }

    Out put(String name, double value) {
        builder.put(name, value);
        return this;
    }

    Out put(String name, boolean value) {
        builder.put(name, value);
        return this;
    }

    Out put(String name, OptionalInt value) {
        if (value.isPresent()) {
            builder.put(name, value.getAsInt());
        }
        return this;
    }

    Out put(String name, OptionalLong value) {
        if (value.isPresent()) {
            builder.put(name, value.getAsLong());
        }
        return this;
    }

    Out put(String name, OptionalDouble value) {
        if (value.isPresent()) {
            builder.put(name, value.getAsDouble());
        }
        return this;
    }

    Out put(String name, Instant value) {
        if (value != null) {
            builder.put(name, value.toString());
        }
        return this;
    }

    Out put(String name, StructuredValue value) {
        if (value != null) {
            builder.put(name, value);
        }
        return this;
    }

    Out put(String name, Out value) {
        if (value != null) {
            builder.put(name, value.build());
        }
        return this;
    }

    /** Puts an array; an empty collection is left out. */
    Out putArray(String name, Collection<StructuredValue> values) {
        if (!values.isEmpty()) {
            builder.put(name, StructuredValue.ofArray(values));
        }
        return this;
    }

    /** Puts an array of strings; an empty collection is left out. */
    Out putTexts(String name, Collection<String> values) {
        List<StructuredValue> elements = new ArrayList<StructuredValue>(values.size());
        for (String value : values) {
            elements.add(StructuredValue.of(value));
        }
        return putArray(name, elements);
    }

    StructuredValue build() {
        return builder.build();
    }
}
