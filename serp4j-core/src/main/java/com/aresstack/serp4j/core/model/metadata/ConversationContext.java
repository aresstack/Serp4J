package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ElementId;
import com.aresstack.serp4j.core.model.id.SearchSessionId;

import java.util.Objects;
import java.util.Optional;

/**
 * Conversation state a response belongs to (ADR-009, WP1 §4.11): the session, the 0-based turn index, the
 * provider's own conversation token if it exposes one, and a reference to the answer element of this turn.
 * Present only on responses of a conversational search.
 */
public final class ConversationContext {

    private final SearchSessionId sessionId;
    private final int turnIndex;
    private final String providerConversationId;
    private final ElementId answerElement;

    private ConversationContext(Builder builder) {
        this.sessionId = builder.sessionId;
        this.turnIndex = builder.turnIndex;
        this.providerConversationId = builder.providerConversationId;
        this.answerElement = builder.answerElement;
    }

    /**
     * @param turnIndex 0-based index of the turn within the session ({@code 0} = initial search)
     * @throws IllegalArgumentException if {@code turnIndex < 0}
     */
    public static Builder builder(SearchSessionId sessionId, int turnIndex) {
        return new Builder(sessionId, turnIndex);
    }

    /** Shorthand for {@code builder(sessionId, turnIndex).build()}. */
    public static ConversationContext of(SearchSessionId sessionId, int turnIndex) {
        return builder(sessionId, turnIndex).build();
    }

    /** Shorthand for {@code builder(sessionId, turnIndex).providerConversationId(providerConversationId).build()}. */
    public static ConversationContext of(SearchSessionId sessionId, int turnIndex, String providerConversationId) {
        return builder(sessionId, turnIndex).providerConversationId(providerConversationId).build();
    }

    public SearchSessionId getSessionId() {
        return sessionId;
    }

    /**
     * 0-based index of the turn within the session, the same value as {@code ConversationTurn.getIndex()};
     * {@code 0} is the initial search, every follow-up question increments it.
     */
    public int getTurnIndex() {
        return turnIndex;
    }

    /** The provider's own conversation identifier; treat as opaque and never log it as a secret-bearing value. */
    public Optional<String> getProviderConversationId() {
        return Optional.ofNullable(providerConversationId);
    }

    /** Reference to the {@code GeneratedAnswerFeature} of this turn within the response's {@code SearchData}. */
    public Optional<ElementId> getAnswerElement() {
        return Optional.ofNullable(answerElement);
    }

    /** Returns a builder pre-populated with all values of this instance. */
    public Builder toBuilder() {
        Builder builder = new Builder(sessionId, turnIndex);
        builder.providerConversationId = providerConversationId;
        builder.answerElement = answerElement;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConversationContext)) {
            return false;
        }
        ConversationContext that = (ConversationContext) o;
        return sessionId.equals(that.sessionId)
                && turnIndex == that.turnIndex
                && Objects.equals(providerConversationId, that.providerConversationId)
                && Objects.equals(answerElement, that.answerElement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, turnIndex, providerConversationId, answerElement);
    }

    @Override
    public String toString() {
        return "ConversationContext{sessionId=" + sessionId + ", turnIndex=" + turnIndex
                + ", answerElement=" + answerElement + '}';
    }

    /** Builder for {@link ConversationContext}. */
    public static final class Builder {
        private final SearchSessionId sessionId;
        private final int turnIndex;
        private String providerConversationId;
        private ElementId answerElement;

        private Builder(SearchSessionId sessionId, int turnIndex) {
            this.sessionId = Preconditions.requireNonNull(sessionId, "sessionId");
            this.turnIndex = Preconditions.requireNonNegative(turnIndex, "turnIndex");
        }

        /** Blank values are treated as absent; surrounding whitespace is removed. */
        public Builder providerConversationId(String providerConversationId) {
            this.providerConversationId = Preconditions.trimToNull(providerConversationId);
            return this;
        }

        public Builder answerElement(ElementId answerElement) {
            this.answerElement = answerElement;
            return this;
        }

        public ConversationContext build() {
            return new ConversationContext(this);
        }
    }
}
