package com.aresstack.serp4j.mapping.internal;

import com.aresstack.serp4j.core.model.data.SerpArea;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.mapping.convert.ValueConversionException;
import com.aresstack.serp4j.mapping.convert.ValueConverter;
import com.aresstack.serp4j.mapping.convert.ValueConverters;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * Brings the result of a converter (or a raw tree value) into the Java type of a target kind.
 *
 * <p><strong>Internal API.</strong></p>
 */
public final class Coercion {

    private Coercion() {
    }

    /**
     * @return the value as {@code String}, {@code Integer}, {@code Long}, {@code BigDecimal}, {@code Boolean},
     *         {@code Instant}/{@code String} (dates), {@link SerpArea} or – for extensions – {@code String},
     *         {@code BigDecimal}, {@code Boolean} or {@link StructuredValue}; {@code null} for "no value"
     * @throws ValueConversionException if the value does not fit the kind
     */
    public static Object coerce(Object value, Targets.Kind kind) {
        if (value == null) {
            return null;
        }
        switch (kind) {
            case TEXT:
            case TEXT_LIST:
                return text(value);
            case INT:
                return value instanceof Integer ? value : standard("int", value);
            case LONG:
                return value instanceof Long ? value : standard("long", value);
            case DECIMAL:
                return value instanceof BigDecimal ? value : standard("decimal", value);
            case BOOLEAN:
                return value instanceof Boolean ? value : standard("boolean", value);
            case DATE:
                return value instanceof Instant ? value : text(value);
            case AREA:
                if (value instanceof SerpArea) {
                    return value;
                }
                String area = text(value);
                if (area == null) {
                    return null;
                }
                try {
                    return SerpArea.valueOf(area.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new ValueConversionException("not a page area (main, top, sidebar, bottom): '" + area + "'", e);
                }
            default:
                return extension(value);
        }
    }

    private static String text(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof StructuredValue) {
            return (String) standard("string", value);
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Instant) {
            return value.toString();
        }
        throw new ValueConversionException("expected text but the converter returned " + value.getClass().getName());
    }

    private static Object standard(String converter, Object value) {
        StructuredValue tree = toTree(value);
        ValueConverter c = ValueConverters.standard().get(converter).get();
        return c.convert(tree);
    }

    private static StructuredValue toTree(Object value) {
        if (value instanceof StructuredValue) {
            return (StructuredValue) value;
        }
        if (value instanceof String) {
            return StructuredValue.of((String) value);
        }
        if (value instanceof BigDecimal) {
            return StructuredValue.of((BigDecimal) value);
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return StructuredValue.of(((Number) value).longValue());
        }
        if (value instanceof Number) {
            return StructuredValue.of(new BigDecimal(value.toString()));
        }
        if (value instanceof Boolean) {
            return StructuredValue.of((Boolean) value);
        }
        throw new ValueConversionException("unsupported converter result " + value.getClass().getName());
    }

    private static Object extension(Object value) {
        if (!(value instanceof StructuredValue)) {
            return value;
        }
        StructuredValue v = (StructuredValue) value;
        switch (v.getKind()) {
            case STRING:
                return v.asText().get();
            case NUMBER:
                return v.asNumber().get();
            case BOOLEAN:
                return v.asBoolean().get();
            case NULL:
                return null;
            default:
                return v;
        }
    }

    /** Type under which an extension value is stored: its class, or {@code StructuredValue} for trees. */
    public static Class<?> extensionType(Object value) {
        return value instanceof StructuredValue ? StructuredValue.class : value.getClass();
    }
}
