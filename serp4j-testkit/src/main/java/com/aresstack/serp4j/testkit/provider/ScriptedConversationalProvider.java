package com.aresstack.serp4j.testkit.provider;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchSessionId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.port.ProviderDescriptor;
import com.aresstack.serp4j.core.port.ProviderKind;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.capability.ConversationalCapabilities;
import com.aresstack.serp4j.core.port.capability.ResponseFeature;
import com.aresstack.serp4j.core.port.capability.SearchCapabilities;
import com.aresstack.serp4j.core.port.conversation.ConversationTurn;
import com.aresstack.serp4j.core.port.conversation.ConversationalSearchProvider;
import com.aresstack.serp4j.core.port.conversation.SearchConversationRequest;
import com.aresstack.serp4j.core.port.conversation.SearchSession;
import com.aresstack.serp4j.core.port.conversation.SearchSessionOptions;
import com.aresstack.serp4j.core.port.conversation.SessionHandle;
import com.aresstack.serp4j.testkit.FakeSharedResource;
import com.aresstack.serp4j.testkit.SharedInfrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link ConversationalSearchProvider} mock (ADR-009): opens {@link ScriptedSearchSession}s whose turns are answered
 * by a {@link TurnScript}. The sessions enforce the complete session contract – {@code SESSION_BUSY},
 * {@code expectedTurnIndex}, turn limit, {@code SESSION_CLOSED}, expiry, state loss – so orchestration and host code
 * can be tested against it, and so it serves as the reference for {@code ConversationalSearchProviderContractTest}.
 *
 * <pre>{@code
 * ScriptedConversationalProvider aiMode = ScriptedConversationalProvider.builder(ProviderId.of("ai-mode"))
 *         .capabilities(ConversationalCapabilities.builder().maxTurns(3).resumable(true).build())
 *         .script((turn, request) -> turn == 2 ? Outcome.failure(SearchErrorCategory.SESSION_EXPIRED)
 *                                              : Outcome.syntheticResults(1))
 *         .build();
 * }</pre>
 *
 * <p>Defaults: target Google / {@code ai_mode}, one concurrent session, not resumable, no turn limit, every turn
 * answered with one synthetic result. Resuming works only for resumable capabilities and for sessions this provider
 * still knows; {@link #forgetSessions()} simulates a provider restart after which resume fails with
 * {@code SESSION_LOST}.</p>
 */
public final class ScriptedConversationalProvider implements ConversationalSearchProvider {

    private final ProviderDescriptor descriptor;
    private final SearchTarget target;
    private final ConversationalCapabilities conversational;
    private final SharedInfrastructure infrastructure;
    private final Duration defaultLatency;
    private final TurnScript script;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger closeCalls = new AtomicInteger();
    private final AtomicInteger openedSessions = new AtomicInteger();
    private final Map<SearchSessionId, ScriptedSearchSession> openSessions =
            new ConcurrentHashMap<SearchSessionId, ScriptedSearchSession>();
    /** Effective turn limit per known conversation; {@code 0} means unlimited. */
    private final Map<SearchSessionId, Integer> turnLimits = new ConcurrentHashMap<SearchSessionId, Integer>();
    private final Map<SearchSessionId, List<ConversationTurn>> knownConversations =
            new ConcurrentHashMap<SearchSessionId, List<ConversationTurn>>();

    private ScriptedConversationalProvider(Builder builder) {
        this.target = builder.target;
        this.conversational = builder.conversational;
        this.descriptor = ProviderDescriptor.builder(builder.id, builder.providerType, ProviderKind.MOCK,
                        SearchCapabilities.builder()
                                .target(target)
                                .feature(com.aresstack.serp4j.core.model.SearchFeature.QUERY_TEXT)
                                .responseFeatures(ResponseFeature.ORGANIC_RESULTS)
                                .conversational(conversational)
                                .build())
                .displayName(builder.displayName != null ? builder.displayName
                        : "Scripted conversational provider " + builder.id)
                .build();
        this.infrastructure = builder.infrastructure;
        this.defaultLatency = builder.latency;
        this.script = builder.script;
    }

    public static Builder builder(ProviderId id) {
        return new Builder(id);
    }

    @Override
    public ProviderDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public CompletableFuture<SearchSession> openSession(SearchSessionOptions options, SearchContext context) {
        if (options == null) {
            throw new NullPointerException("options must not be null");
        }
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }
        SearchProviderException rejection = checkOpen(context);
        if (rejection == null && !options.getTarget().equals(target)) {
            rejection = failure(SearchErrorCategory.UNSUPPORTED,
                    "target " + options.getTarget() + " is not served; supported: " + target);
        }
        if (rejection != null) {
            return failed(rejection);
        }
        Integer maxTurns = minTurns(options);
        ScriptedSearchSession session = new ScriptedSearchSession(this, SearchSessionId.random(),
                Collections.<ConversationTurn>emptyList(), maxTurns);
        SearchProviderException refused = register(session, false);
        if (refused != null) {
            return failed(refused);
        }
        openedSessions.incrementAndGet();
        return CompletableFuture.<SearchSession>completedFuture(session);
    }

    @Override
    public CompletableFuture<SearchSession> resumeSession(SessionHandle handle, SearchContext context) {
        if (handle == null) {
            throw new NullPointerException("handle must not be null");
        }
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }
        if (!conversational.isResumable()) {
            return failed(failure(SearchErrorCategory.UNSUPPORTED, "provider " + descriptor.getId()
                    + " does not support resuming sessions"));
        }
        SearchProviderException rejection = checkOpen(context);
        if (rejection != null) {
            return failed(rejection);
        }
        if (!handle.getProviderId().equals(descriptor.getId())) {
            return failed(failure(SearchErrorCategory.INVALID_REQUEST,
                    "handle belongs to provider " + handle.getProviderId()));
        }
        List<ConversationTurn> turns = knownConversations.get(handle.getSessionId());
        Integer storedLimit = turnLimits.get(handle.getSessionId());
        if (turns == null || !handle.getOpaqueToken().equals(tokenFor(handle.getSessionId()))) {
            return failed(new com.aresstack.serp4j.core.error.SearchSessionException(handle.getSessionId(),
                    descriptor.getId(), SearchErrorCategory.SESSION_LOST,
                    "session " + handle.getSessionId() + " is unknown to the provider"));
        }
        Integer limit = storedLimit == null || storedLimit == 0 ? null : storedLimit;
        ScriptedSearchSession session = new ScriptedSearchSession(this, handle.getSessionId(), turns, limit);
        SearchProviderException refused = register(session, true);
        if (refused != null) {
            return failed(refused);
        }
        return CompletableFuture.<SearchSession>completedFuture(session);
    }

    private SearchProviderException checkOpen(SearchContext context) {
        if (closed.get()) {
            return failure(SearchErrorCategory.CANCELLED, "provider " + descriptor.getId() + " is closed");
        }
        if (context.getCancellation().isCancelled()) {
            return failure(SearchErrorCategory.CANCELLED, "cancelled");
        }
        if (context.isExpired(Instant.now())) {
            return failure(SearchErrorCategory.TIMEOUT, "deadline passed");
        }
        try {
            useInfrastructure();
        } catch (RuntimeException e) {
            return SearchProviderException.builder(descriptor.getId(), SearchErrorCategory.UNAVAILABLE,
                    "shared infrastructure unavailable").cause(e).build();
        }
        return null;
    }

    /**
     * Registers {@code session} atomically with {@link #close()} and with other opens/resumes: a provider closed in
     * the meantime refuses it with {@code CANCELLED}, otherwise {@link #close()} is guaranteed to see it. With
     * {@code replace}, a live session object of the same conversation is taken out in the same step and closed
     * afterwards, so there is never more than one per conversation.
     *
     * @return {@code null} on success, otherwise the failure to report
     */
    private SearchProviderException register(ScriptedSearchSession session, boolean replace) {
        ScriptedSearchSession previous = null;
        synchronized (this) {
            if (closed.get()) {
                return failure(SearchErrorCategory.CANCELLED, "provider " + descriptor.getId() + " is closed");
            }
            if (replace) {
                previous = openSessions.remove(session.getId());
            }
            if (openSessions.size() >= conversational.getMaxConcurrentSessions()) {
                if (previous != null) {
                    openSessions.put(previous.getId(), previous);
                }
                return failure(SearchErrorCategory.RATE_LIMITED, "concurrent-session limit of "
                        + conversational.getMaxConcurrentSessions() + " reached");
            }
            openSessions.put(session.getId(), session);
            knownConversations.put(session.getId(), session.snapshot().getTurns());
            turnLimits.put(session.getId(), session.getTurnLimit() == null ? 0 : session.getTurnLimit());
        }
        if (previous != null) {
            previous.close(); // removes nothing: the map already holds the new session
        }
        return null;
    }

    private Integer minTurns(SearchSessionOptions options) {
        Integer limit = conversational.getMaxTurns().isPresent() ? conversational.getMaxTurns().getAsInt() : null;
        if (options.getMaxTurns().isPresent()) {
            int requested = options.getMaxTurns().getAsInt();
            limit = limit == null ? requested : Math.min(limit, requested);
        }
        return limit;
    }

    void useInfrastructure() {
        if (infrastructure == null) {
            return;
        }
        if (infrastructure instanceof FakeSharedResource) {
            ((FakeSharedResource) infrastructure).use();
        } else if (infrastructure.isClosed()) {
            throw new IllegalStateException(infrastructure + " is closed");
        }
    }

    // ----- callbacks from sessions -----

    void sessionClosed(ScriptedSearchSession session) {
        openSessions.remove(session.getId(), session);
    }

    void turnCompleted(ScriptedSearchSession session) {
        knownConversations.put(session.getId(), session.snapshot().getTurns());
    }

    SearchTarget getTarget() {
        return target;
    }

    TurnScript getScript() {
        return script;
    }

    Duration getDefaultLatency() {
        return defaultLatency;
    }

    String tokenFor(SearchSessionId sessionId) {
        return "scripted:" + descriptor.getId().getCode() + ":" + sessionId.getValue();
    }

    boolean isResumable() {
        return conversational.isResumable();
    }

    // ----- test hooks -----

    /** Simulates a provider restart: all conversations are forgotten, so resuming fails with {@code SESSION_LOST}. */
    public void forgetSessions() {
        knownConversations.clear();
        turnLimits.clear();
    }

    /** Sessions opened or resumed and not yet closed. */
    public List<ScriptedSearchSession> getOpenSessions() {
        return Collections.unmodifiableList(new ArrayList<ScriptedSearchSession>(openSessions.values()));
    }

    /** Number of sessions opened through {@link #openSession} (resumes not counted). */
    public int getOpenedSessionCount() {
        return openedSessions.get();
    }

    public boolean isClosed() {
        return closed.get();
    }

    public int getCloseCalls() {
        return closeCalls.get();
    }

    /**
     * Closes all still-open sessions (their running turns complete with {@code CANCELLED}); never the borrowed
     * infrastructure (ADR-017). Idempotent.
     */
    @Override
    public void close() {
        closeCalls.incrementAndGet();
        List<ScriptedSearchSession> sessions;
        synchronized (this) { // pairs with register(): every registered session is in this snapshot
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            sessions = new ArrayList<ScriptedSearchSession>(openSessions.values());
        }
        for (ScriptedSearchSession session : sessions) {
            session.close();
        }
    }

    private SearchProviderException failure(SearchErrorCategory category, String message) {
        return new SearchProviderException(descriptor.getId(), category, message);
    }

    private static CompletableFuture<SearchSession> failed(Throwable failure) {
        CompletableFuture<SearchSession> future = new CompletableFuture<SearchSession>();
        future.completeExceptionally(failure);
        return future;
    }

    @Override
    public String toString() {
        return "ScriptedConversationalProvider{" + descriptor.getId() + ", closed=" + closed.get() + '}';
    }

    /** Builder. */
    public static final class Builder {
        private final ProviderId id;
        private String providerType = "mock";
        private String displayName;
        private SearchTarget target = SearchTarget.of(SearchEngineId.GOOGLE, SearchVertical.AI_MODE);
        private ConversationalCapabilities conversational = ConversationalCapabilities.builder().build();
        private SharedInfrastructure infrastructure;
        private Duration latency = Duration.ZERO;
        private TurnScript script = new TurnScript() {
            @Override
            public Outcome answer(int turnIndex, SearchConversationRequest request) {
                return Outcome.syntheticResults(1);
            }
        };

        private Builder(ProviderId id) {
            if (id == null) {
                throw new NullPointerException("id must not be null");
            }
            this.id = id;
        }

        public Builder providerType(String providerType) {
            if (providerType == null) {
                throw new NullPointerException("providerType must not be null");
            }
            this.providerType = providerType;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** The one target sessions run on; default Google / {@code ai_mode}. */
        public Builder target(SearchTarget target) {
            if (target == null) {
                throw new NullPointerException("target must not be null");
            }
            this.target = target;
            return this;
        }

        /** Turn limit, concurrency limit, resumability; default one session, not resumable, unlimited turns. */
        public Builder capabilities(ConversationalCapabilities conversational) {
            if (conversational == null) {
                throw new NullPointerException("conversational must not be null");
            }
            this.conversational = conversational;
            return this;
        }

        /** Borrowed infrastructure (ADR-017), used once per opened session and per turn, never closed. */
        public Builder infrastructure(SharedInfrastructure infrastructure) {
            this.infrastructure = infrastructure;
            return this;
        }

        /** Latency of turns whose outcome names none; default zero. */
        public Builder latency(Duration latency) {
            if (latency == null) {
                throw new NullPointerException("latency must not be null");
            }
            if (latency.isNegative()) {
                throw new IllegalArgumentException("latency must not be negative: " + latency);
            }
            this.latency = latency;
            return this;
        }

        /** Answers every turn with {@code outcome}. */
        public Builder script(final Outcome outcome) {
            if (outcome == null) {
                throw new NullPointerException("outcome must not be null");
            }
            return script(new TurnScript() {
                @Override
                public Outcome answer(int turnIndex, SearchConversationRequest request) {
                    return outcome;
                }
            });
        }

        /** Chooses the outcome per turn. */
        public Builder script(TurnScript script) {
            if (script == null) {
                throw new NullPointerException("script must not be null");
            }
            this.script = script;
            return this;
        }

        public ScriptedConversationalProvider build() {
            return new ScriptedConversationalProvider(this);
        }
    }
}
