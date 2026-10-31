package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An AI-generated answer: Google AI Overview on a result page ({@link SerpFeatureType#AI_OVERVIEW}), a turn of
 * Google AI Mode ({@link SerpFeatureType#AI_MODE_ANSWER}) or a comparable provider feature. The type reflects the
 * search mode that was actually executed, never the provider's JSON element name (architecture.md §5.5).
 *
 * <p>Citations are {@link SourceReference}s, not result items. {@link #isComplete()} is {@code false} for
 * answers the provider delivered partially.</p>
 */
public final class GeneratedAnswerFeature extends AbstractSerpFeature {

    private final SerpFeatureType type;
    private final String text;
    private final String markdown;
    private final List<AnswerSegment> segments;
    private final List<SourceReference> references;
    private final List<String> followUpSuggestions;
    private final boolean complete;

    private GeneratedAnswerFeature(Builder builder) {
        super(builder);
        this.type = builder.type;
        this.text = builder.text;
        this.markdown = builder.markdown;
        this.segments = Preconditions.immutableList(builder.segments, "segments");
        this.references = Preconditions.immutableList(builder.references, "references");
        this.followUpSuggestions = Preconditions.immutableList(builder.followUpSuggestions, "followUpSuggestions");
        this.complete = builder.complete;
        for (AnswerSegment segment : segments) {
            for (Integer index : segment.getReferenceIndexes()) {
                if (index >= references.size()) {
                    throw new IllegalArgumentException("answer segment cites reference " + index
                            + " but only " + references.size() + " references exist");
                }
            }
        }
    }

    /** @param type usually {@link SerpFeatureType#AI_OVERVIEW} or {@link SerpFeatureType#AI_MODE_ANSWER} */
    public static Builder builder(ElementId id, SerpFeatureType type, String text) {
        return new Builder(id, type, text);
    }

    @Override
    public SerpFeatureType getType() {
        return type;
    }

    @Override
    public <R> R accept(SerpFeatureVisitor<R> visitor) {
        return visitor.visitGeneratedAnswer(this);
    }

    /** Plain-text answer. */
    public String getText() {
        return text;
    }

    public Optional<String> getMarkdown() {
        return Optional.ofNullable(markdown);
    }

    public List<AnswerSegment> getSegments() {
        return segments;
    }

    public List<SourceReference> getReferences() {
        return references;
    }

    public List<String> getFollowUpSuggestions() {
        return followUpSuggestions;
    }

    public boolean isComplete() {
        return complete;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(this);
        builder.markdown = markdown;
        builder.segments.addAll(segments);
        builder.references.addAll(references);
        builder.followUpSuggestions.addAll(followUpSuggestions);
        builder.complete = complete;
        return builder;
    }

    @Override
    protected boolean contentEquals(AbstractSerpFeature other) {
        GeneratedAnswerFeature that = (GeneratedAnswerFeature) other;
        return type.equals(that.type)
                && text.equals(that.text)
                && Objects.equals(markdown, that.markdown)
                && segments.equals(that.segments)
                && references.equals(that.references)
                && followUpSuggestions.equals(that.followUpSuggestions)
                && complete == that.complete;
    }

    @Override
    protected int contentHashCode() {
        return Objects.hash(type, text, markdown, segments, references, followUpSuggestions, complete);
    }

    /** Builder for {@link GeneratedAnswerFeature}. */
    public static final class Builder extends AbstractSerpFeature.Builder<Builder> {
        private final SerpFeatureType type;
        private final String text;
        private String markdown;
        private final List<AnswerSegment> segments = new ArrayList<AnswerSegment>();
        private final List<SourceReference> references = new ArrayList<SourceReference>();
        private final List<String> followUpSuggestions = new ArrayList<String>();
        private boolean complete = true;

        private Builder(ElementId id, SerpFeatureType type, String text) {
            super(id);
            this.type = Preconditions.requireNonNull(type, "type");
            this.text = Preconditions.requireText(text, "text");
        }

        private Builder(GeneratedAnswerFeature source) {
            super(source);
            this.type = source.type;
            this.text = source.text;
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder markdown(String markdown) {
            this.markdown = Preconditions.trimToNull(markdown);
            return this;
        }

        public Builder segment(AnswerSegment segment) {
            segments.add(Preconditions.requireNonNull(segment, "segment"));
            return this;
        }

        public Builder segments(Collection<AnswerSegment> segments) {
            for (AnswerSegment segment : Preconditions.requireNonNull(segments, "segments")) {
                segment(segment);
            }
            return this;
        }

        public Builder reference(SourceReference reference) {
            references.add(Preconditions.requireNonNull(reference, "reference"));
            return this;
        }

        public Builder references(Collection<SourceReference> references) {
            for (SourceReference reference : Preconditions.requireNonNull(references, "references")) {
                reference(reference);
            }
            return this;
        }

        public Builder followUpSuggestion(String suggestion) {
            String value = Preconditions.trimToNull(suggestion);
            if (value != null) {
                followUpSuggestions.add(value);
            }
            return this;
        }

        public Builder followUpSuggestions(Collection<String> suggestions) {
            for (String suggestion : Preconditions.requireNonNull(suggestions, "suggestions")) {
                followUpSuggestion(suggestion);
            }
            return this;
        }

        public Builder complete(boolean complete) {
            this.complete = complete;
            return this;
        }

        /** @throws IllegalArgumentException if a segment cites a non-existent reference */
        public GeneratedAnswerFeature build() {
            return new GeneratedAnswerFeature(this);
        }
    }
}
