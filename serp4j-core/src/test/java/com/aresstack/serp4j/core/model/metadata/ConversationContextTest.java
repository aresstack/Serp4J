package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.model.id.ElementId;
import com.aresstack.serp4j.core.model.id.SearchSessionId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * A.1 #5/#14: {@link ConversationContext} carries a 0-based turn index and the answer element
 * (WP1 §4.11, ADR-009), toBuilder, validation, equality.
 */
class ConversationContextTest {

    private static final SearchSessionId SESSION = SearchSessionId.of("session-1");
    private static final ElementId ANSWER = ElementId.of("f-1");

    @Test
    void turnIndexIsZeroBasedAndZeroIsTheInitialSearch() {
        ConversationContext initial = ConversationContext.of(SESSION, 0);

        assertThat(initial.getTurnIndex()).isZero();
        assertThat(initial.getSessionId()).isEqualTo(SESSION);
        assertThat(initial.getProviderConversationId()).isEmpty();
        assertThat(initial.getAnswerElement()).isEmpty();
        assertThat(ConversationContext.builder(SESSION, 3).build().getTurnIndex()).isEqualTo(3);
    }

    @Test
    void negativeTurnIndexIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConversationContext.of(SESSION, -1))
                .withMessageContaining("turnIndex");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConversationContext.builder(SESSION, -1));
    }

    @Test
    void sessionIdIsMandatory() {
        assertThatNullPointerException().isThrownBy(() -> ConversationContext.of(null, 0));
        assertThatNullPointerException().isThrownBy(() -> ConversationContext.builder(null, 0));
    }

    @Test
    void answerElementIsOptional() {
        ConversationContext withAnswer = ConversationContext.builder(SESSION, 1).answerElement(ANSWER).build();

        assertThat(withAnswer.getAnswerElement()).contains(ANSWER);
        assertThat(withAnswer.toBuilder().answerElement(null).build().getAnswerElement()).isEmpty();
        assertThat(withAnswer.toString()).contains("turnIndex=1").contains("answerElement=f-1");
    }

    @Test
    void providerConversationIdIsTrimmedAndBlankMeansAbsent() {
        assertThat(ConversationContext.of(SESSION, 0, " conv-7 ").getProviderConversationId()).contains("conv-7");
        assertThat(ConversationContext.of(SESSION, 0, "   ").getProviderConversationId()).isEmpty();
        assertThat(ConversationContext.of(SESSION, 0, null).getProviderConversationId()).isEmpty();
    }

    @Test
    void toStringDoesNotLeakTheProviderConversationId() {
        assertThat(ConversationContext.of(SESSION, 0, "opaque-token-xyz").toString())
                .doesNotContain("opaque-token-xyz");
    }

    @Test
    void toBuilderRoundTripKeepsEveryField() {
        ConversationContext original = ConversationContext.builder(SESSION, 2)
                .providerConversationId("conv-7")
                .answerElement(ANSWER)
                .build();

        ConversationContext copy = original.toBuilder().build();

        assertThat(copy).isEqualTo(original);
        assertThat(copy.hashCode()).isEqualTo(original.hashCode());
        assertThat(copy.getTurnIndex()).isEqualTo(2);
        assertThat(copy.getProviderConversationId()).contains("conv-7");
        assertThat(copy.getAnswerElement()).contains(ANSWER);
    }

    @Test
    void equalityAndInequality() {
        ConversationContext base = ConversationContext.builder(SESSION, 1)
                .providerConversationId("conv-7").answerElement(ANSWER).build();
        ConversationContext same = ConversationContext.builder(SearchSessionId.of("session-1"), 1)
                .providerConversationId("conv-7").answerElement(ElementId.of("f-1")).build();

        assertThat(base).isEqualTo(same);
        assertThat(base.hashCode()).isEqualTo(same.hashCode());
        assertThat(base).isNotEqualTo(ConversationContext.builder(SearchSessionId.of("session-2"), 1)
                .providerConversationId("conv-7").answerElement(ANSWER).build());
        assertThat(base).isNotEqualTo(base.toBuilder().answerElement(ElementId.of("f-2")).build());
        assertThat(base).isNotEqualTo(base.toBuilder().answerElement(null).build());
        assertThat(base).isNotEqualTo(base.toBuilder().providerConversationId("conv-8").build());
        assertThat(base).isNotEqualTo(ConversationContext.builder(SESSION, 2)
                .providerConversationId("conv-7").answerElement(ANSWER).build());
        assertThat(base).isNotEqualTo(null);
        assertThat(base).isNotEqualTo("ConversationContext");
    }
}
