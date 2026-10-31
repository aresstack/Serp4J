package com.aresstack.serp4j.testkit.provider;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchData;

import java.time.Duration;

/**
 * What a mock does with one call: deliver results, deliver a prepared response unchanged, fail with a category, or
 * never answer. Optionally after a simulated latency. Immutable.
 *
 * <pre>{@code
 * Outcome.syntheticResults(3)                                 // three generated organic hits
 * Outcome.results(data).after(Duration.ofMillis(200))          // given data, provider adds the metadata
 * Outcome.failure(SearchErrorCategory.RATE_LIMITED)            // typed failure through the future
 * Outcome.hang()                                               // completes only on cancel, deadline or close
 * }</pre>
 */
public final class Outcome {

    enum Kind { SYNTHETIC, DATA, RESPONSE, FAILURE, HANG }

    private final Kind kind;
    private final int syntheticCount;
    private final SearchData data;
    private final SearchResponse response;
    private final SearchErrorCategory category;
    private final String message;
    private final SearchProviderException exception;
    private final Duration latency;

    private Outcome(Kind kind, int syntheticCount, SearchData data, SearchResponse response,
                    SearchErrorCategory category, String message, SearchProviderException exception,
                    Duration latency) {
        this.kind = kind;
        this.syntheticCount = syntheticCount;
        this.data = data;
        this.response = response;
        this.category = category;
        this.message = message;
        this.exception = exception;
        this.latency = latency;
    }

    /**
     * Generated organic results (see {@link SyntheticResults}), capped by the request's limit; the provider adds
     * the metadata.
     *
     * @param count number of results, {@code >= 0}
     */
    public static Outcome syntheticResults(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0: " + count);
        }
        return new Outcome(Kind.SYNTHETIC, count, null, null, null, null, null, null);
    }

    /** Successful empty result: no hits, complete metadata. */
    public static Outcome empty() {
        return syntheticResults(0);
    }

    /** The given data; the provider adds metadata that names itself, engine, vertical and query (ADR-008). */
    public static Outcome results(SearchData data) {
        return new Outcome(Kind.DATA, 0, requireNonNull(data, "data"), null, null, null, null, null);
    }

    /**
     * The given response, delivered <em>unchanged</em> – also when its metadata violates the contract. Meant for
     * testing how callers cope with misbehaving providers; use {@link #results(SearchData)} otherwise.
     */
    public static Outcome response(SearchResponse response) {
        return new Outcome(Kind.RESPONSE, 0, null, requireNonNull(response, "response"), null, null, null, null);
    }

    /** Fails the future with a {@code SearchProviderException} of {@code category} for the calling provider. */
    public static Outcome failure(SearchErrorCategory category) {
        return failure(category, "simulated " + requireNonNull(category, "category").name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Fails the future with a {@code SearchProviderException} of {@code category} and {@code message}. */
    public static Outcome failure(SearchErrorCategory category, String message) {
        return new Outcome(Kind.FAILURE, 0, null, null, requireNonNull(category, "category"),
                requireNonNull(message, "message"), null, null);
    }

    /**
     * Fails the future with exactly this exception, e.g. one with {@code retryAfter} or a provider status code.
     * Inside a conversational session, use a {@code SearchSessionException} for session categories.
     */
    public static Outcome failure(SearchProviderException exception) {
        return new Outcome(Kind.FAILURE, 0, null, null, requireNonNull(exception, "exception").getCategory(),
                exception.getMessage(), exception, null);
    }

    /** Never answers by itself: the future completes only on cancellation, deadline or close of the mock. */
    public static Outcome hang() {
        return new Outcome(Kind.HANG, 0, null, null, null, null, null, null);
    }

    /** The same outcome, delivered after {@code latency} instead of the mock's default latency. */
    public Outcome after(Duration latency) {
        requireNonNull(latency, "latency");
        if (latency.isNegative()) {
            throw new IllegalArgumentException("latency must not be negative: " + latency);
        }
        return new Outcome(kind, syntheticCount, data, response, category, message, exception, latency);
    }

    Kind getKind() {
        return kind;
    }

    int getSyntheticCount() {
        return syntheticCount;
    }

    SearchData getData() {
        return data;
    }

    SearchResponse getResponse() {
        return response;
    }

    SearchErrorCategory getCategory() {
        return category;
    }

    String getMessage() {
        return message;
    }

    SearchProviderException getException() {
        return exception;
    }

    Duration latencyOr(Duration defaultLatency) {
        return latency != null ? latency : defaultLatency;
    }

    /** Whether this outcome fails the call. */
    public boolean isFailure() {
        return kind == Kind.FAILURE;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("Outcome{").append(kind);
        if (kind == Kind.SYNTHETIC) {
            text.append(' ').append(syntheticCount);
        } else if (kind == Kind.FAILURE) {
            text.append(' ').append(category);
        }
        if (latency != null) {
            text.append(", after ").append(latency);
        }
        return text.append('}').toString();
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        return value;
    }
}
