package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** How the call was executed: timing, provider task/request identifiers, attempts, cache hit. */
public final class ExecutionInfo {

    private final Instant startedAt;
    private final Duration duration;
    private final String providerRequestId;
    private final Integer attempts;
    private final Boolean cached;

    private ExecutionInfo(Builder builder) {
        this.startedAt = builder.startedAt;
        this.duration = builder.duration;
        this.providerRequestId = builder.providerRequestId;
        this.attempts = builder.attempts;
        this.cached = builder.cached;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Instant> getStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Duration> getDuration() {
        return Optional.ofNullable(duration);
    }

    /** Provider-side identifier of the request or task, useful for support inquiries. */
    public Optional<String> getProviderRequestId() {
        return Optional.ofNullable(providerRequestId);
    }

    public OptionalInt getAttempts() {
        return attempts == null ? OptionalInt.empty() : OptionalInt.of(attempts);
    }

    public Optional<Boolean> getCached() {
        return Optional.ofNullable(cached);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExecutionInfo)) {
            return false;
        }
        ExecutionInfo that = (ExecutionInfo) o;
        return Objects.equals(startedAt, that.startedAt)
                && Objects.equals(duration, that.duration)
                && Objects.equals(providerRequestId, that.providerRequestId)
                && Objects.equals(attempts, that.attempts)
                && Objects.equals(cached, that.cached);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startedAt, duration, providerRequestId, attempts, cached);
    }

    @Override
    public String toString() {
        return "ExecutionInfo{startedAt=" + startedAt + ", duration=" + duration
                + ", providerRequestId=" + providerRequestId + ", attempts=" + attempts + ", cached=" + cached + '}';
    }

    /** Builder for {@link ExecutionInfo}. */
    public static final class Builder {
        private Instant startedAt;
        private Duration duration;
        private String providerRequestId;
        private Integer attempts;
        private Boolean cached;

        private Builder() {
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder duration(Duration duration) {
            if (duration != null && duration.isNegative()) {
                throw new IllegalArgumentException("duration must not be negative but was " + duration);
            }
            this.duration = duration;
            return this;
        }

        public Builder providerRequestId(String providerRequestId) {
            this.providerRequestId = Preconditions.trimToNull(providerRequestId);
            return this;
        }

        public Builder attempts(int attempts) {
            this.attempts = Preconditions.requirePositive(attempts, "attempts");
            return this;
        }

        public Builder cached(boolean cached) {
            this.cached = cached;
            return this;
        }

        public ExecutionInfo build() {
            return new ExecutionInfo(this);
        }
    }
}
