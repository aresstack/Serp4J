package com.aresstack.serp4j.core.port;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * How a search is executed, as opposed to what is searched (WP2 E9): deadline, cancellation, correlation id for
 * logs and traces, budget and free attributes. Not part of the domain model and never persisted with a response.
 */
public final class SearchContext {

    private static final SearchContext DEFAULTS = builder().build();

    private final Instant deadline;
    private final CancellationSignal cancellation;
    private final String correlationId;
    private final SearchBudget budget;
    private final Map<String, String> attributes;

    private SearchContext(Builder builder) {
        this.deadline = builder.deadline;
        this.cancellation = builder.cancellation;
        this.correlationId = builder.correlationId != null ? builder.correlationId : UUID.randomUUID().toString();
        this.budget = builder.budget;
        this.attributes = Preconditions.immutableMap(builder.attributes, "attributes");
    }

    /** No deadline, never cancelled, generated correlation id, no budget. */
    public static SearchContext defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Absolute point in time after which the result is worthless; empty = provider default. */
    public Optional<Instant> getDeadline() {
        return Optional.ofNullable(deadline);
    }

    /** Time left until the deadline as seen at {@code now}; zero when passed, empty without deadline. */
    public Optional<Duration> getRemainingTime(Instant now) {
        Preconditions.requireNonNull(now, "now");
        if (deadline == null) {
            return Optional.empty();
        }
        Duration remaining = Duration.between(now, deadline);
        return Optional.of(remaining.isNegative() ? Duration.ZERO : remaining);
    }

    public boolean isExpired(Instant now) {
        return deadline != null && !Preconditions.requireNonNull(now, "now").isBefore(deadline);
    }

    /** Never null; {@link CancellationSignal#NONE} when nothing can cancel. */
    public CancellationSignal getCancellation() {
        return cancellation;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Optional<SearchBudget> getBudget() {
        return Optional.ofNullable(budget);
    }

    /** Free, unmodifiable attributes for logging and tracing; no secrets. */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    /** Derived context with the earlier of the current deadline and {@code deadline}; everything else shared. */
    public SearchContext withDeadline(Instant deadline) {
        Preconditions.requireNonNull(deadline, "deadline");
        Instant effective = this.deadline == null || deadline.isBefore(this.deadline) ? deadline : this.deadline;
        return toBuilder().deadline(effective).build();
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.deadline = deadline;
        builder.cancellation = cancellation;
        builder.correlationId = correlationId;
        builder.budget = budget;
        builder.attributes.putAll(attributes);
        return builder;
    }

    @Override
    public String toString() {
        return "SearchContext{correlationId='" + correlationId + "', deadline=" + deadline
                + ", cancelled=" + cancellation.isCancelled() + ", budget=" + (budget != null) + '}';
    }

    /** Builder for {@link SearchContext}. */
    public static final class Builder {
        private Instant deadline;
        private CancellationSignal cancellation = CancellationSignal.NONE;
        private String correlationId;
        private SearchBudget budget;
        private final Map<String, String> attributes = new LinkedHashMap<String, String>();

        private Builder() {
        }

        public Builder deadline(Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        /** Deadline relative to {@code now}. */
        public Builder timeout(Instant now, Duration timeout) {
            Preconditions.requireNonNull(now, "now");
            Preconditions.requireNonNull(timeout, "timeout");
            if (timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must not be negative but was " + timeout);
            }
            this.deadline = now.plus(timeout);
            return this;
        }

        public Builder cancellation(CancellationSignal cancellation) {
            this.cancellation = Preconditions.requireNonNull(cancellation, "cancellation");
            return this;
        }

        /** Correlation id for logs and traces; generated when not set. */
        public Builder correlationId(String correlationId) {
            this.correlationId = Preconditions.trimToNull(correlationId);
            return this;
        }

        public Builder budget(SearchBudget budget) {
            this.budget = budget;
            return this;
        }

        public Builder attribute(String name, String value) {
            attributes.put(Preconditions.requireText(name, "name"), Preconditions.requireNonNull(value, "value"));
            return this;
        }

        public SearchContext build() {
            return new SearchContext(this);
        }
    }
}
