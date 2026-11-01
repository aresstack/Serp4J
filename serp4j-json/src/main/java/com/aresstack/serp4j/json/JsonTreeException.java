package com.aresstack.serp4j.json;

/** Thrown for invalid JSON text or an unreadable JSON resource. */
@SuppressWarnings("serial")
public class JsonTreeException extends RuntimeException {

    public JsonTreeException(String message) {
        super(message);
    }

    public JsonTreeException(String message, Throwable cause) {
        super(message, cause);
    }
}
