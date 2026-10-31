package com.aresstack.serp4j.core.model.ext;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * JSON-like, immutable value tree without any JSON library (ADR-006): objects, arrays, strings, numbers,
 * booleans and null. Used for unmapped provider data in {@link Extensions}, for the content of
 * {@code GenericSerpFeature} and as the input of the mapping infrastructure.
 *
 * <p>The class is effectively sealed: all instances are created through the static factories. Numbers are
 * stored as {@link BigDecimal} and compared by numeric value, so {@code 1}, {@code 1.0} and {@code 1E0} are
 * equal.</p>
 */
public abstract class StructuredValue {

    /** The kind of a value, mirroring the JSON data types. */
    public enum Kind {
        OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL
    }

    private static final StructuredValue NULL = new NullValue();
    private static final StructuredValue TRUE = new BooleanValue(true);
    private static final StructuredValue FALSE = new BooleanValue(false);

    StructuredValue() {
    }

    // ---------------------------------------------------------------- factories

    public static StructuredValue nullValue() {
        return NULL;
    }

    public static StructuredValue of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static StructuredValue of(String value) {
        return new StringValue(Preconditions.requireNonNull(value, "value"));
    }

    public static StructuredValue of(long value) {
        return new NumberValue(BigDecimal.valueOf(value));
    }

    /** @throws IllegalArgumentException if {@code value} is NaN or infinite */
    public static StructuredValue of(double value) {
        Preconditions.requireFinite(value, "value");
        return new NumberValue(BigDecimal.valueOf(value));
    }

    public static StructuredValue of(BigDecimal value) {
        return new NumberValue(Preconditions.requireNonNull(value, "value"));
    }

    /** Creates an array value; the list is copied and must not contain {@code null} (use {@link #nullValue()}). */
    public static StructuredValue ofArray(Collection<? extends StructuredValue> elements) {
        return new ArrayValue(Preconditions.immutableList(elements, "elements"));
    }

    /** Creates an object value; the map is copied and keeps its iteration order. */
    public static StructuredValue ofObject(Map<String, ? extends StructuredValue> members) {
        return new ObjectValue(Preconditions.immutableMap(members, "members"));
    }

    public static ObjectBuilder objectBuilder() {
        return new ObjectBuilder();
    }

    public static ArrayBuilder arrayBuilder() {
        return new ArrayBuilder();
    }

    // ---------------------------------------------------------------- accessors

    public abstract Kind getKind();

    public final boolean isObject() {
        return getKind() == Kind.OBJECT;
    }

    public final boolean isArray() {
        return getKind() == Kind.ARRAY;
    }

    public final boolean isString() {
        return getKind() == Kind.STRING;
    }

    public final boolean isNumber() {
        return getKind() == Kind.NUMBER;
    }

    public final boolean isBoolean() {
        return getKind() == Kind.BOOLEAN;
    }

    public final boolean isNull() {
        return getKind() == Kind.NULL;
    }

    /** Members of an object value in their original order; empty for every other kind. */
    public Map<String, StructuredValue> getMembers() {
        return Collections.emptyMap();
    }

    /** Elements of an array value; empty for every other kind. */
    public List<StructuredValue> getElements() {
        return Collections.emptyList();
    }

    /** The member {@code name} of an object value; empty if this is not an object or has no such member. */
    public Optional<StructuredValue> get(String name) {
        return Optional.ofNullable(getMembers().get(Preconditions.requireNonNull(name, "name")));
    }

    /** The element at {@code index} of an array value; empty if this is not an array or the index is out of range. */
    public Optional<StructuredValue> get(int index) {
        List<StructuredValue> elements = getElements();
        if (index < 0 || index >= elements.size()) {
            return Optional.empty();
        }
        return Optional.of(elements.get(index));
    }

    /**
     * Navigates a path of member names. A segment consisting of digits is interpreted as an array index when
     * the current value is an array. Returns empty as soon as a segment cannot be resolved.
     */
    public Optional<StructuredValue> path(String... segments) {
        Preconditions.requireNonNull(segments, "segments");
        StructuredValue current = this;
        for (String segment : segments) {
            Preconditions.requireNonNull(segment, "segment");
            Optional<StructuredValue> next;
            if (current.isArray() && isIndex(segment)) {
                next = current.get(Integer.parseInt(segment));
            } else {
                next = current.get(segment);
            }
            if (!next.isPresent()) {
                return Optional.empty();
            }
            current = next.get();
        }
        return Optional.of(current);
    }

    /** The text of a string value; empty for every other kind (no coercion). */
    public Optional<String> asText() {
        return Optional.empty();
    }

    /** The number of a number value; empty for every other kind (no coercion). */
    public Optional<BigDecimal> asNumber() {
        return Optional.empty();
    }

    /** The number as {@code long} if it is integral and fits; empty otherwise. */
    public OptionalLong asLong() {
        Optional<BigDecimal> number = asNumber();
        if (!number.isPresent()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(number.get().longValueExact());
        } catch (ArithmeticException notIntegral) {
            return OptionalLong.empty();
        }
    }

    /** The value of a boolean value; empty for every other kind (no coercion). */
    public Optional<Boolean> asBoolean() {
        return Optional.empty();
    }

    private static boolean isIndex(String segment) {
        if (segment.isEmpty() || segment.length() > 9) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** Renders the value in JSON syntax (for debugging and tests; not a stable serialisation format). */
    @Override
    public abstract String toString();

    static void appendJsonString(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    // ---------------------------------------------------------------- builders

    /** Builder for object values; keeps insertion order. */
    public static final class ObjectBuilder {
        private final Map<String, StructuredValue> members = new LinkedHashMap<String, StructuredValue>();

        private ObjectBuilder() {
        }

        public ObjectBuilder put(String name, StructuredValue value) {
            members.put(Preconditions.requireNonNull(name, "name"), Preconditions.requireNonNull(value, "value"));
            return this;
        }

        public ObjectBuilder put(String name, String value) {
            return put(name, value == null ? nullValue() : of(value));
        }

        public ObjectBuilder put(String name, long value) {
            return put(name, of(value));
        }

        public ObjectBuilder put(String name, double value) {
            return put(name, of(value));
        }

        public ObjectBuilder put(String name, boolean value) {
            return put(name, of(value));
        }

        public ObjectBuilder putNull(String name) {
            return put(name, nullValue());
        }

        public StructuredValue build() {
            return new ObjectValue(Collections.unmodifiableMap(new LinkedHashMap<String, StructuredValue>(members)));
        }
    }

    /** Builder for array values. */
    public static final class ArrayBuilder {
        private final List<StructuredValue> elements = new ArrayList<StructuredValue>();

        private ArrayBuilder() {
        }

        public ArrayBuilder add(StructuredValue value) {
            elements.add(Preconditions.requireNonNull(value, "value"));
            return this;
        }

        public ArrayBuilder add(String value) {
            return add(value == null ? nullValue() : of(value));
        }

        public ArrayBuilder add(long value) {
            return add(of(value));
        }

        public ArrayBuilder add(double value) {
            return add(of(value));
        }

        public ArrayBuilder add(boolean value) {
            return add(of(value));
        }

        public ArrayBuilder addAll(Collection<? extends StructuredValue> values) {
            for (StructuredValue value : Preconditions.requireNonNull(values, "values")) {
                add(value);
            }
            return this;
        }

        public StructuredValue build() {
            return new ArrayValue(Collections.unmodifiableList(new ArrayList<StructuredValue>(elements)));
        }
    }

    // ---------------------------------------------------------------- implementations

    private static final class ObjectValue extends StructuredValue {
        private final Map<String, StructuredValue> members;

        ObjectValue(Map<String, StructuredValue> members) {
            this.members = members;
        }

        @Override
        public Kind getKind() {
            return Kind.OBJECT;
        }

        @Override
        public Map<String, StructuredValue> getMembers() {
            return members;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof ObjectValue && members.equals(((ObjectValue) o).members));
        }

        @Override
        public int hashCode() {
            return members.hashCode();
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder("{");
            Iterator<Map.Entry<String, StructuredValue>> it = members.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, StructuredValue> entry = it.next();
                appendJsonString(out, entry.getKey());
                out.append(':').append(entry.getValue());
                if (it.hasNext()) {
                    out.append(',');
                }
            }
            return out.append('}').toString();
        }
    }

    private static final class ArrayValue extends StructuredValue {
        private final List<StructuredValue> elements;

        ArrayValue(List<StructuredValue> elements) {
            this.elements = elements;
        }

        @Override
        public Kind getKind() {
            return Kind.ARRAY;
        }

        @Override
        public List<StructuredValue> getElements() {
            return elements;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof ArrayValue && elements.equals(((ArrayValue) o).elements));
        }

        @Override
        public int hashCode() {
            return elements.hashCode();
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(elements.get(i));
            }
            return out.append(']').toString();
        }
    }

    private static final class StringValue extends StructuredValue {
        private final String value;

        StringValue(String value) {
            this.value = value;
        }

        @Override
        public Kind getKind() {
            return Kind.STRING;
        }

        @Override
        public Optional<String> asText() {
            return Optional.of(value);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof StringValue && value.equals(((StringValue) o).value));
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder(value.length() + 2);
            appendJsonString(out, value);
            return out.toString();
        }
    }

    private static final class NumberValue extends StructuredValue {
        private final BigDecimal value;

        NumberValue(BigDecimal value) {
            this.value = value;
        }

        @Override
        public Kind getKind() {
            return Kind.NUMBER;
        }

        @Override
        public Optional<BigDecimal> asNumber() {
            return Optional.of(value);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof NumberValue && value.compareTo(((NumberValue) o).value) == 0);
        }

        @Override
        public int hashCode() {
            return value.stripTrailingZeros().hashCode();
        }

        @Override
        public String toString() {
            return value.toPlainString();
        }
    }

    private static final class BooleanValue extends StructuredValue {
        private final boolean value;

        BooleanValue(boolean value) {
            this.value = value;
        }

        @Override
        public Kind getKind() {
            return Kind.BOOLEAN;
        }

        @Override
        public Optional<Boolean> asBoolean() {
            return Optional.of(value);
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof BooleanValue && value == ((BooleanValue) o).value);
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(value);
        }

        @Override
        public String toString() {
            return Boolean.toString(value);
        }
    }

    private static final class NullValue extends StructuredValue {
        NullValue() {
        }

        @Override
        public Kind getKind() {
            return Kind.NULL;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof NullValue;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "null";
        }
    }
}
