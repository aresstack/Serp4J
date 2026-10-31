package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/** Rating attached to a result: value, optional maximum and number of votes. */
public final class Rating {

    private final double value;
    private final Double max;
    private final Long votes;

    private Rating(double value, Double max, Long votes) {
        this.value = value;
        this.max = max;
        this.votes = votes;
    }

    public static Rating of(double value) {
        return new Rating(Preconditions.requireRange(value, 0, Double.MAX_VALUE, "value"), null, null);
    }

    public static Rating of(double value, double max) {
        Preconditions.requireRange(value, 0, Double.MAX_VALUE, "value");
        Preconditions.requireRange(max, value, Double.MAX_VALUE, "max");
        return new Rating(value, max, null);
    }

    public static Rating of(double value, double max, long votes) {
        Preconditions.requireRange(value, 0, Double.MAX_VALUE, "value");
        Preconditions.requireRange(max, value, Double.MAX_VALUE, "max");
        return new Rating(value, max, Preconditions.requireNonNegative(votes, "votes"));
    }

    public double getValue() {
        return value;
    }

    public OptionalDouble getMax() {
        return max == null ? OptionalDouble.empty() : OptionalDouble.of(max);
    }

    public OptionalLong getVotes() {
        return votes == null ? OptionalLong.empty() : OptionalLong.of(votes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Rating)) {
            return false;
        }
        Rating that = (Rating) o;
        return Double.compare(value, that.value) == 0
                && Objects.equals(max, that.max)
                && Objects.equals(votes, that.votes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, max, votes);
    }

    @Override
    public String toString() {
        return "Rating{" + value + (max == null ? "" : "/" + max) + (votes == null ? "" : ", votes=" + votes) + '}';
    }
}
