package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.Objects;

/** Featured snippet: a quoted passage with its source. Type {@link SerpFeatureType#FEATURED_SNIPPET}. */
public final class FeaturedSnippetFeature extends AbstractSerpFeature {

    private final String text;
    private final SourceReference source;

    private FeaturedSnippetFeature(Builder builder) {
        super(builder);
        this.text = builder.text;
        this.source = builder.source;
    }

    public static Builder builder(ElementId id, String text, SourceReference source) {
        return new Builder(id, text, source);
    }

    @Override
    public SerpFeatureType getType() {
        return SerpFeatureType.FEATURED_SNIPPET;
    }

    @Override
    public <R> R accept(SerpFeatureVisitor<R> visitor) {
        return visitor.visitFeaturedSnippet(this);
    }

    public String getText() {
        return text;
    }

    public SourceReference getSource() {
        return source;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    protected boolean contentEquals(AbstractSerpFeature other) {
        FeaturedSnippetFeature that = (FeaturedSnippetFeature) other;
        return text.equals(that.text) && source.equals(that.source);
    }

    @Override
    protected int contentHashCode() {
        return Objects.hash(text, source);
    }

    /** Builder for {@link FeaturedSnippetFeature}. */
    public static final class Builder extends AbstractSerpFeature.Builder<Builder> {
        private final String text;
        private final SourceReference source;

        private Builder(ElementId id, String text, SourceReference source) {
            super(id);
            this.text = Preconditions.requireText(text, "text");
            this.source = Preconditions.requireNonNull(source, "source");
        }

        private Builder(FeaturedSnippetFeature feature) {
            super(feature);
            this.text = feature.text;
            this.source = feature.source;
        }

        @Override
        protected Builder self() {
            return this;
        }

        public FeaturedSnippetFeature build() {
            return new FeaturedSnippetFeature(this);
        }
    }
}
