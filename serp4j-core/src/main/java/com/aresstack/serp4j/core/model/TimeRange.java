package com.aresstack.serp4j.core.model;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Freshness restriction of a search: either a relative period ({@link Period}) or an absolute range with an
 * open or closed start and end.
 */
public final class TimeRange {

    /** Relative periods most providers support directly. */
    public enum Period {
        DAY, WEEK, MONTH, YEAR
    }

    private final Period period;
    private final Instant from;
    private final Instant to;

    private TimeRange(Period period, Instant from, Instant to) {
        this.period = period;
        this.from = from;
        this.to = to;
    }

    /** Results from the past {@code period}. */
    public static TimeRange past(Period period) {
        return new TimeRange(Preconditions.requireNonNull(period, "period"), null, null);
    }

    public static TimeRange pastDay() {
        return past(Period.DAY);
    }

    public static TimeRange pastWeek() {
        return past(Period.WEEK);
    }

    public static TimeRange pastMonth() {
        return past(Period.MONTH);
    }

    public static TimeRange pastYear() {
        return past(Period.YEAR);
    }

    /** Results published at or after {@code from}. */
    public static TimeRange since(Instant from) {
        return new TimeRange(null, Preconditions.requireNonNull(from, "from"), null);
    }

    /** Results published at or before {@code to}. */
    public static TimeRange until(Instant to) {
        return new TimeRange(null, null, Preconditions.requireNonNull(to, "to"));
    }

    /** Results published between {@code from} and {@code to} (inclusive). */
    public static TimeRange between(Instant from, Instant to) {
        Preconditions.requireNonNull(from, "from");
        Preconditions.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        return new TimeRange(null, from, to);
    }

    public Optional<Period> getPeriod() {
        return Optional.ofNullable(period);
    }

    public Optional<Instant> getFrom() {
        return Optional.ofNullable(from);
    }

    public Optional<Instant> getTo() {
        return Optional.ofNullable(to);
    }

    public boolean isRelative() {
        return period != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TimeRange)) {
            return false;
        }
        TimeRange that = (TimeRange) o;
        return period == that.period && Objects.equals(from, that.from) && Objects.equals(to, that.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(period, from, to);
    }

    @Override
    public String toString() {
        return period != null ? "TimeRange{past " + period + '}' : "TimeRange{from=" + from + ", to=" + to + '}';
    }
}
