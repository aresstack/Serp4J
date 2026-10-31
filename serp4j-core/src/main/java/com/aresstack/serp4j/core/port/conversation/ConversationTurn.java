package com.aresstack.serp4j.core.port.conversation;

import com.aresstack.serp4j.core.annotation.Experimental;
import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.SearchResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A completed turn of a conversation (WP2 §11.3): the request, the response and the timing. Turn 0 is the initial
 * search, every later turn a follow-up. The response is a normal {@link SearchResponse}; the AI answer is a
 * {@code GeneratedAnswerFeature} of type {@code ai_mode_answer} in its data.
 */
@Experimental("conversational search stabilises with the first Google AI Mode adapter (Paket G)")
public final class ConversationTurn {

    private final int index;
    private final SearchConversationRequest request;
    private final SearchResponse response;
    private final Instant startedAt;
    private final Instant completedAt;

    private ConversationTurn(int index, SearchConversationRequest request, SearchResponse response,
                             Instant startedAt, Instant completedAt) {
        this.index = index;
        this.request = request;
        this.response = response;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    /** @throws IllegalArgumentException if {@code index < 0} or {@code completedAt} is before {@code startedAt} */
    public static ConversationTurn of(int index, SearchConversationRequest request, SearchResponse response,
                                      Instant startedAt, Instant completedAt) {
        Preconditions.requireNonNegative(index, "index");
        Preconditions.requireNonNull(startedAt, "startedAt");
        Preconditions.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt " + completedAt + " is before startedAt " + startedAt);
        }
        return new ConversationTurn(index, Preconditions.requireNonNull(request, "request"),
                Preconditions.requireNonNull(response, "response"), startedAt, completedAt);
    }

    /** 0-based position in the conversation. */
    public int getIndex() {
        return index;
    }

    public TurnKind getKind() {
        return index == 0 ? TurnKind.INITIAL : TurnKind.FOLLOW_UP;
    }

    public SearchConversationRequest getRequest() {
        return request;
    }

    public SearchResponse getResponse() {
        return response;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Duration getDuration() {
        return Duration.between(startedAt, completedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConversationTurn)) {
            return false;
        }
        ConversationTurn that = (ConversationTurn) o;
        return index == that.index
                && request.equals(that.request)
                && response.equals(that.response)
                && startedAt.equals(that.startedAt)
                && completedAt.equals(that.completedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, request, response, startedAt, completedAt);
    }

    @Override
    public String toString() {
        return "ConversationTurn{index=" + index + ", kind=" + getKind() + ", message='" + request.getMessage()
                + "', duration=" + getDuration() + '}';
    }
}
