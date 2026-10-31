package com.aresstack.serp4j.testkit.http;

import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.testkit.SharedInfrastructure;
import com.aresstack.serp4j.testkit.internal.TestkitScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory {@link HttpTransport} for adapter tests (architecture.md §10): stubs answer matching requests with
 * responses, fixtures, transport failures, delays or not at all; every request is recorded.
 *
 * <pre>{@code
 * FakeHttpTransport http = FakeHttpTransport.create()
 *         .on(RequestMatcher.post("https://api.example.invalid/task_post"))
 *             .reply(FakeHttpStep.json(200, "{\"status\":\"queued\"}"))
 *         .on(RequestMatcher.get("https://api.example.invalid/task_get"))
 *             .reply(FakeHttpStep.json(200, "{\"status\":\"pending\"}"),     // first poll
 *                    FakeHttpStep.fixture(Fixtures.load("dataforseo", "organic.json")));  // then ready
 * }</pre>
 *
 * <p>Rules: the first registered stub that matches answers. A stub plays its steps in order and repeats the last one
 * (so "pending, then ready" stays ready). A request no stub matches fails with {@link UnexpectedHttpRequestError}
 * and is recorded for {@link #assertNoUnmatchedRequests()}. Like a real transport, every status code is delivered as
 * {@link HttpResponse}; only {@link FakeHttpStep#fail} produces an {@link HttpTransportException}. A request timeout
 * ({@link HttpRequest#getTimeout()}) shorter than the step's delay, or a {@link FakeHttpStep#hang()}, fails the
 * request with {@code TIMEOUT}. {@link #close()} completes running requests with {@code CANCELLED}; later calls to
 * {@link #execute} throw {@link IllegalStateException}, as the port demands.</p>
 *
 * <p>As {@link SharedInfrastructure} it lets the ownership contract (ADR-017) check that a provider never closes the
 * transport it borrowed. Thread-safe.</p>
 */
public final class FakeHttpTransport implements HttpTransport, SharedInfrastructure {

    private final List<Stub> stubs = new CopyOnWriteArrayList<Stub>();
    private final List<HttpRequest> requests = new CopyOnWriteArrayList<HttpRequest>();
    private final List<HttpRequest> unmatched = new CopyOnWriteArrayList<HttpRequest>();
    private final Set<CompletableFuture<HttpResponse>> running =
            Collections.newSetFromMap(new ConcurrentHashMap<CompletableFuture<HttpResponse>, Boolean>());
    private final AtomicInteger closeCalls = new AtomicInteger();

    private FakeHttpTransport() {
    }

    public static FakeHttpTransport create() {
        return new FakeHttpTransport();
    }

    /** Starts a stub for requests matching {@code matcher}; complete it with {@link Stubbing#reply}. */
    public Stubbing on(RequestMatcher matcher) {
        if (matcher == null) {
            throw new NullPointerException("matcher must not be null");
        }
        return new Stubbing(matcher);
    }

    /** Shortcut for {@code on(any()).reply(...)}. */
    public FakeHttpTransport onAnyRequest(FakeHttpStep first, FakeHttpStep... more) {
        return on(RequestMatcher.any()).reply(first, more);
    }

    @Override
    public CompletableFuture<HttpResponse> execute(HttpRequest request) {
        if (request == null) {
            throw new NullPointerException("request must not be null");
        }
        if (isClosed()) {
            throw new IllegalStateException("FakeHttpTransport is closed");
        }
        requests.add(request);
        final CompletableFuture<HttpResponse> future = new CompletableFuture<HttpResponse>();
        Stub stub = find(request);
        if (stub == null) {
            unmatched.add(request);
            future.completeExceptionally(new UnexpectedHttpRequestError(request, stubs.toString()));
            return future;
        }
        FakeHttpStep step = stub.next();
        running.add(future);
        future.whenComplete((response, error) -> running.remove(future));

        final Duration timeout = request.getTimeout().orElse(null);
        boolean timesOut = timeout != null && (step.isHang() || step.getDelay().compareTo(timeout) > 0);
        if (timesOut) {
            schedule(future, new Runnable() {
                @Override
                public void run() {
                    future.completeExceptionally(new HttpTransportException(HttpTransportException.Kind.TIMEOUT,
                            "request timeout of " + timeout + " elapsed (simulated)"));
                }
            }, timeout);
            return future;
        }
        if (step.isHang()) {
            return future;
        }
        final FakeHttpStep answer = step;
        Runnable deliver = new Runnable() {
            @Override
            public void run() {
                if (answer.getFailure() != null) {
                    future.completeExceptionally(new HttpTransportException(answer.getFailure(),
                            "simulated " + answer.getFailure() + " failure"));
                } else {
                    future.complete(answer.getResponse());
                }
            }
        };
        if (step.getDelay().isZero()) {
            deliver.run();
        } else {
            schedule(future, deliver, step.getDelay());
        }
        return future;
    }

    private static void schedule(CompletableFuture<HttpResponse> future, Runnable task, Duration delay) {
        final ScheduledFuture<?> scheduled = TestkitScheduler.schedule(task, delay);
        future.whenComplete((response, error) -> scheduled.cancel(false));
    }

    private Stub find(HttpRequest request) {
        for (Stub stub : stubs) {
            if (stub.matcher.matches(request)) {
                return stub;
            }
        }
        return null;
    }

    // ----- inspection -----

    /** All executed requests in order, including unmatched ones. */
    public List<HttpRequest> getRequests() {
        return Collections.unmodifiableList(new ArrayList<HttpRequest>(requests));
    }

    /** Executed requests matching {@code matcher}. */
    public List<HttpRequest> getRequests(RequestMatcher matcher) {
        List<HttpRequest> result = new ArrayList<HttpRequest>();
        for (HttpRequest request : requests) {
            if (matcher.matches(request)) {
                result.add(request);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * The last executed request.
     *
     * @throws AssertionError if no request was executed
     */
    public HttpRequest getLastRequest() {
        if (requests.isEmpty()) {
            throw new AssertionError("FakeHttpTransport received no request");
        }
        return requests.get(requests.size() - 1);
    }

    public int getRequestCount() {
        return requests.size();
    }

    /** Requests no stub matched. */
    public List<HttpRequest> getUnmatchedRequests() {
        return Collections.unmodifiableList(new ArrayList<HttpRequest>(unmatched));
    }

    /**
     * Fails if any request was not matched by a stub.
     *
     * @throws AssertionError listing the unmatched requests (URLs redacted)
     */
    public void assertNoUnmatchedRequests() {
        if (!unmatched.isEmpty()) {
            StringBuilder message = new StringBuilder("unmatched requests:");
            for (HttpRequest request : unmatched) {
                message.append("\n  ").append(request.getMethod()).append(' ').append(request.getRedactedUrl());
            }
            throw new AssertionError(message.toString());
        }
    }

    /**
     * Fails unless exactly {@code expected} requests matched {@code matcher}.
     *
     * @throws AssertionError on a different count
     */
    public void assertRequestCount(RequestMatcher matcher, int expected) {
        int actual = getRequests(matcher).size();
        if (actual != expected) {
            throw new AssertionError("expected " + expected + " request(s) matching [" + matcher + "] but got "
                    + actual);
        }
    }

    /** Number of requests currently waiting for their answer. */
    public int getRunningCount() {
        return running.size();
    }

    // ----- SharedInfrastructure -----

    /** Number of executed requests. */
    @Override
    public int getUses() {
        return requests.size();
    }

    @Override
    public boolean isClosed() {
        return closeCalls.get() > 0;
    }

    @Override
    public int getCloseCalls() {
        return closeCalls.get();
    }

    /** Completes running requests with {@code CANCELLED}. Idempotent; only the test that created it calls this. */
    @Override
    public void close() {
        if (closeCalls.getAndIncrement() == 0) {
            for (CompletableFuture<HttpResponse> future : new ArrayList<CompletableFuture<HttpResponse>>(running)) {
                future.completeExceptionally(new HttpTransportException(HttpTransportException.Kind.CANCELLED,
                        "transport closed"));
            }
        }
    }

    @Override
    public String toString() {
        return "FakeHttpTransport{stubs=" + stubs.size() + ", requests=" + requests.size() + ", unmatched="
                + unmatched.size() + ", closed=" + isClosed() + '}';
    }

    /** Second half of {@link #on(RequestMatcher)}. */
    public final class Stubbing {
        private final RequestMatcher matcher;

        private Stubbing(RequestMatcher matcher) {
            this.matcher = matcher;
        }

        /**
         * Registers the steps; the stub plays them in order and repeats the last one.
         *
         * @return the transport, for further stubs
         */
        public FakeHttpTransport reply(FakeHttpStep first, FakeHttpStep... more) {
            if (first == null) {
                throw new NullPointerException("first must not be null");
            }
            List<FakeHttpStep> steps = new ArrayList<FakeHttpStep>();
            steps.add(first);
            for (FakeHttpStep step : Arrays.asList(more)) {
                if (step == null) {
                    throw new NullPointerException("steps must not contain null");
                }
                steps.add(step);
            }
            stubs.add(new Stub(matcher, steps));
            return FakeHttpTransport.this;
        }
    }

    private static final class Stub {
        private final RequestMatcher matcher;
        private final List<FakeHttpStep> steps;
        private final AtomicInteger calls = new AtomicInteger();

        Stub(RequestMatcher matcher, List<FakeHttpStep> steps) {
            this.matcher = matcher;
            this.steps = Collections.unmodifiableList(steps);
        }

        FakeHttpStep next() {
            return steps.get(Math.min(calls.getAndIncrement(), steps.size() - 1));
        }

        @Override
        public String toString() {
            return "[" + matcher + " -> " + steps + "]";
        }
    }
}
