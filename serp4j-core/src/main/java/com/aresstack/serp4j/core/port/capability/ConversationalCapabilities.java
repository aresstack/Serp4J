package com.aresstack.serp4j.core.port.capability;

import com.aresstack.serp4j.core.annotation.Experimental;
import com.aresstack.serp4j.core.internal.Preconditions;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Limits of a conversational provider (WP2 §6, ADR-009). Present in {@link SearchCapabilities} if and only if the
 * provider implements {@code ConversationalSearchProvider}.
 */
@Experimental("conversational search stabilises with the first Google AI Mode adapter (Paket G)")
public final class ConversationalCapabilities {

    private final Integer maxTurns;
    private final Duration idleTimeout;
    private final Duration maxSessionAge;
    private final int maxConcurrentSessions;
    private final boolean resumable;

    private ConversationalCapabilities(Builder builder) {
        this.maxTurns = builder.maxTurns;
        this.idleTimeout = builder.idleTimeout;
        this.maxSessionAge = builder.maxSessionAge;
        this.maxConcurrentSessions = builder.maxConcurrentSessions;
        this.resumable = builder.resumable;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Maximum number of turns per session including the initial turn; empty = no known limit. */
    public OptionalInt getMaxTurns() {
        return maxTurns == null ? OptionalInt.empty() : OptionalInt.of(maxTurns);
    }

    /** Inactivity after which the provider expires the session. */
    public Optional<Duration> getIdleTimeout() {
        return Optional.ofNullable(idleTimeout);
    }

    public Optional<Duration> getMaxSessionAge() {
        return Optional.ofNullable(maxSessionAge);
    }

    /** Sessions that may be open at the same time per provider instance; {@code 1} = serial. */
    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }

    /** Whether a session can be resumed from a {@code SessionHandle}. */
    public boolean isResumable() {
        return resumable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConversationalCapabilities)) {
            return false;
        }
        ConversationalCapabilities that = (ConversationalCapabilities) o;
        return Objects.equals(maxTurns, that.maxTurns)
                && Objects.equals(idleTimeout, that.idleTimeout)
                && Objects.equals(maxSessionAge, that.maxSessionAge)
                && maxConcurrentSessions == that.maxConcurrentSessions
                && resumable == that.resumable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxTurns, idleTimeout, maxSessionAge, maxConcurrentSessions, resumable);
    }

    @Override
    public String toString() {
        return "ConversationalCapabilities{maxTurns=" + maxTurns + ", idleTimeout=" + idleTimeout
                + ", maxSessionAge=" + maxSessionAge + ", maxConcurrentSessions=" + maxConcurrentSessions
                + ", resumable=" + resumable + '}';
    }

    /** Builder for {@link ConversationalCapabilities}; defaults: one concurrent session, not resumable. */
    public static final class Builder {
        private Integer maxTurns;
        private Duration idleTimeout;
        private Duration maxSessionAge;
        private int maxConcurrentSessions = 1;
        private boolean resumable;

        private Builder() {
        }

        public Builder maxTurns(int maxTurns) {
            this.maxTurns = Preconditions.requirePositive(maxTurns, "maxTurns");
            return this;
        }

        public Builder idleTimeout(Duration idleTimeout) {
            this.idleTimeout = requirePositive(idleTimeout, "idleTimeout");
            return this;
        }

        public Builder maxSessionAge(Duration maxSessionAge) {
            this.maxSessionAge = requirePositive(maxSessionAge, "maxSessionAge");
            return this;
        }

        public Builder maxConcurrentSessions(int maxConcurrentSessions) {
            this.maxConcurrentSessions = Preconditions.requirePositive(maxConcurrentSessions, "maxConcurrentSessions");
            return this;
        }

        public Builder resumable(boolean resumable) {
            this.resumable = resumable;
            return this;
        }

        public ConversationalCapabilities build() {
            return new ConversationalCapabilities(this);
        }

        private static Duration requirePositive(Duration duration, String name) {
            if (duration != null && (duration.isNegative() || duration.isZero())) {
                throw new IllegalArgumentException(name + " must be positive but was " + duration);
            }
            return duration;
        }
    }
}
