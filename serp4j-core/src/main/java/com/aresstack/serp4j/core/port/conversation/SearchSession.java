package com.aresstack.serp4j.core.port.conversation;

import com.aresstack.serp4j.core.annotation.Experimental;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchSessionId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.port.SearchContext;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A stateful conversation with a search engine's AI surface (ADR-009, WP2 §11): the initial search followed by
 * follow-up questions that keep the engine-side context. Created by
 * {@link ConversationalSearchProvider#openSession}; owned and closed by the caller.
 *
 * <p>Implementations must be thread-safe; {@link #send}, {@link #getState()}, {@link #snapshot()} and
 * {@link #close()} may be called concurrently (a concurrent send fails with {@code SESSION_BUSY}).</p>
 */
@Experimental("conversational search stabilises with the first Google AI Mode adapter (Paket G)")
public interface SearchSession extends AutoCloseable {

    SearchSessionId getId();

    ProviderId getProviderId();

    SearchTarget getTarget();

    SessionState getState();

    /**
     * Sends the next turn: the first call is the initial search, every later call a follow-up.
     *
     * <p>Contract (failures are {@link com.aresstack.serp4j.core.error.SearchSessionException}s via the future):</p>
     * <ul>
     * <li>At most one turn runs per session; a concurrent second call fails with {@code SESSION_BUSY} – no implicit
     * queueing, so the order stays unambiguous.</li>
     * <li>If {@link SearchConversationRequest#getExpectedTurnIndex()} is set and does not match, the call fails with
     * {@code INVALID_REQUEST}.</li>
     * <li>When the provider expired the session: {@code SESSION_EXPIRED}, state becomes {@link SessionState#EXPIRED}.
     * A new session is never opened silently.</li>
     * <li>When the turn limit is reached: {@code TURN_LIMIT_REACHED}; the session stays readable but accepts no
     * further turns.</li>
     * <li>After {@link #close()}: {@code SESSION_CLOSED}. When the provider lost the session's state (e.g. after a
     * restart) and it cannot be resumed: {@code SESSION_LOST}.</li>
     * <li>Honours deadline and cancellation of the context.</li>
     * </ul>
     */
    CompletableFuture<ConversationTurn> send(SearchConversationRequest request, SearchContext context);

    /** Immutable snapshot of all completed turns; cheap, no I/O. */
    SearchConversation snapshot();

    /** Handle to resume the session later, present only for resumable providers. */
    Optional<SessionHandle> getHandle();

    /**
     * Releases the session's resources and completes a running turn with {@code CANCELLED}. Idempotent. Closes only
     * what the session owns (ADR-017): never the provider or shared infrastructure.
     *
     * <p>Thread-safe: may be called from any thread, concurrently with running calls and with a second {@code close()}; the first call wins, concurrent calls return after the resources are released or at once. (ADR-017 rule 8).</p>
     */
    @Override
    void close();
}
