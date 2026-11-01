package com.aresstack.serp4j.mapping.convert;

/** Thrown by a {@link ValueConverter} for a value it cannot convert. */
@SuppressWarnings("serial")
public class ValueConversionException extends RuntimeException {

    public ValueConversionException(String message) {
        super(message);
    }

    public ValueConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
