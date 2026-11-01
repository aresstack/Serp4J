package com.aresstack.serp4j.mapping.convert;

import com.aresstack.serp4j.core.model.ext.StructuredValue;

/**
 * A named conversion from a value of the provider response to the value assigned to a mapping target
 * (ADR-019 §2). Referenced from a mapping definition by name ({@code "convert": "int"}) and registered in
 * {@link ValueConverters}.
 *
 * <p>The input is never {@code null} and never a JSON {@code null} (the engine treats both as "no value"). The
 * result is one of {@link String}, {@link Integer}, {@link Long}, {@link java.math.BigDecimal}, {@link Boolean},
 * {@link java.time.Instant} or {@link StructuredValue}; {@code null} means "no value". A converter signals a value
 * it cannot convert by throwing {@link ValueConversionException} (any other runtime exception is treated the same
 * way); the engine then records a warning and treats the field as missing – mapping never fails because of a
 * single bad value. Implementations must be stateless and thread-safe.</p>
 */
@FunctionalInterface
public interface ValueConverter {

    Object convert(StructuredValue value);
}
