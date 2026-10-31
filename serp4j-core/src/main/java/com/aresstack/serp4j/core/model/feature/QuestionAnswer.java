package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.Optional;

/** One question of a people-also-ask or FAQ block with its optional answer and source. */
public final class QuestionAnswer {

    private final String question;
    private final String answer;
    private final SourceReference source;

    private QuestionAnswer(String question, String answer, SourceReference source) {
        this.question = question;
        this.answer = answer;
        this.source = source;
    }

    public static QuestionAnswer of(String question) {
        return of(question, null, null);
    }

    public static QuestionAnswer of(String question, String answer) {
        return of(question, answer, null);
    }

    public static QuestionAnswer of(String question, String answer, SourceReference source) {
        return new QuestionAnswer(Preconditions.requireText(question, "question"),
                Preconditions.trimToNull(answer), source);
    }

    public String getQuestion() {
        return question;
    }

    public Optional<String> getAnswer() {
        return Optional.ofNullable(answer);
    }

    public Optional<SourceReference> getSource() {
        return Optional.ofNullable(source);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QuestionAnswer)) {
            return false;
        }
        QuestionAnswer that = (QuestionAnswer) o;
        return question.equals(that.question)
                && Objects.equals(answer, that.answer)
                && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(question, answer, source);
    }

    @Override
    public String toString() {
        return "QuestionAnswer{question='" + question + "', answered=" + (answer != null) + '}';
    }
}
