package com.aresstack.serp4j.testkit.provider;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.error.SearchSessionException;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchSessionId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.metadata.ConversationContext;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.conversation.ConversationTurn;
import com.aresstack.serp4j.core.port.conversation.SearchConversation;
import com.aresstack.serp4j.core.port.conversation.SearchConversationRequest;
import com.aresstack.serp4j.core.port.conversation.SearchSession;
import com.aresstack.serp4j.core.port.conversation.SessionHandle;
import com.aresstack.serp4j.core.port.conversation.SessionState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Session of a {@link ScriptedConversationalProvider}; implements the full {@link SearchSession#send} contract and
 * offers test hooks to simulate expiry ({@link #expire()}) and state loss ({@link #loseState()}).
 *
 * <p>State transitions: {@code OPEN} &#8594; {@code BUSY} while a turn runs &#8594; {@code ACTIVE} after a turn;
 * a failed turn returns to the previous state, except {@code SESSION_EXPIRED} ({@code EXPIRED}) and
 * {@code SESSION_LOST} ({@code FAILED}); {@link #close()} &#8594; {@code CLOSED}.</p>
 */
public final class ScriptedSearchSession implements SearchSession {

    private final ScriptedConversationalProvider provider;
    private final SearchSessionId id;
    private final Integer maxTurns;
    private final List<ConversationTurn> turns;
    private SessionState state;
    private CompletableFuture<SearchResponse> running;

    ScriptedSearchSession(ScriptedConversationalProvider provider, SearchSessionId id, List<ConversationTurn> turns,
                          Integer maxTurns) {
        this.provider = provider;
        this.id = id;
        this.maxTurns = maxTurns;
        this.turns = new ArrayList<ConversationTurn>(turns);
        this.state = turns.isEmpty() ? SessionState.OPEN : SessionState.ACTIVE;
    }

    /** Effective turn limit (provider capability and session options), {@code null} if unlimited. */
    Integer getTurnLimit() {
        return maxTurns;
    }

    @Override
    public SearchSessionId getId() {
        return id;
    }

    @Override
    public ProviderId getProviderId() {
        return provider.getDescriptor().getId();
    }

    @Override
    public SearchTarget getTarget() {
        return provider.getTarget();
    }

    @Override
    public synchronized SessionState getState() {
        return state;
    }

    @Override
    public CompletableFuture<ConversationTurn> send(final SearchConversationRequest request, SearchContext context) {
        if (request == null) {
            throw new NullPointerException("request must not be null");
        }
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }
        final CompletableFuture<SearchResponse> raw = new CompletableFuture<SearchResponse>();
        final int index;
        final SessionState previous;
        synchronized (this) {
            SearchProviderException rejection = checkSend(request, context);
            if (rejection != null) {
                return failed(rejection);
            }
            index = turns.size();
            previous = state;
            state = SessionState.BUSY;
            running = raw;
        }
        final Instant startedAt = Instant.now();
        final CompletableFuture<ConversationTurn> result = new CompletableFuture<ConversationTurn>();
        raw.whenComplete((response, error) -> {
            ConversationTurn turn = null;
            synchronized (ScriptedSearchSession.this) {
                running = null;
                if (error == null) {
                    Instant completedAt = Instant.now();
                    turn = ConversationTurn.of(index, request, response, startedAt,
                            completedAt.isBefore(startedAt) ? startedAt : completedAt);
                    turns.add(turn);
                    if (state == SessionState.BUSY) {
                        state = SessionState.ACTIVE;
                    }
                } else if (state == SessionState.BUSY) {
                    state = stateAfter(error, previous);
                }
            }
            if (error == null) {
                provider.turnCompleted(ScriptedSearchSession.this);
                result.complete(turn);
            } else {
                result.completeExceptionally(error);
            }
        });
        Outcome outcome = provider.getScript().answer(index, request);
        if (outcome == null) {
            raw.completeExceptionally(failure(SearchErrorCategory.INTERNAL, "TurnScript returned null"));
            return result;
        }
        final Outcome turnOutcome = outcome;
        Supplier<Completion.Result<SearchResponse>> supplier = outcome.getKind() == Outcome.Kind.HANG ? null
                : new Supplier<Completion.Result<SearchResponse>>() {
                    @Override
                    public Completion.Result<SearchResponse> get() {
                        return resolve(turnOutcome, index, request);
                    }
                };
        Completion.deliver(raw, supplier, outcome.latencyOr(provider.getDefaultLatency()), context,
                new Function<SearchErrorCategory, Throwable>() {
                    @Override
                    public Throwable apply(SearchErrorCategory category) {
                        return failure(category, category == SearchErrorCategory.TIMEOUT
                                ? "deadline passed" : "turn was cancelled");
                    }
                });
        return result;
    }

    private SearchProviderException checkSend(SearchConversationRequest request, SearchContext context) {
        switch (state) {
            case CLOSED:
                return failure(SearchErrorCategory.SESSION_CLOSED, "session " + id + " is closed");
            case EXPIRED:
                return failure(SearchErrorCategory.SESSION_EXPIRED, "session " + id + " has expired");
            case FAILED:
                return failure(SearchErrorCategory.SESSION_LOST, "session " + id + " lost its state");
            case BUSY:
                return failure(SearchErrorCategory.SESSION_BUSY, "a turn of session " + id + " is still running");
            default:
                break;
        }
        if (request.getExpectedTurnIndex().isPresent() && request.getExpectedTurnIndex().getAsInt() != turns.size()) {
            return failure(SearchErrorCategory.INVALID_REQUEST, "expected turn index "
                    + request.getExpectedTurnIndex().getAsInt() + " but the next turn is " + turns.size());
        }
        if (maxTurns != null && turns.size() >= maxTurns) {
            return failure(SearchErrorCategory.TURN_LIMIT_REACHED, "turn limit of " + maxTurns + " reached");
        }
        if (context.getCancellation().isCancelled()) {
            return failure(SearchErrorCategory.CANCELLED, "turn was cancelled");
        }
        if (context.isExpired(Instant.now())) {
            return failure(SearchErrorCategory.TIMEOUT, "deadline passed before the turn started");
        }
        try {
            provider.useInfrastructure();
        } catch (RuntimeException e) {
            return SearchSessionException.builder(id, getProviderId(), SearchErrorCategory.UNAVAILABLE,
                    "shared infrastructure unavailable").cause(e).build();
        }
        return null;
    }

    private static SessionState stateAfter(Throwable error, SessionState previous) {
        Optional<SearchProviderException> failure = SearchProviderException.unwrap(error);
        if (failure.isPresent()) {
            if (failure.get().getCategory() == SearchErrorCategory.SESSION_EXPIRED) {
                return SessionState.EXPIRED;
            }
            if (failure.get().getCategory() == SearchErrorCategory.SESSION_LOST) {
                return SessionState.FAILED;
            }
        }
        return previous;
    }

    private Completion.Result<SearchResponse> resolve(Outcome outcome, int index, SearchConversationRequest request) {
        switch (outcome.getKind()) {
            case FAILURE:
                return Completion.Result.<SearchResponse>failure(outcome.getException() != null
                        ? outcome.getException() : failure(outcome.getCategory(), outcome.getMessage()));
            case RESPONSE:
                return Completion.Result.success(outcome.getResponse());
            case DATA:
                return Completion.Result.success(respond(outcome.getData(), index, request));
            case SYNTHETIC:
                SearchRequest asSearch = SearchRequest.builder(request.getMessage()).target(getTarget()).build();
                return Completion.Result.success(respond(
                        SyntheticResults.organic(getProviderId(), asSearch, outcome.getSyntheticCount()), index,
                        request));
            default:
                throw new IllegalStateException("unexpected outcome " + outcome);
        }
    }

    private SearchResponse respond(SearchData data, int index, SearchConversationRequest request) {
        SearchTarget target = getTarget();
        SearchMetadata metadata = SearchMetadata
                .forProvider(getProviderId(), target.getEngine(), target.getVertical(),
                        QueryInfo.of(request.getMessage()))
                .conversation(ConversationContext.of(id, index + 1))
                .build();
        return SearchResponse.of(data, metadata);
    }

    @Override
    public synchronized SearchConversation snapshot() {
        return SearchConversation.builder(id, getProviderId(), getTarget(), state).turns(turns).build();
    }

    @Override
    public Optional<SessionHandle> getHandle() {
        return provider.isResumable()
                ? Optional.of(SessionHandle.of(getProviderId(), id, provider.tokenFor(id)))
                : Optional.<SessionHandle>empty();
    }

    /** Releases the session; a running turn completes with {@code CANCELLED}. Idempotent. */
    @Override
    public void close() {
        CompletableFuture<SearchResponse> toCancel;
        synchronized (this) {
            if (state == SessionState.CLOSED) {
                return;
            }
            state = SessionState.CLOSED;
            toCancel = running;
        }
        if (toCancel != null) {
            toCancel.completeExceptionally(failure(SearchErrorCategory.CANCELLED, "session " + id + " was closed"));
        }
        provider.sessionClosed(this);
    }

    /** Test hook: the provider expired the session; following turns fail with {@code SESSION_EXPIRED}. */
    public synchronized void expire() {
        if (!state.isTerminal()) {
            state = SessionState.EXPIRED;
        }
    }

    /** Test hook: the provider lost the session's state; following turns fail with {@code SESSION_LOST}. */
    public synchronized void loseState() {
        if (!state.isTerminal()) {
            state = SessionState.FAILED;
        }
    }

    private SearchSessionException failure(SearchErrorCategory category, String message) {
        return new SearchSessionException(id, getProviderId(), category, message);
    }

    private static CompletableFuture<ConversationTurn> failed(Throwable failure) {
        CompletableFuture<ConversationTurn> future = new CompletableFuture<ConversationTurn>();
        future.completeExceptionally(failure);
        return future;
    }

    @Override
    public synchronized String toString() {
        return "ScriptedSearchSession{" + id + ", provider=" + getProviderId() + ", state=" + state
                + ", turns=" + turns.size() + '}';
    }
}
