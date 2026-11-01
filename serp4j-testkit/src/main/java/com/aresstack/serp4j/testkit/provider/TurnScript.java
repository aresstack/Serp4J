package com.aresstack.serp4j.testkit.provider;

import com.aresstack.serp4j.core.port.conversation.SearchConversationRequest;

/** Chooses the {@link Outcome} of one turn of a {@link ScriptedConversationalProvider} session. */
public interface TurnScript {

    /**
     * @param turnIndex 0-based index of the turn about to run; 0 is the initial search
     * @param request   the turn's request
     * @return what the turn does; never {@code null}
     */
    Outcome answer(int turnIndex, SearchConversationRequest request);
}
