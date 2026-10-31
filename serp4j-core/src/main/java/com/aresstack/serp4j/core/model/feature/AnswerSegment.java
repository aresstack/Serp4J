package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A passage of a generated answer together with the 0-based indexes of the {@link SourceReference}s
 * ({@link GeneratedAnswerFeature#getReferences()}) it cites.
 */
public final class AnswerSegment {

    private final String text;
    private final List<Integer> referenceIndexes;

    private AnswerSegment(String text, List<Integer> referenceIndexes) {
        this.text = text;
        this.referenceIndexes = referenceIndexes;
    }

    public static AnswerSegment of(String text) {
        return new AnswerSegment(Preconditions.requireText(text, "text"), Collections.<Integer>emptyList());
    }

    /** @param referenceIndexes 0-based indexes into the feature's reference list; each must be {@code >= 0} */
    public static AnswerSegment of(String text, Collection<Integer> referenceIndexes) {
        List<Integer> indexes = new ArrayList<Integer>();
        for (Integer index : Preconditions.requireNonNull(referenceIndexes, "referenceIndexes")) {
            Preconditions.requireNonNull(index, "reference index");
            indexes.add(Preconditions.requireNonNegative(index.intValue(), "reference index"));
        }
        return new AnswerSegment(Preconditions.requireText(text, "text"),
                Preconditions.immutableList(indexes, "referenceIndexes"));
    }

    public static AnswerSegment of(String text, int... referenceIndexes) {
        List<Integer> indexes = new ArrayList<Integer>();
        for (int index : Preconditions.requireNonNull(referenceIndexes, "referenceIndexes")) {
            indexes.add(index);
        }
        return of(text, indexes);
    }

    public String getText() {
        return text;
    }

    public List<Integer> getReferenceIndexes() {
        return referenceIndexes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnswerSegment)) {
            return false;
        }
        AnswerSegment that = (AnswerSegment) o;
        return text.equals(that.text) && referenceIndexes.equals(that.referenceIndexes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, referenceIndexes);
    }

    @Override
    public String toString() {
        return "AnswerSegment{text='" + text + "', references=" + referenceIndexes + '}';
    }
}
