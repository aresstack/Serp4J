package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * People-also-ask ({@link SerpFeatureType#PEOPLE_ALSO_ASK}) or FAQ ({@link SerpFeatureType#FAQ}) block: a list of
 * questions with optional answers and sources.
 */
public final class QuestionAnswerFeature extends AbstractSerpFeature {

    private final SerpFeatureType type;
    private final List<QuestionAnswer> questions;

    private QuestionAnswerFeature(Builder builder) {
        super(builder);
        this.type = builder.type;
        this.questions = Preconditions.immutableList(builder.questions, "questions");
    }

    /** @param type usually {@link SerpFeatureType#PEOPLE_ALSO_ASK} or {@link SerpFeatureType#FAQ} */
    public static Builder builder(ElementId id, SerpFeatureType type) {
        return new Builder(id, type);
    }

    @Override
    public SerpFeatureType getType() {
        return type;
    }

    @Override
    public <R> R accept(SerpFeatureVisitor<R> visitor) {
        return visitor.visitQuestionAnswer(this);
    }

    public List<QuestionAnswer> getQuestions() {
        return questions;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(this);
        builder.questions.addAll(questions);
        return builder;
    }

    @Override
    protected boolean contentEquals(AbstractSerpFeature other) {
        QuestionAnswerFeature that = (QuestionAnswerFeature) other;
        return type.equals(that.type) && questions.equals(that.questions);
    }

    @Override
    protected int contentHashCode() {
        return Objects.hash(type, questions);
    }

    /** Builder for {@link QuestionAnswerFeature}. */
    public static final class Builder extends AbstractSerpFeature.Builder<Builder> {
        private final SerpFeatureType type;
        private final List<QuestionAnswer> questions = new ArrayList<QuestionAnswer>();

        private Builder(ElementId id, SerpFeatureType type) {
            super(id);
            this.type = Preconditions.requireNonNull(type, "type");
        }

        private Builder(QuestionAnswerFeature source) {
            super(source);
            this.type = source.type;
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder question(QuestionAnswer question) {
            questions.add(Preconditions.requireNonNull(question, "question"));
            return this;
        }

        public Builder questions(Collection<QuestionAnswer> questions) {
            for (QuestionAnswer question : Preconditions.requireNonNull(questions, "questions")) {
                question(question);
            }
            return this;
        }

        public QuestionAnswerFeature build() {
            return new QuestionAnswerFeature(this);
        }
    }
}
