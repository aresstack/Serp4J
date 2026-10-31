package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.Objects;
import java.util.Optional;

/**
 * Direct answer box ({@link SerpFeatureType#ANSWER_BOX}, {@link SerpFeatureType#CALCULATOR},
 * {@link SerpFeatureType#WEATHER}, …): a short answer text with an optional source.
 */
public final class AnswerBoxFeature extends AbstractSerpFeature {

    private final SerpFeatureType type;
    private final String text;
    private final SourceReference source;

    private AnswerBoxFeature(Builder builder) {
        super(builder);
        this.type = builder.type;
        this.text = builder.text;
        this.source = builder.source;
    }

    public static Builder builder(ElementId id, SerpFeatureType type, String text) {
        return new Builder(id, type, text);
    }

    @Override
    public SerpFeatureType getType() {
        return type;
    }

    @Override
    public <R> R accept(SerpFeatureVisitor<R> visitor) {
        return visitor.visitAnswerBox(this);
    }

    public String getText() {
        return text;
    }

    public Optional<SourceReference> getSource() {
        return Optional.ofNullable(source);
    }

    public Builder toBuilder() {
        Builder builder = new Builder(this);
        builder.source = source;
        return builder;
    }

    @Override
    protected boolean contentEquals(AbstractSerpFeature other) {
        AnswerBoxFeature that = (AnswerBoxFeature) other;
        return type.equals(that.type) && text.equals(that.text) && Objects.equals(source, that.source);
    }

    @Override
    protected int contentHashCode() {
        return Objects.hash(type, text, source);
    }

    /** Builder for {@link AnswerBoxFeature}. */
    public static final class Builder extends AbstractSerpFeature.Builder<Builder> {
        private final SerpFeatureType type;
        private final String text;
        private SourceReference source;

        private Builder(ElementId id, SerpFeatureType type, String text) {
            super(id);
            this.type = Preconditions.requireNonNull(type, "type");
            this.text = Preconditions.requireText(text, "text");
        }

        private Builder(AnswerBoxFeature feature) {
            super(feature);
            this.type = feature.type;
            this.text = feature.text;
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder source(SourceReference source) {
            this.source = source;
            return this;
        }

        public AnswerBoxFeature build() {
            return new AnswerBoxFeature(this);
        }
    }
}
