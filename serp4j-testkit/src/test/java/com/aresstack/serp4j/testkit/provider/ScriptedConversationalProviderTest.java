package com.aresstack.serp4j.testkit.provider;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.capability.ConversationalCapabilities;
import com.aresstack.serp4j.core.port.conversation.ConversationTurn;
import com.aresstack.serp4j.core.port.conversation.SearchConversationRequest;
import com.aresstack.serp4j.core.port.conversation.SearchSession;
import com.aresstack.serp4j.core.port.conversation.SearchSessionOptions;
import com.aresstack.serp4j.core.port.conversation.SessionHandle;
import com.aresstack.serp4j.core.port.conversation.SessionState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertCompletes;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertFailsWith;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertSessionFailure;
import static org.assertj.core.api.Assertions.assertThat;

class ScriptedConversationalProviderTest {

    private static final ProviderId ID = ProviderId.of("ai-mode");
    private static final SearchTarget AI_MODE = SearchTarget.of(SearchEngineId.GOOGLE, SearchVertical.AI_MODE);
    private static final SearchSessionOptions OPTIONS = SearchSessionOptions.builder(AI_MODE).build();
    private static final SearchConversationRequest QUESTION = SearchConversationRequest.of("what is serp4j?");

    private static SearchSession open(ScriptedConversationalProvider provider) {
        return assertCompletes(provider.openSession(OPTIONS, SearchContext.defaults()));
    }

    @Test
    void secondTurnWhileFirstRunsFailsWithSessionBusy() {
        ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID)
                .latency(Duration.ofMillis(200)).build();
        SearchSession session = open(provider);

        CompletableFuture<ConversationTurn> first = session.send(QUESTION, SearchContext.defaults());
        assertThat(session.getState()).isEqualTo(SessionState.BUSY);
        assertSessionFailure(session.send(QUESTION, SearchContext.defaults()), SearchErrorCategory.SESSION_BUSY);

        assertThat(assertCompletes(first).getIndex()).isZero();
        assertThat(session.getState()).isEqualTo(SessionState.ACTIVE);
    }

    @Test
    void scriptedExpiryMovesSessionToExpired() {
        ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID)
                .script((turn, request) -> turn == 1 ? Outcome.failure(SearchErrorCategory.SESSION_EXPIRED)
                        : Outcome.syntheticResults(1))
                .build();
        SearchSession session = open(provider);
        assertCompletes(session.send(QUESTION, SearchContext.defaults()));

        assertSessionFailure(session.send(QUESTION, SearchContext.defaults()), SearchErrorCategory.SESSION_EXPIRED);

        assertThat(session.getState()).isEqualTo(SessionState.EXPIRED);
        assertSessionFailure(session.send(QUESTION, SearchContext.defaults()), SearchErrorCategory.SESSION_EXPIRED);
        assertThat(session.snapshot().getTurnCount()).isEqualTo(1);
    }

    @Test
    void ordinaryTurnFailureKeepsTheSessionUsable() {
        ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID)
                .script((turn, request) -> request.getMessage().equals("boom")
                        ? Outcome.failure(SearchErrorCategory.UNAVAILABLE) : Outcome.syntheticResults(1))
                .build();
        SearchSession session = open(provider);

        assertSessionFailure(session.send(SearchConversationRequest.of("boom"), SearchContext.defaults()),
                SearchErrorCategory.UNAVAILABLE);

        assertThat(session.getState()).isEqualTo(SessionState.OPEN);
        assertThat(assertCompletes(session.send(QUESTION, SearchContext.defaults())).getIndex()).isZero();
    }

    @Test
    void hooksSimulateExpiryAndStateLoss() {
        ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID)
                .capabilities(ConversationalCapabilities.builder().maxConcurrentSessions(2).build()).build();
        ScriptedSearchSession expired = (ScriptedSearchSession) open(provider);
        ScriptedSearchSession lost = (ScriptedSearchSession) open(provider);

        expired.expire();
        lost.loseState();

        assertSessionFailure(expired.send(QUESTION, SearchContext.defaults()), SearchErrorCategory.SESSION_EXPIRED);
        assertSessionFailure(lost.send(QUESTION, SearchContext.defaults()), SearchErrorCategory.SESSION_LOST);
        assertThat(lost.getState()).isEqualTo(SessionState.FAILED);
    }

    @Test
    void concurrentSessionLimitFailsWithRateLimited() {
        ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID).build();
        SearchSession first = open(provider);

        assertFailsWith(provider.openSession(OPTIONS, SearchContext.defaults()), SearchErrorCategory.RATE_LIMITED);

        first.close();
        open(provider);
        assertThat(provider.getOpenedSessionCount()).isEqualTo(2);
    }

    @Test
    void otherTargetFailsWithUnsupported() {
        ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID).build();
        SearchSessionOptions web = SearchSessionOptions.builder(SearchTarget.web(SearchEngineId.GOOGLE)).build();

        assertFailsWith(provider.openSession(web, SearchContext.defaults()), SearchErrorCategory.UNSUPPORTED);
    }

    @Test
    void closingTheSessionCancelsTheRunningTurn() {
        ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID)
                .script(Outcome.hang()).build();
        SearchSession session = open(provider);
        CompletableFuture<ConversationTurn> turn = session.send(QUESTION, SearchContext.defaults());

        session.close();

        assertSessionFailure(turn, SearchErrorCategory.CANCELLED);
        assertThat(session.getState()).isEqualTo(SessionState.CLOSED);
        assertThat(provider.getOpenSessions()).isEmpty();
    }

    @Test
    void resumeAfterProviderRestartFailsWithSessionLost() {
        ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID)
                .capabilities(ConversationalCapabilities.builder().resumable(true).build()).build();
        SearchSession session = open(provider);
        assertCompletes(session.send(QUESTION, SearchContext.defaults()));
        SessionHandle handle = session.getHandle().get();
        session.close();

        provider.forgetSessions();

        assertSessionFailure(provider.resumeSession(handle, SearchContext.defaults()),
                SearchErrorCategory.SESSION_LOST);
    }

    @Test
    void sessionOptionsCanLowerTheTurnLimit() {
        ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID).build();
        SearchSession session = assertCompletes(provider.openSession(
                SearchSessionOptions.builder(AI_MODE).maxTurns(1).build(), SearchContext.defaults()));
        assertCompletes(session.send(QUESTION, SearchContext.defaults()));

        assertSessionFailure(session.send(QUESTION, SearchContext.defaults()), SearchErrorCategory.TURN_LIMIT_REACHED);
    }

    @Test
    void resumedSessionKeepsTheTurnLimitOfItsOptions() {
        ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID)
                .capabilities(ConversationalCapabilities.builder().resumable(true).build()).build();
        SearchSession session = assertCompletes(provider.openSession(
                SearchSessionOptions.builder(AI_MODE).maxTurns(1).build(), SearchContext.defaults()));
        assertCompletes(session.send(QUESTION, SearchContext.defaults()));
        SessionHandle handle = session.getHandle().get();
        session.close();

        SearchSession resumed = assertCompletes(provider.resumeSession(handle, SearchContext.defaults()));

        assertSessionFailure(resumed.send(QUESTION, SearchContext.defaults()), SearchErrorCategory.TURN_LIMIT_REACHED);
    }

    @Test
    void throwingScriptFailsTheTurnAndLeavesTheSessionUsable() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID)
                .script((turn, request) -> {
                    if (calls.getAndIncrement() == 0) {
                        throw new IllegalStateException("script bug");
                    }
                    return Outcome.syntheticResults(1);
                }).build();
        SearchSession session = open(provider);

        assertSessionFailure(session.send(QUESTION, SearchContext.defaults()), SearchErrorCategory.INTERNAL);

        assertThat(session.getState()).isNotEqualTo(SessionState.BUSY);
        assertThat(assertCompletes(session.send(QUESTION, SearchContext.defaults())).getIndex()).isZero();
    }

    @Test
    void resumingTwiceKeepsOneLiveSessionPerConversation() {
        ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID)
                .capabilities(ConversationalCapabilities.builder().resumable(true).maxConcurrentSessions(2).build())
                .build();
        SearchSession session = open(provider);
        SessionHandle handle = session.getHandle().get();
        session.close();

        SearchSession first = assertCompletes(provider.resumeSession(handle, SearchContext.defaults()));
        SearchSession second = assertCompletes(provider.resumeSession(handle, SearchContext.defaults()));

        assertThat(first.getState()).isEqualTo(SessionState.CLOSED);
        assertThat(provider.getOpenSessions()).containsExactly((ScriptedSearchSession) second);
    }

    @Test
    void concurrentResumesNeverLeaveTwoLiveSessions() throws Exception {
        for (int round = 0; round < 200; round++) {
            ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID)
                    .capabilities(ConversationalCapabilities.builder().resumable(true).maxConcurrentSessions(4)
                            .build()).build();
            SearchSession session = open(provider);
            SessionHandle handle = session.getHandle().get();
            session.close();
            java.util.List<CompletableFuture<SearchSession>> resumes = race(4,
                    () -> provider.resumeSession(handle, SearchContext.defaults()));

            long live = resumes.stream().map(CompletableFuture::join)
                    .filter(s -> s.getState() != SessionState.CLOSED).count();
            assertThat(live).isEqualTo(1);
            assertThat(provider.getOpenSessions()).hasSize(1);
        }
    }

    @Test
    void sessionsOpenedConcurrentlyWithCloseAreClosedOrRefused() throws Exception {
        for (int round = 0; round < 200; round++) {
            ScriptedConversationalProvider provider = ScriptedConversationalProvider.builder(ID)
                    .capabilities(ConversationalCapabilities.builder().maxConcurrentSessions(8).build()).build();
            java.util.List<CompletableFuture<SearchSession>> opens = race(4, () -> {
                if (Thread.currentThread().getName().endsWith("-0")) {
                    provider.close();
                    return CompletableFuture.<SearchSession>completedFuture(null);
                }
                return provider.openSession(OPTIONS, SearchContext.defaults());
            });

            for (CompletableFuture<SearchSession> open : opens) {
                SearchSession opened = open.handle((s, e) -> s).join();
                if (opened != null) {
                    assertThat(opened.getState()).isEqualTo(SessionState.CLOSED);
                }
            }
            assertThat(provider.getOpenSessions()).isEmpty();
        }
    }

    /** Runs {@code action} on {@code threads} threads released at the same moment. */
    private static <T> java.util.List<T> race(int threads, java.util.concurrent.Callable<T> action)
            throws Exception {
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threads);
        java.util.List<java.util.concurrent.FutureTask<T>> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(() -> {
                barrier.await();
                return action.call();
            });
            tasks.add(task);
            new Thread(task, "race-" + i).start();
        }
        java.util.List<T> results = new java.util.ArrayList<>();
        for (java.util.concurrent.FutureTask<T> task : tasks) {
            results.add(task.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        return results;
    }
}
