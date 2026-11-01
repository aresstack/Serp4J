package com.aresstack.serp4j.json.internal;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.json.Serp4jJsonException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Read view on one JSON object of the model format. Unknown members are ignored (ADR-013); a member with JSON
 * {@code null} counts as absent. Every error names the JSON path of the offending member.
 */
final class In {

    private final StructuredValue value;
    private final String path;

    private In(StructuredValue value, String path) {
        this.value = value;
        this.path = path;
    }

    static In root(StructuredValue value) {
        return object(value, "$");
    }

    private static In object(StructuredValue value, String path) {
        if (!value.isObject()) {
            throw new Serp4jJsonException(path + ": expected a JSON object but found " + describe(value));
        }
        return new In(value, path);
    }

    String path() {
        return path;
    }

    Serp4jJsonException fail(String member, String message) {
        return new Serp4jJsonException(child(member) + ": " + message);
    }

    private String child(String member) {
        return path + "." + member;
    }

    boolean has(String name) {
        StructuredValue member = value.getMembers().get(name);
        return member != null && !member.isNull();
    }

    StructuredValue raw(String name) {
        StructuredValue member = value.getMembers().get(name);
        return member == null || member.isNull() ? null : member;
    }

    private StructuredValue required(String name) {
        StructuredValue member = raw(name);
        if (member == null) {
            throw new Serp4jJsonException(child(name) + ": required member is missing");
        }
        return member;
    }

    In obj(String name) {
        return object(required(name), child(name));
    }

    In optObj(String name) {
        StructuredValue member = raw(name);
        return member == null ? null : object(member, child(name));
    }

    String text(String name) {
        return asText(required(name), child(name));
    }

    String optText(String name) {
        StructuredValue member = raw(name);
        return member == null ? null : asText(member, child(name));
    }

    private static String asText(StructuredValue v, String path) {
        if (!v.isString()) {
            throw new Serp4jJsonException(path + ": expected a string but found " + describe(v));
        }
        return v.asText().get();
    }

    BigDecimal decimal(String name) {
        return asNumber(required(name), child(name));
    }

    BigDecimal optDecimal(String name) {
        StructuredValue member = raw(name);
        return member == null ? null : asNumber(member, child(name));
    }

    private static BigDecimal asNumber(StructuredValue v, String path) {
        if (!v.isNumber()) {
            throw new Serp4jJsonException(path + ": expected a number but found " + describe(v));
        }
        return v.asNumber().get();
    }

    int integer(String name) {
        return toInt(decimal(name), child(name));
    }

    Integer optInteger(String name) {
        BigDecimal number = optDecimal(name);
        return number == null ? null : toInt(number, child(name));
    }

    Long optLong(String name) {
        BigDecimal number = optDecimal(name);
        if (number == null) {
            return null;
        }
        try {
            return number.longValueExact();
        } catch (ArithmeticException e) {
            throw new Serp4jJsonException(child(name) + ": expected a whole number in long range but found " + number);
        }
    }

    double dbl(String name) {
        return decimal(name).doubleValue();
    }

    Double optDouble(String name) {
        BigDecimal number = optDecimal(name);
        return number == null ? null : number.doubleValue();
    }

    private static int toInt(BigDecimal number, String path) {
        try {
            return number.intValueExact();
        } catch (ArithmeticException e) {
            throw new Serp4jJsonException(path + ": expected a whole number in int range but found " + number);
        }
    }

    boolean bool(String name) {
        StructuredValue member = required(name);
        if (!member.isBoolean()) {
            throw new Serp4jJsonException(child(name) + ": expected a boolean but found " + describe(member));
        }
        return member.asBoolean().get();
    }

    Boolean optBool(String name) {
        return has(name) ? bool(name) : null;
    }

    Instant instant(String name) {
        return parseInstant(text(name), name);
    }

    Instant optInstant(String name) {
        String text = optText(name);
        return text == null ? null : parseInstant(text, name);
    }

    private Instant parseInstant(String text, String name) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw new Serp4jJsonException(child(name) + ": expected an ISO-8601 instant but found '" + text + "'", e);
        }
    }

    Duration optDuration(String name) {
        String text = optText(name);
        if (text == null) {
            return null;
        }
        try {
            return Duration.parse(text);
        } catch (DateTimeParseException e) {
            throw new Serp4jJsonException(child(name) + ": expected an ISO-8601 duration but found '" + text + "'", e);
        }
    }

    <E extends Enum<E>> E enumValue(String name, Class<E> type) {
        return toEnum(text(name), name, type);
    }

    <E extends Enum<E>> E optEnum(String name, Class<E> type) {
        String text = optText(name);
        return text == null ? null : toEnum(text, name, type);
    }

    private <E extends Enum<E>> E toEnum(String text, String name, Class<E> type) {
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException e) {
            throw new Serp4jJsonException(child(name) + ": unknown " + type.getSimpleName() + " '" + text + "'", e);
        }
    }

    /** Elements of an array member as objects; an absent member is an empty list. */
    List<In> objects(String name) {
        List<StructuredValue> elements = array(name);
        List<In> result = new ArrayList<In>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            result.add(object(elements.get(i), child(name) + "[" + i + "]"));
        }
        return result;
    }

    /** Elements of an array member as strings; an absent member is an empty list. */
    List<String> texts(String name) {
        List<StructuredValue> elements = array(name);
        List<String> result = new ArrayList<String>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            result.add(asText(elements.get(i), child(name) + "[" + i + "]"));
        }
        return result;
    }

    /** Elements of an array member as ints; an absent member is an empty list. */
    List<Integer> integers(String name) {
        List<StructuredValue> elements = array(name);
        List<Integer> result = new ArrayList<Integer>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            String elementPath = child(name) + "[" + i + "]";
            result.add(toInt(asNumber(elements.get(i), elementPath), elementPath));
        }
        return result;
    }

    private List<StructuredValue> array(String name) {
        StructuredValue member = raw(name);
        if (member == null) {
            return Collections.emptyList();
        }
        if (!member.isArray()) {
            throw new Serp4jJsonException(child(name) + ": expected an array but found " + describe(member));
        }
        return member.getElements();
    }

    /** Members of an object member; an absent member is an empty map. */
    Map<String, StructuredValue> members(String name) {
        StructuredValue member = raw(name);
        if (member == null) {
            return Collections.emptyMap();
        }
        return object(member, child(name)).value.getMembers();
    }

    static String describe(StructuredValue v) {
        return v.getKind().name().toLowerCase(java.util.Locale.ROOT);
    }
}
