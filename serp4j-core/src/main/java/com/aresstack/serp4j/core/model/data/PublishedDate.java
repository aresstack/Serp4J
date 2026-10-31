package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Publication date of a result: an {@link Instant} only when the provider value is reliably parseable, plus the
 * provider's display text ("2 days ago") as delivered. Relative texts are never guessed into instants.
 */
public final class PublishedDate {

    private final Instant instant;
    private final String displayText;

    private PublishedDate(Instant instant, String displayText) {
        this.instant = instant;
        this.displayText = displayText;
    }

    public static PublishedDate of(Instant instant) {
        return new PublishedDate(Preconditions.requireNonNull(instant, "instant"), null);
    }

    public static PublishedDate ofText(String displayText) {
        return new PublishedDate(null, Preconditions.requireText(displayText, "displayText"));
    }

    public static PublishedDate of(Instant instant, String displayText) {
        return new PublishedDate(Preconditions.requireNonNull(instant, "instant"),
                Preconditions.requireText(displayText, "displayText"));
    }

    public Optional<Instant> getInstant() {
        return Optional.ofNullable(instant);
    }

    public Optional<String> getDisplayText() {
        return Optional.ofNullable(displayText);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PublishedDate)) {
            return false;
        }
        PublishedDate that = (PublishedDate) o;
        return Objects.equals(instant, that.instant) && Objects.equals(displayText, that.displayText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instant, displayText);
    }

    @Override
    public String toString() {
        return "PublishedDate{instant=" + instant + ", displayText=" + displayText + '}';
    }
}
