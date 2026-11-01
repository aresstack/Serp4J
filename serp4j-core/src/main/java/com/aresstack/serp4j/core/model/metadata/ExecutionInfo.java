package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * How the call was executed: timing (measured by Serp4J and as reported by the provider), provider task/request
 * identifiers, the provider-side source URL, attempts, cache hit. All fields are optional (WP1 §4.8).
 */
public final class ExecutionInfo {

    private final Instant startedAt;
    private final Duration duration;
    private final Duration providerReportedDuration;
    private final String providerRequestId;
    private final String sourceUrl;
    private final Integer attempts;
    private final Instant providerTimestamp;

    private ExecutionInfo(Builder builder) {
        this.startedAt = builder.startedAt;
        this.duration = builder.duration;
        this.providerReportedDuration = builder.providerReportedDuration;
        this.providerRequestId = builder.providerRequestId;
        this.sourceUrl = builder.sourceUrl;
        this.attempts = builder.attempts;
        this.providerTimestamp = builder.providerTimestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Instant> getStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    /** Wall-clock duration of the call as measured by Serp4J. */
    public Optional<Duration> getDuration() {
        return Optional.ofNullable(duration);
    }

    /** Processing time as reported by the provider itself (e.g. DataForSEO {@code time}, Bright Data {@code search_time}). */
    public Optional<Duration> getProviderReportedDuration() {
        return Optional.ofNullable(providerReportedDuration);
    }

    /** Provider-side identifier of the request or task, useful for support inquiries. */
    public Optional<String> getProviderRequestId() {
        return Optional.ofNullable(providerRequestId);
    }

    /**
     * URL of the search result page the provider fetched or offers for verification (e.g. DataForSEO
     * {@code check_url}, Bright Data {@code input.original_url}).
     */
    public Optional<String> getSourceUrl() {
        return Optional.ofNullable(sourceUrl);
    }

    public OptionalInt getAttempts() {
        return attempts == null ? OptionalInt.empty() : OptionalInt.of(attempts);
    }

    /** Timestamp the provider attached to the response (e.g. DataForSEO {@code datetime}, Bright Data {@code general.timestamp}). */
    public Optional<Instant> getProviderTimestamp() {
        return Optional.ofNullable(providerTimestamp);
    }

    /** Returns a builder pre-populated with all values of this instance. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.startedAt = startedAt;
        builder.duration = duration;
        builder.providerReportedDuration = providerReportedDuration;
        builder.providerRequestId = providerRequestId;
        builder.sourceUrl = sourceUrl;
        builder.attempts = attempts;
        builder.providerTimestamp = providerTimestamp;
        return builder;
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
                && Objects.equals(providerReportedDuration, that.providerReportedDuration)
                && Objects.equals(providerRequestId, that.providerRequestId)
                && Objects.equals(sourceUrl, that.sourceUrl)
                && Objects.equals(attempts, that.attempts)
                && Objects.equals(providerTimestamp, that.providerTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startedAt, duration, providerReportedDuration, providerRequestId, sourceUrl, attempts,
                providerTimestamp);
    }

    /** Prints the source URL without query string and fragment, which may echo credentials. */
    @Override
    public String toString() {
        return "ExecutionInfo{startedAt=" + startedAt + ", duration=" + duration
                + ", providerReportedDuration=" + providerReportedDuration
                + ", providerRequestId=" + providerRequestId + ", sourceUrl=" + redactedSourceUrl()
                + ", attempts=" + attempts + ", providerTimestamp=" + providerTimestamp + '}';
    }

    private String redactedSourceUrl() {
        if (sourceUrl == null) {
            return null;
        }
        int cut = sourceUrl.length();
        int query = sourceUrl.indexOf('?');
        int fragment = sourceUrl.indexOf('#');
        if (query >= 0) {
            cut = query;
        }
        if (fragment >= 0 && fragment < cut) {
            cut = fragment;
        }
        return cut == sourceUrl.length() ? sourceUrl : sourceUrl.substring(0, cut) + "?...";
    }

    /** Builder for {@link ExecutionInfo}. */
    public static final class Builder {
        private Instant startedAt;
        private Duration duration;
        private Duration providerReportedDuration;
        private String providerRequestId;
        private String sourceUrl;
        private Integer attempts;
        private Instant providerTimestamp;

        private Builder() {
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        /** @throws IllegalArgumentException if {@code duration} is negative */
        public Builder duration(Duration duration) {
            this.duration = requireNonNegative(duration, "duration");
            return this;
        }

        /** @throws IllegalArgumentException if {@code providerReportedDuration} is negative */
        public Builder providerReportedDuration(Duration providerReportedDuration) {
            this.providerReportedDuration = requireNonNegative(providerReportedDuration, "providerReportedDuration");
            return this;
        }

        public Builder providerRequestId(String providerRequestId) {
            this.providerRequestId = Preconditions.trimToNull(providerRequestId);
            return this;
        }

        /** Blank values are treated as absent; surrounding whitespace is removed. */
        public Builder sourceUrl(String sourceUrl) {
            this.sourceUrl = Preconditions.trimToNull(sourceUrl);
            return this;
        }

        public Builder attempts(int attempts) {
            this.attempts = Preconditions.requirePositive(attempts, "attempts");
            return this;
        }

        public Builder providerTimestamp(Instant providerTimestamp) {
            this.providerTimestamp = providerTimestamp;
            return this;
        }

        public ExecutionInfo build() {
            return new ExecutionInfo(this);
        }

        private static Duration requireNonNegative(Duration value, String name) {
            if (value != null && value.isNegative()) {
                throw new IllegalArgumentException(name + " must not be negative but was " + value);
            }
            return value;
        }
    }
}
