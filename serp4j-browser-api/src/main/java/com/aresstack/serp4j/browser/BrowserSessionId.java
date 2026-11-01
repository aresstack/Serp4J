package com.aresstack.serp4j.browser;

import java.util.UUID;

/** Opaque id of a {@link BrowserSession}. Immutable. */
public final class BrowserSessionId {

    private final String value;

    private BrowserSessionId(String value) {
        this.value = value;
    }

    /**
     * @param value non-blank id
     * @return the id
     */
    public static BrowserSessionId of(String value) {
        return new BrowserSessionId(Args.notBlank(value, "value").trim());
    }

    /**
     * @return a new random id
     */
    public static BrowserSessionId random() {
        return new BrowserSessionId(UUID.randomUUID().toString());
    }

    /**
     * @return the id string
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof BrowserSessionId && value.equals(((BrowserSessionId) o).value));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
