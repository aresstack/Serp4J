package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Rate-limit state reported by a provider (remaining requests, limit, reset time). */
public final class RateLimitInfo {

    private final Long limit;
    private final Long remaining;
    private final Instant resetAt;

    private RateLimitInfo(Builder builder) {
        this.limit = builder.limit;
        this.remaining = builder.remaining;
        this.resetAt = builder.resetAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Returns a builder pre-filled with this instance's values. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.limit = limit;
        builder.remaining = remaining;
        builder.resetAt = resetAt;
        return builder;
    }

    public OptionalLong getLimit() {
        return limit == null ? OptionalLong.empty() : OptionalLong.of(limit);
    }

    public OptionalLong getRemaining() {
        return remaining == null ? OptionalLong.empty() : OptionalLong.of(remaining);
    }

    public Optional<Instant> getResetAt() {
        return Optional.ofNullable(resetAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RateLimitInfo)) {
            return false;
        }
        RateLimitInfo that = (RateLimitInfo) o;
        return Objects.equals(limit, that.limit)
                && Objects.equals(remaining, that.remaining)
                && Objects.equals(resetAt, that.resetAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(limit, remaining, resetAt);
    }

    @Override
    public String toString() {
        return "RateLimitInfo{limit=" + limit + ", remaining=" + remaining + ", resetAt=" + resetAt + '}';
    }

    /** Builder for {@link RateLimitInfo}. */
    public static final class Builder {
        private Long limit;
        private Long remaining;
        private Instant resetAt;

        private Builder() {
        }

        public Builder limit(long limit) {
            this.limit = Preconditions.requireNonNegative(limit, "limit");
            return this;
        }

        public Builder remaining(long remaining) {
            this.remaining = Preconditions.requireNonNegative(remaining, "remaining");
            return this;
        }

        public Builder resetAt(Instant resetAt) {
            this.resetAt = resetAt;
            return this;
        }

        public RateLimitInfo build() {
            return new RateLimitInfo(this);
        }
    }
}
