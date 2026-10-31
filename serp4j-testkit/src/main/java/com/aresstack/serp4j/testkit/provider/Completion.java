package com.aresstack.serp4j.testkit.provider;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.testkit.internal.TestkitScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/** Delivers a simulated result honouring latency, deadline and cancellation; shared by all mocks. */
final class Completion {

    private Completion() {
    }

    /**
     * Completes {@code future} with {@code result} after {@code latency}, or never if {@code result} is
     * {@code null} (hang). Cancellation of the context and its deadline win if they come first; their failures are
     * produced by {@code failure}. Caller has already checked that the context is neither cancelled nor expired.
     */
    static <T> void deliver(final CompletableFuture<T> future, final Supplier<Result<T>> result, Duration latency,
                            SearchContext context, final Function<SearchErrorCategory, Throwable> failure) {
        context.getCancellation().onCancel(new Runnable() {
            @Override
            public void run() {
                future.completeExceptionally(failure.apply(SearchErrorCategory.CANCELLED));
            }
        });
        Optional<Duration> remaining = context.getRemainingTime(Instant.now());
        if (remaining.isPresent()) {
            final ScheduledFuture<?> timeout = TestkitScheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    future.completeExceptionally(failure.apply(SearchErrorCategory.TIMEOUT));
                }
            }, remaining.get());
            future.whenComplete((value, error) -> timeout.cancel(false));
        }
        if (result == null) {
            return; // hang: only cancellation, deadline or close complete the future
        }
        Runnable finish = new Runnable() {
            @Override
            public void run() {
                if (future.isDone()) {
                    return;
                }
                Result<T> outcome = result.get();
                if (outcome.failure != null) {
                    future.completeExceptionally(outcome.failure);
                } else {
                    future.complete(outcome.value);
                }
            }
        };
        if (latency.isZero()) {
            finish.run();
        } else {
            TestkitScheduler.schedule(finish, latency);
        }
    }

    /** Either a value or a failure. */
    static final class Result<T> {
        final T value;
        final Throwable failure;

        private Result(T value, Throwable failure) {
            this.value = value;
            this.failure = failure;
        }

        static <T> Result<T> success(T value) {
            return new Result<T>(value, null);
        }

        static <T> Result<T> failure(Throwable failure) {
            return new Result<T>(null, failure);
        }
    }
}
