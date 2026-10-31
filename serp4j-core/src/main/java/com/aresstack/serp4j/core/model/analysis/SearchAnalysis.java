package com.aresstack.serp4j.core.model.analysis;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.ext.Extensions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Derived findings about a response (ADR-003, M13): intent, entities, topics, metrics. Never produced by a search
 * provider – always by a {@code SearchAnalyzer} afterwards – and therefore kept apart from {@code SearchData} and
 * {@code SearchMetadata}. Every analysis names at least one {@link AnalysisProvenance}.
 */
public final class SearchAnalysis {

    private final List<AnalysisProvenance> provenance;
    private final SearchIntent intent;
    private final List<EntityMention> entities;
    private final List<Topic> topics;
    private final SearchMetrics metrics;
    private final Extensions extensions;

    private SearchAnalysis(Builder builder) {
        this.provenance = Preconditions.immutableList(builder.provenance, "provenance");
        this.intent = builder.intent;
        this.entities = Preconditions.immutableList(builder.entities, "entities");
        this.topics = Preconditions.immutableList(builder.topics, "topics");
        this.metrics = builder.metrics;
        this.extensions = builder.extensions;
    }

    public static Builder builder(AnalysisProvenance provenance) {
        return new Builder(provenance);
    }

    /** All analyzers that contributed, in order; never empty. */
    public List<AnalysisProvenance> getProvenance() {
        return provenance;
    }

    public Optional<SearchIntent> getIntent() {
        return Optional.ofNullable(intent);
    }

    public List<EntityMention> getEntities() {
        return entities;
    }

    public List<Topic> getTopics() {
        return topics;
    }

    public SearchMetrics getMetrics() {
        return metrics;
    }

    public Extensions getExtensions() {
        return extensions;
    }

    /**
     * Combines two analyses of the same response: provenance, entities and topics are concatenated, metrics and
     * extensions are merged, and for the intent {@code this} wins when both are present.
     */
    public SearchAnalysis merge(SearchAnalysis other) {
        Preconditions.requireNonNull(other, "other");
        Builder builder = toBuilder();
        builder.provenance.addAll(other.provenance);
        if (builder.intent == null) {
            builder.intent = other.intent;
        }
        builder.entities.addAll(other.entities);
        builder.topics.addAll(other.topics);
        builder.metrics = metrics.merge(other.metrics);
        builder.extensions = extensions.merge(other.extensions);
        return builder.build();
    }

    public Builder toBuilder() {
        Builder builder = new Builder(provenance.get(0));
        builder.provenance.addAll(provenance.subList(1, provenance.size()));
        builder.intent = intent;
        builder.entities.addAll(entities);
        builder.topics.addAll(topics);
        builder.metrics = metrics;
        builder.extensions = extensions;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchAnalysis)) {
            return false;
        }
        SearchAnalysis that = (SearchAnalysis) o;
        return provenance.equals(that.provenance)
                && Objects.equals(intent, that.intent)
                && entities.equals(that.entities)
                && topics.equals(that.topics)
                && metrics.equals(that.metrics)
                && extensions.equals(that.extensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provenance, intent, entities, topics, metrics, extensions);
    }

    @Override
    public String toString() {
        return "SearchAnalysis{provenance=" + provenance + ", intent=" + intent + ", entities=" + entities.size()
                + ", topics=" + topics.size() + ", metrics=" + metrics + '}';
    }

    /** Builder for {@link SearchAnalysis}. */
    public static final class Builder {
        private final List<AnalysisProvenance> provenance = new ArrayList<AnalysisProvenance>();
        private SearchIntent intent;
        private final List<EntityMention> entities = new ArrayList<EntityMention>();
        private final List<Topic> topics = new ArrayList<Topic>();
        private SearchMetrics metrics = SearchMetrics.empty();
        private Extensions extensions = Extensions.empty();

        private Builder(AnalysisProvenance provenance) {
            this.provenance.add(Preconditions.requireNonNull(provenance, "provenance"));
        }

        public Builder provenance(AnalysisProvenance provenance) {
            this.provenance.add(Preconditions.requireNonNull(provenance, "provenance"));
            return this;
        }

        public Builder intent(SearchIntent intent) {
            this.intent = intent;
            return this;
        }

        public Builder entity(EntityMention entity) {
            entities.add(Preconditions.requireNonNull(entity, "entity"));
            return this;
        }

        public Builder entities(Collection<EntityMention> entities) {
            for (EntityMention entity : Preconditions.requireNonNull(entities, "entities")) {
                entity(entity);
            }
            return this;
        }

        public Builder topic(Topic topic) {
            topics.add(Preconditions.requireNonNull(topic, "topic"));
            return this;
        }

        public Builder topics(Collection<Topic> topics) {
            for (Topic topic : Preconditions.requireNonNull(topics, "topics")) {
                topic(topic);
            }
            return this;
        }

        public Builder metrics(SearchMetrics metrics) {
            this.metrics = Preconditions.requireNonNull(metrics, "metrics");
            return this;
        }

        public Builder extensions(Extensions extensions) {
            this.extensions = Preconditions.requireNonNull(extensions, "extensions");
            return this;
        }

        public SearchAnalysis build() {
            return new SearchAnalysis(this);
        }
    }
}
