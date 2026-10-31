package com.aresstack.serp4j.testkit.assertion;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.error.SearchSessionException;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Static assertions for futures, failures and responses of Serp4J ports. */
public final class Serp4jAssertions {

    /** How long the assertions wait for a future by default. */
    public static final Duration DEFAULT_WAIT = Duration.ofSeconds(10);

    private Serp4jAssertions() {
    }

    /** Waits up to {@link #DEFAULT_WAIT} for the future and returns its value. */
    public static <T> T assertCompletes(CompletableFuture<T> future) {
        return assertCompletes(future, DEFAULT_WAIT);
    }

    /**
     * Waits up to {@code wait} for the future and returns its value.
     *
     * @throws AssertionError if it fails or does not complete in time
     */
    public static <T> T assertCompletes(CompletableFuture<T> future, Duration wait) {
        try {
            return future.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw failure("expected the future to complete normally, but it failed with " + e.getCause(), e.getCause());
        } catch (TimeoutException e) {
            throw failure("future did not complete within " + wait, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure("interrupted while waiting for the future", e);
        }
    }

    /** Waits up to {@link #DEFAULT_WAIT}; see {@link #assertFailsWith(CompletableFuture, SearchErrorCategory, Duration)}. */
    public static SearchProviderException assertFailsWith(CompletableFuture<?> future, SearchErrorCategory expected) {
        return assertFailsWith(future, expected, DEFAULT_WAIT);
    }

    /**
     * Asserts that the future fails – through the future, as the ports demand – with exactly one
     * {@link SearchProviderException} of the expected category, and returns it.
     *
     * @throws AssertionError if the future completes normally, fails differently, or not within {@code wait}
     */
    public static SearchProviderException assertFailsWith(CompletableFuture<?> future, SearchErrorCategory expected,
                                                          Duration wait) {
        Object value;
        try {
            value = future.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Optional<SearchProviderException> failure = SearchProviderException.unwrap(e);
            if (!failure.isPresent()) {
                throw failure("expected SearchProviderException(" + expected + ") but the future failed with "
                        + e.getCause(), e.getCause());
            }
            if (failure.get().getCategory() != expected) {
                throw failure("expected category " + expected + " but was " + failure.get().getCategory() + ": "
                        + failure.get().getMessage(), failure.get());
            }
            return failure.get();
        } catch (TimeoutException e) {
            throw failure("expected failure " + expected + ", but the future did not complete within " + wait, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure("interrupted while waiting for the future", e);
        } catch (java.util.concurrent.CancellationException e) {
            throw failure("expected SearchProviderException(" + expected + ") but the future was cancelled"
                    + " without one", e);
        }
        throw new AssertionError("expected failure " + expected + " but the future completed with " + value);
    }

    /** Like {@link #assertFailsWith(CompletableFuture, SearchErrorCategory)} and requires a session exception. */
    public static SearchSessionException assertSessionFailure(CompletableFuture<?> future,
                                                              SearchErrorCategory expected) {
        SearchProviderException failure = assertFailsWith(future, expected);
        if (!(failure instanceof SearchSessionException)) {
            throw failure("expected a SearchSessionException for " + expected + " but got " + failure, failure);
        }
        return (SearchSessionException) failure;
    }

    /**
     * Asserts that the future has not completed yet.
     *
     * @throws AssertionError if it is done
     */
    public static void assertPending(CompletableFuture<?> future) {
        if (future.isDone()) {
            throw new AssertionError("expected the future to be still running, but it is done: " + future);
        }
    }

    /**
     * Single-provider response contract (ADR-008, architecture.md §5.4): metadata is present, names {@code provider}
     * as the provider and as the only contributing provider, and names engine and vertical.
     *
     * @return the metadata
     * @throws AssertionError if any part is missing or different
     */
    public static SearchMetadata assertSingleProviderResponse(SearchResponse response, ProviderId provider) {
        if (!response.getMetadata().isPresent()) {
            throw new AssertionError("response of " + provider + " has no metadata");
        }
        SearchMetadata metadata = response.getMetadata().get();
        if (!metadata.getProvider().equals(Optional.of(provider))) {
            throw new AssertionError("metadata.provider must be " + provider + " but was " + metadata.getProvider());
        }
        if (!metadata.getContributingProviders().equals(java.util.Collections.singletonList(provider))) {
            throw new AssertionError("metadata.contributingProviders must be exactly [" + provider + "] but was "
                    + metadata.getContributingProviders());
        }
        if (!metadata.getEngine().isPresent()) {
            throw new AssertionError("metadata of " + provider + " does not name the engine actually searched");
        }
        if (!metadata.getVertical().isPresent()) {
            throw new AssertionError("metadata of " + provider + " does not name the vertical actually searched");
        }
        return metadata;
    }

    /**
     * Asserts that {@code secret} does not appear in the string form of {@code value} (architecture.md §9: secrets
     * never in {@code toString()}, exception texts, details, raw payload headers, extensions or logs).
     *
     * @param what label for the failure message
     * @throws AssertionError if the secret appears; the message does not repeat it
     */
    public static void assertNoSecret(String what, Object value, String secret) {
        if (secret == null || secret.isEmpty() || value == null) {
            return;
        }
        if (String.valueOf(value).contains(secret)) {
            throw new AssertionError(what + " exposes the secret in its string form");
        }
    }

    /** {@link #assertNoSecret(String, Object, String)} for an exception, its details and its causes. */
    public static void assertNoSecret(String what, Throwable failure, String secret) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            assertNoSecret(what + " (" + t.getClass().getSimpleName() + ")", t.toString(), secret);
            if (t instanceof SearchProviderException) {
                assertNoSecret(what + " details", ((SearchProviderException) t).getDetails(), secret);
            }
        }
    }

    private static AssertionError failure(String message, Throwable cause) {
        AssertionError error = new AssertionError(message);
        error.initCause(cause);
        return error;
    }
}
