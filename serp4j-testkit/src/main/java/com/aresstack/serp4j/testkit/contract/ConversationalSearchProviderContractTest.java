package com.aresstack.serp4j.testkit.contract;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.metadata.ConversationContext;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.capability.ConversationalCapabilities;
import com.aresstack.serp4j.core.port.conversation.ConversationTurn;
import com.aresstack.serp4j.core.port.conversation.ConversationalSearchProvider;
import com.aresstack.serp4j.core.port.conversation.SearchConversation;
import com.aresstack.serp4j.core.port.conversation.SearchConversationRequest;
import com.aresstack.serp4j.core.port.conversation.SearchSession;
import com.aresstack.serp4j.core.port.conversation.SearchSessionOptions;
import com.aresstack.serp4j.core.port.conversation.SessionHandle;
import com.aresstack.serp4j.core.port.conversation.SessionState;
import com.aresstack.serp4j.core.port.conversation.TurnKind;
import com.aresstack.serp4j.testkit.SharedInfrastructure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertCompletes;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertFailsWith;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertSessionFailure;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertSingleProviderResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contract every {@link ConversationalSearchProvider} and its {@link SearchSession}s must fulfil (ADR-009, WP2 §11,
 * ADR-017). Each test gets fresh infrastructure and two providers that borrow it; sessions opened through
 * {@link #open(ConversationalSearchProvider)} are closed after the test.
 *
 * <ul>
 * <li><strong>Sessions:</strong> open empty in state {@code OPEN}; turn 0 is the initial search, later turns are
 * follow-ups; state {@code ACTIVE} after a turn; snapshots are immutable; responses are normal single-provider
 * responses with a {@link ConversationContext} (session id, 0-based turn index).</li>
 * <li><strong>Turn rules:</strong> wrong {@code expectedTurnIndex} fails with {@code INVALID_REQUEST} and leaves the
 * session usable; a declared turn limit fails with {@code TURN_LIMIT_REACHED}; after {@code close()} turns fail with
 * {@code SESSION_CLOSED}; {@code close()} is idempotent.</li>
 * <li><strong>Resume:</strong> a handle exists iff the provider is resumable; non-resumable providers fail
 * {@code resumeSession} with {@code UNSUPPORTED}; resumable ones continue the same conversation.</li>
 * <li><strong>Ownership:</strong> closing the provider closes its sessions but never the borrowed infrastructure;
 * the other provider keeps working; opening after close fails with {@code CANCELLED}.</li>
 * </ul>
 *
 * <p>{@code SESSION_BUSY} cannot be provoked generically (a fast adapter finishes the first turn before the second
 * starts); adapters test it with a delayed fixture, the testkit's own tests check it on the scripted mock.</p>
 *
 * @param <I> the infrastructure the providers borrow
 */
public abstract class ConversationalSearchProviderContractTest<I extends SharedInfrastructure> {

    protected static final ProviderId PROVIDER_A = ProviderId.of("contract-a");
    protected static final ProviderId PROVIDER_B = ProviderId.of("contract-b");

    protected I infrastructure;
    protected ConversationalSearchProvider provider;
    protected ConversationalSearchProvider secondProvider;
    private final List<SearchSession> sessions = new CopyOnWriteArrayList<SearchSession>();

    /** Fresh infrastructure, prepared so that sessions can be opened and turns answered. */
    protected abstract I createInfrastructure();

    /** Creates a provider with the given id that <em>borrows</em> {@code infrastructure}. */
    protected abstract ConversationalSearchProvider createProvider(ProviderId id, I infrastructure);

    /** Options of a session the provider supports. */
    protected abstract SearchSessionOptions sessionOptions();

    /** Request of the initial turn. */
    protected SearchConversationRequest initialRequest() {
        return SearchConversationRequest.of("serp4j conversational contract");
    }

    /** Request of a follow-up turn. */
    protected SearchConversationRequest followUpRequest() {
        return SearchConversationRequest.of("serp4j follow-up question");
    }

    protected SearchContext context() {
        return SearchContext.defaults();
    }

    @BeforeEach
    protected final void setUpContract() {
        infrastructure = createInfrastructure();
        provider = createProvider(PROVIDER_A, infrastructure);
        secondProvider = createProvider(PROVIDER_B, infrastructure);
    }

    @AfterEach
    protected final void tearDownContract() {
        for (SearchSession session : sessions) {
            session.close();
        }
        provider.close();
        secondProvider.close();
        infrastructure.close();
    }

    /** Opens a session that is closed after the test. */
    protected final SearchSession open(ConversationalSearchProvider target) {
        SearchSession session = assertCompletes(target.openSession(sessionOptions(), context()));
        sessions.add(session);
        return session;
    }

    protected final ConversationTurn send(SearchSession session, SearchConversationRequest request) {
        return assertCompletes(session.send(request, context()));
    }

    protected final ConversationalCapabilities conversationalCapabilities() {
        return provider.getDescriptor().getCapabilities().getConversational()
                .orElseThrow(() -> new AssertionError("conversational capabilities must be declared"));
    }

    private int maxTurns() {
        int limit = Integer.MAX_VALUE;
        if (conversationalCapabilities().getMaxTurns().isPresent()) {
            limit = conversationalCapabilities().getMaxTurns().getAsInt();
        }
        if (sessionOptions().getMaxTurns().isPresent()) {
            limit = Math.min(limit, sessionOptions().getMaxTurns().getAsInt());
        }
        return limit;
    }

    @Nested
    @DisplayName("capabilities")
    class Capabilities {

        @Test
        @DisplayName("conversational capabilities are declared and serve the session target")
        void conversationalCapabilitiesDeclared() {
            assertTrue(provider.getDescriptor().getCapabilities().getConversational().isPresent(),
                    "ConversationalSearchProvider must declare conversational capabilities (WP2 §6.2)");
            assertTrue(provider.getDescriptor().getCapabilities().supports(sessionOptions().getTarget()),
                    "session target is declared");
            assertEquals(PROVIDER_A, provider.getDescriptor().getId());
        }

        @Test
        @DisplayName("null arguments throw NullPointerException synchronously")
        void nullArgumentsThrowSynchronously() {
            assertThrows(NullPointerException.class, () -> provider.openSession(null, context()));
            assertThrows(NullPointerException.class, () -> provider.openSession(sessionOptions(), null));
        }
    }

    @Nested
    @DisplayName("session contract (ADR-009)")
    class SessionContract {

        @Test
        @DisplayName("a new session is open and empty")
        void newSessionIsOpenAndEmpty() {
            SearchSession session = open(provider);
            assertEquals(SessionState.OPEN, session.getState());
            assertEquals(PROVIDER_A, session.getProviderId());
            assertEquals(sessionOptions().getTarget(), session.getTarget());
            SearchConversation snapshot = session.snapshot();
            assertEquals(0, snapshot.getTurnCount());
            assertEquals(session.getId(), snapshot.getSessionId());
        }

        @Test
        @DisplayName("turn 0 is the initial search and activates the session")
        void firstTurnIsInitial() {
            SearchSession session = open(provider);
            ConversationTurn turn = send(session, initialRequest());

            assertEquals(0, turn.getIndex());
            assertEquals(TurnKind.INITIAL, turn.getKind());
            assertEquals(SessionState.ACTIVE, session.getState());
            SearchMetadata metadata = assertSingleProviderResponse(turn.getResponse(), PROVIDER_A);
            assertTrue(metadata.getConversation().isPresent(), "turn responses carry a ConversationContext");
            ConversationContext conversation = metadata.getConversation().get();
            assertEquals(session.getId(), conversation.getSessionId());
            assertEquals(0, conversation.getTurnIndex(), "turn index is 0-based like ConversationTurn.getIndex()");
            assertFalse(turn.getResponse().getAnalysis().isPresent(), "providers never produce analysis");
        }

        @Test
        @DisplayName("follow-up continues the conversation; snapshots are immutable")
        void followUpContinuesConversation() {
            assumeTrue(maxTurns() >= 2, "provider allows only one turn");
            SearchSession session = open(provider);
            send(session, initialRequest());
            SearchConversation afterFirst = session.snapshot();

            ConversationTurn followUp = send(session, followUpRequest());

            assertEquals(1, followUp.getIndex());
            assertEquals(TurnKind.FOLLOW_UP, followUp.getKind());
            assertEquals(1, followUp.getResponse().getMetadata().get().getConversation().get().getTurnIndex());
            assertEquals(1, afterFirst.getTurnCount(), "earlier snapshot is unchanged");
            assertEquals(2, session.snapshot().getTurnCount());
            assertEquals(followUp, session.snapshot().getLastTurn().get());
        }

        @Test
        @DisplayName("wrong expectedTurnIndex fails with INVALID_REQUEST and keeps the session usable")
        void wrongExpectedTurnIndexFails() {
            SearchSession session = open(provider);
            SearchConversationRequest wrong = initialRequest().toBuilder().expectedTurnIndex(5).build();
            assertSessionFailure(session.send(wrong, context()), SearchErrorCategory.INVALID_REQUEST);

            SearchConversationRequest right = initialRequest().toBuilder().expectedTurnIndex(0).build();
            assertEquals(0, send(session, right).getIndex());
        }

        @Test
        @DisplayName("declared turn limit fails with TURN_LIMIT_REACHED; the session stays readable")
        void turnLimitIsEnforced() {
            int limit = maxTurns();
            assumeTrue(limit <= 10, "no small turn limit declared");
            SearchSession session = open(provider);
            send(session, initialRequest());
            for (int i = 1; i < limit; i++) {
                send(session, followUpRequest());
            }
            assertSessionFailure(session.send(followUpRequest(), context()), SearchErrorCategory.TURN_LIMIT_REACHED);
            assertEquals(limit, session.snapshot().getTurnCount());
        }

        @Test
        @DisplayName("after close() turns fail with SESSION_CLOSED; close() is idempotent")
        void sendAfterCloseFails() {
            SearchSession session = open(provider);
            session.close();
            session.close();
            assertEquals(SessionState.CLOSED, session.getState());
            assertSessionFailure(session.send(initialRequest(), context()), SearchErrorCategory.SESSION_CLOSED);
            assertFalse(infrastructure.isClosed(), "closing a session never closes infrastructure");
        }
    }

    @Nested
    @DisplayName("resume")
    class Resume {

        @Test
        @DisplayName("a handle exists iff the provider is resumable")
        void handleIffResumable() {
            SearchSession session = open(provider);
            assertEquals(conversationalCapabilities().isResumable(), session.getHandle().isPresent());
        }

        @Test
        @DisplayName("non-resumable providers fail resumeSession with UNSUPPORTED")
        void resumeUnsupported() {
            assumeFalse(conversationalCapabilities().isResumable(), "provider is resumable");
            SearchSession session = open(provider);
            SessionHandle foreign = SessionHandle.of(PROVIDER_A, session.getId(), "serp4j-contract-token");
            assertFailsWith(provider.resumeSession(foreign, context()), SearchErrorCategory.UNSUPPORTED);
        }

        @Test
        @DisplayName("resumable providers continue the same conversation")
        void resumeContinuesConversation() {
            assumeTrue(conversationalCapabilities().isResumable(), "provider is not resumable");
            SearchSession session = open(provider);
            send(session, initialRequest());
            SessionHandle handle = session.getHandle().get();
            session.close();

            SearchSession resumed = assertCompletes(provider.resumeSession(handle, context()));
            sessions.add(resumed);
            assertEquals(session.getId(), resumed.getId());
            assertEquals(1, resumed.snapshot().getTurnCount());
        }
    }

    @Nested
    @DisplayName("ownership contract (ADR-017)")
    class OwnershipContract {

        @Test
        @DisplayName("closing the provider closes its sessions but not the infrastructure")
        void providerCloseClosesSessions() {
            SearchSession session = open(provider);
            provider.close();

            assertEquals(SessionState.CLOSED, session.getState());
            assertSessionFailure(session.send(initialRequest(), context()), SearchErrorCategory.SESSION_CLOSED);
            assertFalse(infrastructure.isClosed(), "injected infrastructure belongs to the caller");
            assertEquals(0, infrastructure.getCloseCalls());
        }

        @Test
        @DisplayName("closing one provider leaves the other provider on the same infrastructure working")
        void otherProviderKeepsWorking() {
            provider.close();
            SearchSession session = open(secondProvider);
            ConversationTurn turn = send(session, initialRequest());
            assertSingleProviderResponse(turn.getResponse(), PROVIDER_B);
        }

        @Test
        @DisplayName("concurrent close() calls neither fail nor close the infrastructure (ADR-017 rule 8)")
        void concurrentCloseCallsDoNotFail() throws Exception {
            SearchSession session = open(provider);
            ConcurrentClose.run(4, provider::close, session::close);
            assertEquals(SessionState.CLOSED, session.getState());
            assertFalse(infrastructure.isClosed());
            assertEquals(0, infrastructure.getCloseCalls());
            assertFailsWith(provider.openSession(sessionOptions(), context()), SearchErrorCategory.CANCELLED);
        }

        @Test
        @DisplayName("openSession after close fails with CANCELLED; close() is idempotent")
        void openAfterCloseFails() {
            provider.close();
            provider.close();
            assertFailsWith(provider.openSession(sessionOptions(), context()), SearchErrorCategory.CANCELLED);
            assertEquals(0, infrastructure.getCloseCalls());
        }
    }
}
