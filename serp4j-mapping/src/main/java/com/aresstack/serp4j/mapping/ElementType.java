package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;

import java.util.Objects;
import java.util.Optional;

/**
 * The resolved type of a mapped element: either a {@link ResultType} (the element becomes an item) or a
 * {@link SerpFeatureType} (the element becomes a feature). Written as {@code item:<code>} or
 * {@code feature:<code>} in polymorphic sections. Immutable.
 */
public final class ElementType {

    private final ResultType resultType;
    private final SerpFeatureType featureType;

    private ElementType(ResultType resultType, SerpFeatureType featureType) {
        this.resultType = resultType;
        this.featureType = featureType;
    }

    public static ElementType item(ResultType type) {
        return new ElementType(Objects.requireNonNull(type, "type"), null);
    }

    public static ElementType feature(SerpFeatureType type) {
        return new ElementType(null, Objects.requireNonNull(type, "type"));
    }

    public boolean isItem() {
        return resultType != null;
    }

    public boolean isFeature() {
        return featureType != null;
    }

    public Optional<ResultType> getResultType() {
        return Optional.ofNullable(resultType);
    }

    public Optional<SerpFeatureType> getFeatureType() {
        return Optional.ofNullable(featureType);
    }

    /** The type code without prefix, for example {@code organic} or {@code knowledge_panel}. */
    public String getCode() {
        return resultType != null ? resultType.getCode() : featureType.getCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ElementType)) {
            return false;
        }
        ElementType that = (ElementType) o;
        return Objects.equals(resultType, that.resultType) && Objects.equals(featureType, that.featureType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resultType, featureType);
    }

    /** {@code item:<code>} or {@code feature:<code>}. */
    @Override
    public String toString() {
        return (isItem() ? "item:" : "feature:") + getCode();
    }
}
