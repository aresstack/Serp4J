package com.aresstack.serp4j.mapping.convert;

import com.aresstack.serp4j.core.model.ext.StructuredValue;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable registry of named {@link ValueConverter}s. {@link #standard()} contains the converters every mapping
 * definition can use; provider modules and users add their own with {@link #toBuilder()}:
 *
 * <pre>{@code
 * ValueConverters converters = ValueConverters.standard().toBuilder()
 *         .register("serpArea", v -> "right".equals(v.asText().orElse(null)) ? "SIDEBAR" : "MAIN")
 *         .build();
 * }</pre>
 *
 * <p>Standard converters (all accept strings as well as native JSON types where it makes sense):</p>
 * <table>
 *   <caption>Standard converters</caption>
 *   <tr><th>Name</th><th>Result</th><th>Accepts</th></tr>
 *   <tr><td>{@code string}</td><td>String</td><td>string, number (plain notation), boolean</td></tr>
 *   <tr><td>{@code int}</td><td>Integer</td><td>integral number or numeric string ({@code "3"}, {@code "3.0"})</td></tr>
 *   <tr><td>{@code long}</td><td>Long</td><td>like {@code int}</td></tr>
 *   <tr><td>{@code decimal}</td><td>BigDecimal</td><td>number or numeric string</td></tr>
 *   <tr><td>{@code boolean}</td><td>Boolean</td><td>boolean, {@code "true"/"false"}, {@code "1"/"0"}, 1/0</td></tr>
 *   <tr><td>{@code stripHtml}</td><td>String</td><td>string; removes tags, decodes the common entities</td></tr>
 *   <tr><td>{@code host}</td><td>String</td><td>URL string; lower-cased host without user info and port</td></tr>
 *   <tr><td>{@code isoDate}</td><td>Instant</td><td>ISO-8601 instant/offset date-time, {@code yyyy-MM-dd HH:mm:ss XXX},
 *       local date-time or date (interpreted as UTC)</td></tr>
 *   <tr><td>{@code epochSeconds}</td><td>Instant</td><td>number or numeric string</td></tr>
 *   <tr><td>{@code epochMillis}</td><td>Instant</td><td>number or numeric string</td></tr>
 *   <tr><td>{@code raw}</td><td>StructuredValue</td><td>anything; keeps the value unchanged</td></tr>
 * </table>
 */
public final class ValueConverters {

    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final DateTimeFormatter SPACE_OFFSET = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .appendLiteral(' ')
            .appendOffset("+HH:MM", "Z")
            .toFormatter(Locale.ROOT);

    private static final ValueConverters STANDARD = createStandard();

    private final Map<String, ValueConverter> converters;

    private ValueConverters(Map<String, ValueConverter> converters) {
        this.converters = converters;
    }

    /** The standard converters (see class documentation). */
    public static ValueConverters standard() {
        return STANDARD;
    }

    /** An empty builder; most callers want {@code standard().toBuilder()} instead. */
    public static Builder builder() {
        return new Builder(new LinkedHashMap<String, ValueConverter>());
    }

    public Optional<ValueConverter> get(String name) {
        return Optional.ofNullable(converters.get(Objects.requireNonNull(name, "name")));
    }

    public boolean contains(String name) {
        return converters.containsKey(Objects.requireNonNull(name, "name"));
    }

    public Set<String> getNames() {
        return converters.keySet();
    }

    public Builder toBuilder() {
        return new Builder(new LinkedHashMap<String, ValueConverter>(converters));
    }

    @Override
    public String toString() {
        return "ValueConverters" + converters.keySet();
    }

    /** Builder; registering a name twice is an error, so a provider can never silently replace a standard converter. */
    public static final class Builder {
        private final Map<String, ValueConverter> converters;

        private Builder(Map<String, ValueConverter> converters) {
            this.converters = converters;
        }

        /**
         * @throws IllegalArgumentException if {@code name} is not of the form {@code [A-Za-z][A-Za-z0-9_-]*} or
         *                                  already registered
         */
        public Builder register(String name, ValueConverter converter) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(converter, "converter");
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("converter name must match " + NAME.pattern() + " but was '" + name + "'");
            }
            if (converters.containsKey(name)) {
                throw new IllegalArgumentException("converter '" + name + "' is already registered");
            }
            converters.put(name, converter);
            return this;
        }

        public ValueConverters build() {
            return new ValueConverters(Collections.unmodifiableMap(new LinkedHashMap<String, ValueConverter>(converters)));
        }
    }

    // ------------------------------------------------------------------ standard converters

    private static ValueConverters createStandard() {
        return builder()
                .register("string", ValueConverters::toText)
                .register("int", v -> {
                    BigDecimal d = toIntegral(v);
                    if (d == null) {
                        return null;
                    }
                    try {
                        return Integer.valueOf(d.intValueExact());
                    } catch (ArithmeticException e) {
                        throw new ValueConversionException("not an int: " + v, e);
                    }
                })
                .register("long", v -> {
                    BigDecimal d = toIntegral(v);
                    if (d == null) {
                        return null;
                    }
                    try {
                        return Long.valueOf(d.longValueExact());
                    } catch (ArithmeticException e) {
                        throw new ValueConversionException("not a long: " + v, e);
                    }
                })
                .register("decimal", ValueConverters::toDecimal)
                .register("boolean", ValueConverters::toBoolean)
                .register("stripHtml", v -> stripHtml(toText(v)))
                .register("host", v -> host(toText(v)))
                .register("isoDate", v -> isoDate(toText(v)))
                .register("epochSeconds", v -> epoch(v, false))
                .register("epochMillis", v -> epoch(v, true))
                .register("raw", v -> v)
                .build();
    }

    private static String toText(StructuredValue v) {
        switch (v.getKind()) {
            case STRING:
                return v.asText().get();
            case NUMBER:
                return v.asNumber().get().toPlainString();
            case BOOLEAN:
                return v.asBoolean().get().toString();
            default:
                throw new ValueConversionException("expected a string but found " + v.getKind().name().toLowerCase(Locale.ROOT));
        }
    }

    private static BigDecimal toDecimal(StructuredValue v) {
        if (v.isNumber()) {
            return v.asNumber().get();
        }
        if (v.isString()) {
            String s = v.asText().get().trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                throw new ValueConversionException("not a number: '" + s + "'", e);
            }
        }
        throw new ValueConversionException("expected a number but found " + v.getKind().name().toLowerCase(Locale.ROOT));
    }

    private static BigDecimal toIntegral(StructuredValue v) {
        BigDecimal d = toDecimal(v);
        if (d == null) {
            return null;
        }
        if (d.signum() != 0 && d.stripTrailingZeros().scale() > 0) {
            throw new ValueConversionException("not an integral number: " + d.toPlainString());
        }
        return d;
    }

    private static Boolean toBoolean(StructuredValue v) {
        if (v.isBoolean()) {
            return v.asBoolean().get();
        }
        String s = toText(v).trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        if (s.equals("true") || s.equals("1")) {
            return Boolean.TRUE;
        }
        if (s.equals("false") || s.equals("0")) {
            return Boolean.FALSE;
        }
        throw new ValueConversionException("not a boolean: '" + s + "'");
    }

    private static String stripHtml(String s) {
        String text = TAG.matcher(s).replaceAll("");
        text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&#x27;", "'").replace("&apos;", "'").replace("&amp;", "&");
        return text;
    }

    private static String host(String url) {
        String s = url.trim();
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            s = s.substring(scheme + 3);
        } else if (s.startsWith("//")) {
            s = s.substring(2);
        }
        int end = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        s = s.substring(0, end);
        int at = s.lastIndexOf('@');
        if (at >= 0) {
            s = s.substring(at + 1);
        }
        if (s.startsWith("[")) {
            int close = s.indexOf(']');
            s = close > 0 ? s.substring(0, close + 1) : s;
        } else {
            int colon = s.indexOf(':');
            if (colon >= 0) {
                s = s.substring(0, colon);
            }
        }
        if (s.isEmpty()) {
            throw new ValueConversionException("no host in '" + url + "'");
        }
        return s.toLowerCase(Locale.ROOT);
    }

    private static Instant isoDate(String text) {
        String s = text.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeException ignored) {
            // try the next format
        }
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeException ignored) {
            // try the next format
        }
        try {
            return OffsetDateTime.parse(s, SPACE_OFFSET).toInstant();
        } catch (DateTimeException ignored) {
            // try the next format
        }
        try {
            return LocalDateTime.parse(s.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
        } catch (DateTimeException ignored) {
            // try the next format
        }
        try {
            return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeException e) {
            throw new ValueConversionException("not an ISO-8601 date: '" + s + "'", e);
        }
    }

    private static Instant epoch(StructuredValue v, boolean millis) {
        BigDecimal d = toIntegral(v);
        if (d == null) {
            return null;
        }
        try {
            long n = d.longValueExact();
            return millis ? Instant.ofEpochMilli(n) : Instant.ofEpochSecond(n);
        } catch (ArithmeticException | DateTimeException e) {
            throw new ValueConversionException("not an epoch value: " + d.toPlainString(), e);
        }
    }
}
