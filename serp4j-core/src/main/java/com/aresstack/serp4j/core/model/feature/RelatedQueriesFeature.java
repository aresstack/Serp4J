package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Related searches block. Type {@link SerpFeatureType#RELATED_SEARCHES}. */
public final class RelatedQueriesFeature extends AbstractSerpFeature {

    private final List<RelatedQuery> queries;

    private RelatedQueriesFeature(Builder builder) {
        super(builder);
        this.queries = Preconditions.immutableList(builder.queries, "queries");
    }

    public static Builder builder(ElementId id) {
        return new Builder(id);
    }

    @Override
    public SerpFeatureType getType() {
        return SerpFeatureType.RELATED_SEARCHES;
    }

    @Override
    public <R> R accept(SerpFeatureVisitor<R> visitor) {
        return visitor.visitRelatedQueries(this);
    }

    public List<RelatedQuery> getQueries() {
        return queries;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(this);
        builder.queries.addAll(queries);
        return builder;
    }

    @Override
    protected boolean contentEquals(AbstractSerpFeature other) {
        return queries.equals(((RelatedQueriesFeature) other).queries);
    }

    @Override
    protected int contentHashCode() {
        return queries.hashCode();
    }

    /** Builder for {@link RelatedQueriesFeature}. */
    public static final class Builder extends AbstractSerpFeature.Builder<Builder> {
        private final List<RelatedQuery> queries = new ArrayList<RelatedQuery>();

        private Builder(ElementId id) {
            super(id);
        }

        private Builder(RelatedQueriesFeature source) {
            super(source);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder query(RelatedQuery query) {
            queries.add(Preconditions.requireNonNull(query, "query"));
            return this;
        }

        public Builder query(String text) {
            return query(RelatedQuery.of(text));
        }

        public Builder queries(Collection<RelatedQuery> queries) {
            for (RelatedQuery query : Preconditions.requireNonNull(queries, "queries")) {
                query(query);
            }
            return this;
        }

        public RelatedQueriesFeature build() {
            return new RelatedQueriesFeature(this);
        }
    }
}
