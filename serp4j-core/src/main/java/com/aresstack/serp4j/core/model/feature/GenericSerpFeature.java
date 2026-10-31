package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.Objects;
import java.util.Optional;

/**
 * Any SERP block without a typed representation (ADR-006, M8): keeps the provider's <em>original</em> type code
 * and the complete content as a {@link StructuredValue}, so unknown elements are never dropped.
 */
public final class GenericSerpFeature extends AbstractSerpFeature {

    private final SerpFeatureType type;
    private final String title;
    private final StructuredValue content;

    private GenericSerpFeature(Builder builder) {
        super(builder);
        this.type = builder.type;
        this.title = builder.title;
        this.content = builder.content;
    }

    public static Builder builder(ElementId id, SerpFeatureType type, StructuredValue content) {
        return new Builder(id, type, content);
    }

    @Override
    public SerpFeatureType getType() {
        return type;
    }

    @Override
    public <R> R accept(SerpFeatureVisitor<R> visitor) {
        return visitor.visitGeneric(this);
    }

    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    public StructuredValue getContent() {
        return content;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(this);
        builder.title = title;
        return builder;
    }

    @Override
    protected boolean contentEquals(AbstractSerpFeature other) {
        GenericSerpFeature that = (GenericSerpFeature) other;
        return type.equals(that.type) && Objects.equals(title, that.title) && content.equals(that.content);
    }

    @Override
    protected int contentHashCode() {
        return Objects.hash(type, title, content);
    }

    /** Builder for {@link GenericSerpFeature}. */
    public static final class Builder extends AbstractSerpFeature.Builder<Builder> {
        private final SerpFeatureType type;
        private String title;
        private final StructuredValue content;

        private Builder(ElementId id, SerpFeatureType type, StructuredValue content) {
            super(id);
            this.type = Preconditions.requireNonNull(type, "type");
            this.content = Preconditions.requireNonNull(content, "content");
        }

        private Builder(GenericSerpFeature source) {
            super(source);
            this.type = source.type;
            this.content = source.content;
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder title(String title) {
            this.title = Preconditions.trimToNull(title);
            return this;
        }

        public GenericSerpFeature build() {
            return new GenericSerpFeature(this);
        }
    }
}
