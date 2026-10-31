package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Cost and quota consumption of one provider call, as far as the provider reports it. */
public final class ProviderUsage {

    private final MonetaryAmount cost;
    private final Long creditsUsed;
    private final RateLimitInfo rateLimit;

    private ProviderUsage(Builder builder) {
        this.cost = builder.cost;
        this.creditsUsed = builder.creditsUsed;
        this.rateLimit = builder.rateLimit;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<MonetaryAmount> getCost() {
        return Optional.ofNullable(cost);
    }

    public OptionalLong getCreditsUsed() {
        return creditsUsed == null ? OptionalLong.empty() : OptionalLong.of(creditsUsed);
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
                && Objects.equals(creditsUsed, that.creditsUsed)
                && Objects.equals(rateLimit, that.rateLimit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cost, creditsUsed, rateLimit);
    }

    @Override
    public String toString() {
        return "ProviderUsage{cost=" + cost + ", creditsUsed=" + creditsUsed + ", rateLimit=" + rateLimit + '}';
    }

    /** Builder for {@link ProviderUsage}. */
    public static final class Builder {
        private MonetaryAmount cost;
        private Long creditsUsed;
        private RateLimitInfo rateLimit;

        private Builder() {
        }

        public Builder cost(MonetaryAmount cost) {
            this.cost = cost;
            return this;
        }

        public Builder creditsUsed(long creditsUsed) {
            this.creditsUsed = Preconditions.requireNonNegative(creditsUsed, "creditsUsed");
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
