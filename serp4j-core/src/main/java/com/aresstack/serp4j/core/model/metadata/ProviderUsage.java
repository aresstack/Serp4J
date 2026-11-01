package com.aresstack.serp4j.core.model.metadata;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Cost and quota consumption of one provider call, as far as the provider reports it (WP1 §4.8): money as
 * {@link MonetaryAmount}, non-monetary quota units as {@link #getCredits() credits}, which may be fractional.
 */
public final class ProviderUsage {

    private final MonetaryAmount cost;
    private final BigDecimal credits;
    private final RateLimitInfo rateLimit;

    private ProviderUsage(Builder builder) {
        this.cost = builder.cost;
        this.credits = builder.credits;
        this.rateLimit = builder.rateLimit;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Returns a builder pre-filled with this instance's values. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.cost = cost;
        builder.credits = credits;
        builder.rateLimit = rateLimit;
        return builder;
    }

    public Optional<MonetaryAmount> getCost() {
        return Optional.ofNullable(cost);
    }

    /** Quota units the call consumed when the provider bills in credits rather than money; never negative. */
    public Optional<BigDecimal> getCredits() {
        return Optional.ofNullable(credits);
    }

    public Optional<RateLimitInfo> getRateLimit() {
        return Optional.ofNullable(rateLimit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderUsage)) {
            return false;
        }
        ProviderUsage that = (ProviderUsage) o;
        return Objects.equals(cost, that.cost)
                && (credits == null ? that.credits == null : that.credits != null && credits.compareTo(that.credits) == 0)
                && Objects.equals(rateLimit, that.rateLimit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cost, credits == null ? null : credits.stripTrailingZeros(), rateLimit);
    }

    @Override
    public String toString() {
        return "ProviderUsage{cost=" + cost + ", credits=" + credits + ", rateLimit=" + rateLimit + '}';
    }

    /** Builder for {@link ProviderUsage}. */
    public static final class Builder {
        private MonetaryAmount cost;
        private BigDecimal credits;
        private RateLimitInfo rateLimit;

        private Builder() {
        }

        public Builder cost(MonetaryAmount cost) {
            this.cost = cost;
            return this;
        }

        /** Quota units consumed; {@code null} clears, negative values are rejected. */
        public Builder credits(BigDecimal credits) {
            if (credits != null && credits.signum() < 0) {
                throw new IllegalArgumentException("credits must not be negative but was " + credits);
            }
            this.credits = credits;
            return this;
        }

        public Builder rateLimit(RateLimitInfo rateLimit) {
            this.rateLimit = rateLimit;
            return this;
        }

        public ProviderUsage build() {
            return new ProviderUsage(this);
        }
    }
}
