package com.aresstack.serp4j.core.port.conversation;

import com.aresstack.serp4j.core.annotation.Experimental;
import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchSessionId;
import com.aresstack.serp4j.core.model.id.SearchTarget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of a session's completed turns (WP2 §11.3), suitable for persistence by the consumer. There is
 * no separate "final response": the provider cannot know when a dialogue is done, so the final answer is simply
 * {@link #getLastTurn()} before the session is closed.
 */
@Experimental("conversational search stabilises with the first Google AI Mode adapter (Paket G)")
public final class SearchConversation {

    private final SearchSessionId sessionId;
    private final ProviderId providerId;
    private final SearchTarget target;
    private final SessionState state;
    private final List<ConversationTurn> turns;

    private SearchConversation(Builder builder) {
        this.sessionId = builder.sessionId;
        this.providerId = builder.providerId;
        this.target = builder.target;
        this.state = builder.state;
        this.turns = Preconditions.immutableList(builder.turns, "turns");
        for (int i = 0; i < turns.size(); i++) {
            if (turns.get(i).getIndex() != i) {
                throw new IllegalArgumentException("turn at position " + i + " has index " + turns.get(i).getIndex()
                        + "; turns must be consecutive from 0");
            }
        }
    }

    public static Builder builder(SearchSessionId sessionId, ProviderId providerId, SearchTarget target,
                                  SessionState state) {
        return new Builder(sessionId, providerId, target, state);
    }

    public SearchSessionId getSessionId() {
        return sessionId;
    }

    public ProviderId getProviderId() {
        return providerId;
    }

    public SearchTarget getTarget() {
        return target;
    }

    public SessionState getState() {
        return state;
    }

    /** Completed turns in order; index {@code i} has turn index {@code i}. */
    public List<ConversationTurn> getTurns() {
        return turns;
    }

    public Optional<ConversationTurn> getLastTurn() {
        return turns.isEmpty() ? Optional.<ConversationTurn>empty() : Optional.of(turns.get(turns.size() - 1));
    }

    public int getTurnCount() {
        return turns.size();
    }

    public Builder toBuilder() {
        Builder builder = new Builder(sessionId, providerId, target, state);
        builder.turns.addAll(turns);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchConversation)) {
            return false;
        }
        SearchConversation that = (SearchConversation) o;
        return sessionId.equals(that.sessionId)
                && providerId.equals(that.providerId)
                && target.equals(that.target)
                && state == that.state
                && turns.equals(that.turns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, providerId, target, state, turns);
    }

    @Override
    public String toString() {
        return "SearchConversation{sessionId=" + sessionId + ", providerId=" + providerId + ", target=" + target
                + ", state=" + state + ", turns=" + turns.size() + '}';
    }

    /** Builder for {@link SearchConversation}. */
    public static final class Builder {
        private final SearchSessionId sessionId;
        private final ProviderId providerId;
        private final SearchTarget target;
        private SessionState state;
        private final List<ConversationTurn> turns = new ArrayList<ConversationTurn>();

        private Builder(SearchSessionId sessionId, ProviderId providerId, SearchTarget target, SessionState state) {
            this.sessionId = Preconditions.requireNonNull(sessionId, "sessionId");
            this.providerId = Preconditions.requireNonNull(providerId, "providerId");
            this.target = Preconditions.requireNonNull(target, "target");
            this.state = Preconditions.requireNonNull(state, "state");
        }

        public Builder state(SessionState state) {
            this.state = Preconditions.requireNonNull(state, "state");
            return this;
        }

        public Builder turn(ConversationTurn turn) {
            turns.add(Preconditions.requireNonNull(turn, "turn"));
            return this;
        }

        public Builder turns(Collection<ConversationTurn> turns) {
            for (ConversationTurn turn : Preconditions.requireNonNull(turns, "turns")) {
                turn(turn);
            }
            return this;
        }

        /** @throws IllegalArgumentException if the turn indexes are not consecutive from 0 */
        public SearchConversation build() {
            return new SearchConversation(this);
        }
    }
}
