package com.aresstack.serp4j.core.port.conversation;

import com.aresstack.serp4j.core.annotation.Experimental;

/** Kind of a {@link ConversationTurn}: the initial search or a follow-up question. */
@Experimental("conversational search stabilises with the first Google AI Mode adapter (Paket G)")
public enum TurnKind {
    INITIAL, FOLLOW_UP
}
