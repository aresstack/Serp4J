package com.aresstack.serp4j.core.model.analysis;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.Optional;

/** Derived intent of a query: category, confidence in {@code [0,1]} and an optional rationale. */
public final class SearchIntent {

    private final IntentCategory category;
    private final double confidence;
    private final String rationale;

    private SearchIntent(IntentCategory category, double confidence, String rationale) {
        this.category = category;
        this.confidence = confidence;
        this.rationale = rationale;
    }

    public static SearchIntent of(IntentCategory category, double confidence) {
        return of(category, confidence, null);
    }

    public static SearchIntent of(IntentCategory category, double confidence, String rationale) {
        return new SearchIntent(Preconditions.requireNonNull(category, "category"),
                Preconditions.requireRange(confidence, 0, 1, "confidence"), Preconditions.trimToNull(rationale));
    }

    public IntentCategory getCategory() {
        return category;
    }

    public double getConfidence() {
        return confidence;
    }

    public Optional<String> getRationale() {
        return Optional.ofNullable(rationale);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchIntent)) {
            return false;
        }
        SearchIntent that = (SearchIntent) o;
        return category.equals(that.category)
                && Double.compare(confidence, that.confidence) == 0
                && Objects.equals(rationale, that.rationale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, confidence, rationale);
    }

    @Override
    public String toString() {
        return "SearchIntent{" + category + ", confidence=" + confidence + '}';
    }
}
