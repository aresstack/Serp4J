package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.feature.SerpFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The search results proper: result items in page order and SERP features (ADR-003). Mandatory part of every
 * {@code SearchResponse}; may be empty. Element identifiers must be unique across items and features (WP1 D9).
 *
 * <p>Items inside a group (top stories, local pack, video carousel) are normal {@link SearchResultItem}s in
 * {@link #getResults()}; the group feature only references them by id. Citations of AI answers are source
 * references inside the feature, not items.</p>
 */
public final class SearchData {

    private static final SearchData EMPTY = new SearchData(
            Collections.<SearchResultItem>emptyList(), Collections.<SerpFeature>emptyList());

    private final List<SearchResultItem> results;
    private final List<SerpFeature> features;

    private SearchData(List<SearchResultItem> results, List<SerpFeature> features) {
        this.results = results;
        this.features = features;
    }

    public static SearchData empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** All result items in page order. */
    public List<SearchResultItem> getResults() {
        return results;
    }

    public List<SearchResultItem> getResults(ResultType type) {
        Preconditions.requireNonNull(type, "type");
        List<SearchResultItem> matching = new ArrayList<SearchResultItem>();
        for (SearchResultItem item : results) {
            if (item.getType().equals(type)) {
                matching.add(item);
            }
        }
        return Collections.unmodifiableList(matching);
    }

    /** Convenience for {@code getResults(ResultType.ORGANIC)}; never contains paid results. */
    public List<SearchResultItem> getOrganicResults() {
        return getResults(ResultType.ORGANIC);
    }

    public List<SerpFeature> getFeatures() {
        return features;
    }

    public <F extends SerpFeature> List<F> getFeatures(Class<F> type) {
        Preconditions.requireNonNull(type, "type");
        List<F> matching = new ArrayList<F>();
        for (SerpFeature feature : features) {
            if (type.isInstance(feature)) {
                matching.add(type.cast(feature));
            }
        }
        return Collections.unmodifiableList(matching);
    }

    public List<SerpFeature> getFeatures(SerpFeatureType type) {
        Preconditions.requireNonNull(type, "type");
        List<SerpFeature> matching = new ArrayList<SerpFeature>();
        for (SerpFeature feature : features) {
            if (feature.getType().equals(type)) {
                matching.add(feature);
            }
        }
        return Collections.unmodifiableList(matching);
    }

    /** Looks an element up by id among items and features. */
    public Optional<SearchElement> findElement(ElementId id) {
        Preconditions.requireNonNull(id, "id");
        for (SearchResultItem item : results) {
            if (item.getId().equals(id)) {
                return Optional.<SearchElement>of(item);
            }
        }
        for (SerpFeature feature : features) {
            if (feature.getId().equals(id)) {
                return Optional.<SearchElement>of(feature);
            }
        }
        return Optional.empty();
    }

    public Optional<SearchResultItem> findResult(ElementId id) {
        Preconditions.requireNonNull(id, "id");
        for (SearchResultItem item : results) {
            if (item.getId().equals(id)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public boolean isEmpty() {
        return results.isEmpty() && features.isEmpty();
    }

    public int size() {
        return results.size() + features.size();
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.results.addAll(results);
        builder.features.addAll(features);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchData)) {
            return false;
        }
        SearchData that = (SearchData) o;
        return results.equals(that.results) && features.equals(that.features);
    }

    @Override
    public int hashCode() {
        return Objects.hash(results, features);
    }

    @Override
    public String toString() {
        return "SearchData{results=" + results.size() + ", features=" + features.size() + '}';
    }

    /** Builder; {@link #build()} rejects duplicate element identifiers. */
    public static final class Builder {
        private final List<SearchResultItem> results = new ArrayList<SearchResultItem>();
        private final List<SerpFeature> features = new ArrayList<SerpFeature>();

        private Builder() {
        }

        public Builder result(SearchResultItem item) {
            results.add(Preconditions.requireNonNull(item, "item"));
            return this;
        }

        public Builder results(Collection<? extends SearchResultItem> items) {
            for (SearchResultItem item : Preconditions.requireNonNull(items, "items")) {
                result(item);
            }
            return this;
        }

        public Builder feature(SerpFeature feature) {
            features.add(Preconditions.requireNonNull(feature, "feature"));
            return this;
        }

        public Builder features(Collection<? extends SerpFeature> features) {
            for (SerpFeature feature : Preconditions.requireNonNull(features, "features")) {
                feature(feature);
            }
            return this;
        }

        /** @throws IllegalArgumentException if two elements share an {@link ElementId} */
        public SearchData build() {
            if (results.isEmpty() && features.isEmpty()) {
                return EMPTY;
            }
            Set<ElementId> seen = new HashSet<ElementId>();
            for (SearchResultItem item : results) {
                requireUnique(seen, item.getId());
            }
            for (SerpFeature feature : features) {
                requireUnique(seen, feature.getId());
            }
            return new SearchData(
                    Collections.unmodifiableList(new ArrayList<SearchResultItem>(results)),
                    Collections.unmodifiableList(new ArrayList<SerpFeature>(features)));
        }

        private static void requireUnique(Set<ElementId> seen, ElementId id) {
            if (!seen.add(id)) {
                throw new IllegalArgumentException("duplicate element id '" + id + "' in SearchData");
            }
        }
    }
}
