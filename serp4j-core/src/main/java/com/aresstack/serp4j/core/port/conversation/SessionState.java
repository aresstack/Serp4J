package com.aresstack.serp4j.core.port.conversation;

import com.aresstack.serp4j.core.annotation.Experimental;

/** Lifecycle state of a {@link SearchSession} (WP2 §11.4). */
@Experimental("conversational search stabilises with the first Google AI Mode adapter (Paket G)")
public enum SessionState {
    /** Opened, no turn sent yet. */
    OPEN,
    /** A turn is in flight; a second {@code send} fails with {@code SESSION_BUSY}. */
    BUSY,
    /** At least one turn completed; ready for the next one. */
    ACTIVE,
    /** The provider expired the session; no further turns. */
    EXPIRED,
    /** The conversation context is lost (e.g. blocked or crashed); no further turns. */
    FAILED,
    /** Closed by the caller or the provider; no further turns. */
    CLOSED;

    /** Whether {@code send} may be called in this state. */
    public boolean acceptsTurns() {
        return this == OPEN || this == ACTIVE;
    }

    /** Whether the session will never accept a turn again. */
    public boolean isTerminal() {
        return this == EXPIRED || this == FAILED || this == CLOSED;
    }
}
