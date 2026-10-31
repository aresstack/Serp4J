package com.aresstack.serp4j.core.model.id;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.UUID;

/**
 * Opaque identifier of a conversational {@code SearchSession}, assigned by Serp4J (never a provider-internal
 * identifier; the provider's own conversation id lives in {@code ConversationContext}).
 */
public final class SearchSessionId {

    private final String value;

    private SearchSessionId(String value) {
        this.value = value;
    }

    /** Creates an identifier from any non-blank text. */
    public static SearchSessionId of(String value) {
        return new SearchSessionId(Preconditions.requireText(value, "session id"));
    }

    /** Creates a random identifier. */
    public static SearchSessionId random() {
        return new SearchSessionId(UUID.randomUUID().toString());
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof SearchSessionId && value.equals(((SearchSessionId) o).value));
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
