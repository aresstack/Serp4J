package com.aresstack.serp4j.core.port.conversation;

import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchSessionId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.port.capability.ResponseFeature;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ConversationTypesTest {

    private static final SearchTarget AI_MODE = SearchTarget.of(SearchEngineId.GOOGLE, SearchVertical.AI_MODE);
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static ConversationTurn turn(int index, String message) {
        return ConversationTurn.of(index, SearchConversationRequest.of(message),
                SearchResponse.builder(SearchData.empty()).build(), T0.plusSeconds(index), T0.plusSeconds(index + 1));
    }

    @Test
    void turnsAreIndexedAndKindDerived() {
        assertThat(turn(0, "q").getKind()).isEqualTo(TurnKind.INITIAL);
        assertThat(turn(1, "follow-up").getKind()).isEqualTo(TurnKind.FOLLOW_UP);
        assertThat(turn(0, "q").getDuration()).isEqualTo(Duration.ofSeconds(1));
        assertThatIllegalArgumentException().isThrownBy(() -> ConversationTurn.of(0, SearchConversationRequest.of("q"),
                SearchResponse.builder(SearchData.empty()).build(), T0.plusSeconds(1), T0));
    }

    @Test
    void conversationRequiresConsecutiveTurnsAndExposesLastTurn() {
        SearchSessionId session = SearchSessionId.random();
        SearchConversation conversation = SearchConversation
                .builder(session, ProviderId.of("google-ai-mode"), AI_MODE, SessionState.ACTIVE)
                .turn(turn(0, "What is Serp4J?")).turn(turn(1, "And the licence?")).build();

        assertThat(conversation.getTurnCount()).isEqualTo(2);
        assertThat(conversation.getLastTurn().get().getRequest().getMessage()).isEqualTo("And the licence?");
        assertThat(conversation.getState().acceptsTurns()).isTrue();
        assertThat(SessionState.CLOSED.isTerminal()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> SearchConversation
                .builder(session, ProviderId.of("p"), AI_MODE, SessionState.OPEN).turn(turn(1, "x")).build());
    }

    @Test
    void optionsAndRequestsAreValueObjectsAndHandleHidesItsToken() {
        SearchSessionOptions options = SearchSessionOptions.builder(AI_MODE).maxTurns(5)
                .idleTimeout(Duration.ofMinutes(10)).build();
        SearchConversationRequest request = SearchConversationRequest.builder("q").expectedTurnIndex(0)
                .requestedFeature(ResponseFeature.CITED_SOURCES).build();
        SessionHandle handle = SessionHandle.of(ProviderId.of("p"), SearchSessionId.of("s"), "opaque-token-value");

        assertThat(options.toBuilder().build()).isEqualTo(options);
        assertThat(options.getMaxTurns()).hasValue(5);
        assertThat(request.toBuilder().build()).isEqualTo(request);
        assertThat(request.getExpectedTurnIndex()).hasValue(0);
        assertThat(request.getRequestedFeatures()).containsExactly(ResponseFeature.CITED_SOURCES);
        assertThat(handle.toString()).doesNotContain("opaque-token-value");
        assertThat(handle.getOpaqueToken()).isEqualTo("opaque-token-value");
    }
}
