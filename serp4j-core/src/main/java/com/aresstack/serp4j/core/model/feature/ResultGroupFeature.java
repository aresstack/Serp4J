package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A group of result items on the page – top stories, local pack, video carousel, image block, discussions … The
 * items themselves are {@code SearchResultItem}s in {@code SearchData#getResults()}; the group only references
 * them by {@link ElementId}, so type-filtered result lists stay complete and nothing is stored twice (WP1 §4.7).
 */
public final class ResultGroupFeature extends AbstractSerpFeature {

    private final SerpFeatureType type;
    private final String title;
    private final List<ElementId> items;

    private ResultGroupFeature(Builder builder) {
        super(builder);
        this.type = builder.type;
        this.title = builder.title;
        this.items = Preconditions.immutableList(builder.items, "items");
    }

    /** @param type for example {@link SerpFeatureType#TOP_STORIES} or {@link SerpFeatureType#LOCAL_PACK} */
    public static Builder builder(ElementId id, SerpFeatureType type) {
        return new Builder(id, type);
    }

    @Override
    public SerpFeatureType getType() {
        return type;
    }

    @Override
    public <R> R accept(SerpFeatureVisitor<R> visitor) {
        return visitor.visitResultGroup(this);
    }

    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    /** Identifiers of the grouped result items, in display order. */
    public List<ElementId> getItems() {
        return items;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(this);
        builder.title = title;
        builder.items.addAll(items);
        return builder;
    }

    @Override
    protected boolean contentEquals(AbstractSerpFeature other) {
        ResultGroupFeature that = (ResultGroupFeature) other;
        return type.equals(that.type) && Objects.equals(title, that.title) && items.equals(that.items);
    }

    @Override
    protected int contentHashCode() {
        return Objects.hash(type, title, items);
    }

    /** Builder for {@link ResultGroupFeature}. */
    public static final class Builder extends AbstractSerpFeature.Builder<Builder> {
        private final SerpFeatureType type;
        private String title;
        private final List<ElementId> items = new ArrayList<ElementId>();

        private Builder(ElementId id, SerpFeatureType type) {
            super(id);
            this.type = Preconditions.requireNonNull(type, "type");
        }

        private Builder(ResultGroupFeature source) {
            super(source);
            this.type = source.type;
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder title(String title) {
            this.title = Preconditions.trimToNull(title);
            return this;
        }

        public Builder item(ElementId item) {
            items.add(Preconditions.requireNonNull(item, "item"));
            return this;
        }

        public Builder items(Collection<ElementId> items) {
            for (ElementId item : Preconditions.requireNonNull(items, "items")) {
                item(item);
            }
            return this;
        }

        public ResultGroupFeature build() {
            return new ResultGroupFeature(this);
        }
    }
}
