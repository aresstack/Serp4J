package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.SearchSessionId;

import java.util.Objects;
import java.util.Optional;

/**
 * Conversation state a response belongs to (ADR-009): the session, the 1-based turn number and, if the provider
 * exposes one, its own conversation token. Present only on responses of a conversational search.
 */
public final class ConversationContext {

    private final SearchSessionId sessionId;
    private final int turnNumber;
    private final String providerConversationId;

    private ConversationContext(SearchSessionId sessionId, int turnNumber, String providerConversationId) {
        this.sessionId = sessionId;
        this.turnNumber = turnNumber;
        this.providerConversationId = providerConversationId;
    }

    public static ConversationContext of(SearchSessionId sessionId, int turnNumber) {
        return of(sessionId, turnNumber, null);
    }

    public static ConversationContext of(SearchSessionId sessionId, int turnNumber, String providerConversationId) {
        return new ConversationContext(Preconditions.requireNonNull(sessionId, "sessionId"),
                Preconditions.requirePositive(turnNumber, "turnNumber"),
                Preconditions.trimToNull(providerConversationId));
    }

    public SearchSessionId getSessionId() {
        return sessionId;
    }

    /** 1-based number of the turn within the session. */
    public int getTurnNumber() {
        return turnNumber;
    }

    /** The provider's own conversation identifier; treat as opaque and never log it as a secret-bearing value. */
    public Optional<String> getProviderConversationId() {
        return Optional.ofNullable(providerConversationId);
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
                && turnNumber == that.turnNumber
                && Objects.equals(providerConversationId, that.providerConversationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, turnNumber, providerConversationId);
    }

    @Override
    public String toString() {
        return "ConversationContext{sessionId=" + sessionId + ", turnNumber=" + turnNumber + '}';
    }
}
