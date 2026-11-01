package com.aresstack.serp4j.json;

/**
 * Thrown when a JSON document is valid JSON but not a valid Serp4J model document (missing required field, wrong
 * value type, unknown discriminator, invalid value), or when a model value cannot be written (ADR-013).
 *
 * <p>Extends {@link JsonTreeException}, so callers can catch invalid JSON text and invalid model documents with one
 * type.</p>
 */
@SuppressWarnings("serial")
public class Serp4jJsonException extends JsonTreeException {

    public Serp4jJsonException(String message) {
        super(message);
    }

    public Serp4jJsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
