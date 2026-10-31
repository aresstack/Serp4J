package com.aresstack.serp4j.browser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Neutral value tree for page script results and arguments: null, boolean, number, string, list and map (ADR-010:
 * own value model instead of {@code StructuredValue}, no JSON library in the port). Numbers are JavaScript numbers,
 * that is IEEE doubles. Maps keep their insertion order. Immutable.
 */
public final class BrowserValue {

    /** Type of a value. */
    public enum Type {
        /** {@code null} or {@code undefined}. */
        NULL,
        /** A boolean. */
        BOOLEAN,
        /** A number. */
        NUMBER,
        /** A string. */
        STRING,
        /** An ordered list. */
        LIST,
        /** A map with string keys in insertion order. */
        MAP
    }

    private static final BrowserValue NULL = new BrowserValue(Type.NULL, null);
    private static final BrowserValue TRUE = new BrowserValue(Type.BOOLEAN, Boolean.TRUE);
    private static final BrowserValue FALSE = new BrowserValue(Type.BOOLEAN, Boolean.FALSE);

    private final Type type;
    private final Object value;

    private BrowserValue(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    /**
     * @return the null value
     */
    public static BrowserValue nullValue() {
        return NULL;
    }

    /**
     * @param value boolean
     * @return the value
     */
    public static BrowserValue of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /**
     * @param value number
     * @return the value
     */
    public static BrowserValue of(double value) {
        return new BrowserValue(Type.NUMBER, value);
    }

    /**
     * @param value string, or {@code null} for the null value
     * @return the value
     */
    public static BrowserValue of(String value) {
        return value == null ? NULL : new BrowserValue(Type.STRING, value);
    }

    /**
     * @param values elements
     * @return a list value
     */
    public static BrowserValue list(List<BrowserValue> values) {
        List<BrowserValue> copy = new ArrayList<BrowserValue>();
        for (BrowserValue element : Args.notNull(values, "values")) {
            copy.add(element == null ? NULL : element);
        }
        return new BrowserValue(Type.LIST, Collections.unmodifiableList(copy));
    }

    /**
     * @param values entries in iteration order
     * @return a map value
     */
    public static BrowserValue map(Map<String, BrowserValue> values) {
        Map<String, BrowserValue> copy = new LinkedHashMap<String, BrowserValue>();
        for (Map.Entry<String, BrowserValue> entry : Args.notNull(values, "values").entrySet()) {
            copy.put(Args.notNull(entry.getKey(), "key"), entry.getValue() == null ? NULL : entry.getValue());
        }
        return new BrowserValue(Type.MAP, Collections.unmodifiableMap(copy));
    }

    /**
     * Converts plain Java values as delivered by browser drivers: {@code null}, {@link Boolean}, {@link Number},
     * {@link CharSequence}, {@link Character}, {@link List} (also other {@link Iterable}s and arrays of objects),
     * {@link Map} with keys converted by {@code toString()}, and {@code BrowserValue} itself.
     *
     * @param javaValue value
     * @return the converted value
     * @throws IllegalArgumentException if a value cannot be represented
     */
    public static BrowserValue fromJava(Object javaValue) {
        return fromJava(javaValue, 0);
    }

    private static BrowserValue fromJava(Object javaValue, int depth) {
        if (depth > 256) {
            throw new IllegalArgumentException("value is nested too deeply");
        }
        if (javaValue == null) {
            return NULL;
        }
        if (javaValue instanceof BrowserValue) {
            return (BrowserValue) javaValue;
        }
        if (javaValue instanceof Boolean) {
            return of(((Boolean) javaValue).booleanValue());
        }
        if (javaValue instanceof Number) {
            return of(((Number) javaValue).doubleValue());
        }
        if (javaValue instanceof CharSequence || javaValue instanceof Character) {
            return of(javaValue.toString());
        }
        if (javaValue instanceof Map) {
            Map<String, BrowserValue> map = new LinkedHashMap<String, BrowserValue>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) javaValue).entrySet()) {
                map.put(String.valueOf(entry.getKey()), fromJava(entry.getValue(), depth + 1));
            }
            return map(map);
        }
        if (javaValue instanceof Iterable) {
            List<BrowserValue> list = new ArrayList<BrowserValue>();
            for (Object element : (Iterable<?>) javaValue) {
                list.add(fromJava(element, depth + 1));
            }
            return list(list);
        }
        if (javaValue instanceof Object[]) {
            List<BrowserValue> list = new ArrayList<BrowserValue>();
            for (Object element : (Object[]) javaValue) {
                list.add(fromJava(element, depth + 1));
            }
            return list(list);
        }
        throw new IllegalArgumentException("unsupported value type: " + javaValue.getClass().getName());
    }

    /**
     * Converts back to plain Java values: {@code null}, {@link Boolean}, {@link Double}, {@link String},
     * unmodifiable {@link List} and {@link Map}.
     *
     * @return the Java value
     */
    public Object toJava() {
        switch (type) {
            case LIST:
                List<Object> list = new ArrayList<Object>();
                for (BrowserValue element : asList()) {
                    list.add(element.toJava());
                }
                return Collections.unmodifiableList(list);
            case MAP:
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                for (Map.Entry<String, BrowserValue> entry : asMap().entrySet()) {
                    map.put(entry.getKey(), entry.getValue().toJava());
                }
                return Collections.unmodifiableMap(map);
            default:
                return value;
        }
    }

    /**
     * @return the type
     */
    public Type getType() {
        return type;
    }

    /**
     * @return whether this is the null value
     */
    public boolean isNull() {
        return type == Type.NULL;
    }

    /**
     * @return the string
     * @throws IllegalStateException if this is not a string
     */
    public String asString() {
        return (String) expect(Type.STRING);
    }

    /**
     * @return the boolean
     * @throws IllegalStateException if this is not a boolean
     */
    public boolean asBoolean() {
        return (Boolean) expect(Type.BOOLEAN);
    }

    /**
     * @return the number
     * @throws IllegalStateException if this is not a number
     */
    public double asDouble() {
        return (Double) expect(Type.NUMBER);
    }

    /**
     * @return the number as long
     * @throws IllegalStateException if this is not an integral number within the {@code long} range
     */
    public long asLong() {
        double number = asDouble();
        if (number != Math.rint(number) || Double.isInfinite(number) || Math.abs(number) > 9.007199254740992E15) {
            throw new IllegalStateException("not an integral number: " + number);
        }
        return (long) number;
    }

    /**
     * @return the elements (unmodifiable)
     * @throws IllegalStateException if this is not a list
     */
    @SuppressWarnings("unchecked")
    public List<BrowserValue> asList() {
        return (List<BrowserValue>) expect(Type.LIST);
    }

    /**
     * @return the entries (unmodifiable, insertion order)
     * @throws IllegalStateException if this is not a map
     */
    @SuppressWarnings("unchecked")
    public Map<String, BrowserValue> asMap() {
        return (Map<String, BrowserValue>) expect(Type.MAP);
    }

    /**
     * Navigates the tree: a {@link String} segment selects a map entry, an {@link Integer} segment a list element.
     *
     * @param segments path segments
     * @return the value at the path; empty if a segment does not exist or does not fit the type
     */
    public Optional<BrowserValue> path(Object... segments) {
        BrowserValue current = this;
        for (Object segment : Args.notNull(segments, "segments")) {
            if (segment instanceof String && current.type == Type.MAP) {
                current = current.asMap().get(segment);
            } else if (segment instanceof Integer && current.type == Type.LIST) {
                int index = (Integer) segment;
                List<BrowserValue> list = current.asList();
                current = index >= 0 && index < list.size() ? list.get(index) : null;
            } else {
                return Optional.empty();
            }
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    /**
     * @param key map key
     * @return the string at the key; empty if absent, null or not a string
     */
    public Optional<String> getString(String key) {
        Optional<BrowserValue> entry = path(key);
        return entry.isPresent() && entry.get().type == Type.STRING ? Optional.of(entry.get().asString())
                : Optional.<String>empty();
    }

    private Object expect(Type expected) {
        if (type != expected) {
            throw new IllegalStateException("expected " + expected + " but was " + type);
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BrowserValue)) {
            return false;
        }
        BrowserValue that = (BrowserValue) o;
        if (type != that.type) {
            return false;
        }
        if (type == Type.NUMBER) {
            return Double.compare((Double) value, (Double) that.value) == 0;
        }
        return value == null ? that.value == null : value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + (value == null ? 0 : value.hashCode());
    }

    @Override
    public String toString() {
        switch (type) {
            case NULL:
                return "null";
            case STRING:
                return '"' + ((String) value).replace("\\", "\\\\").replace("\"", "\\\"") + '"';
            case NUMBER:
                double number = (Double) value;
                return number == Math.rint(number) && !Double.isInfinite(number) && Math.abs(number) < 1e15
                        ? Long.toString((long) number) : Double.toString(number);
            default:
                return String.valueOf(value);
        }
    }
}
