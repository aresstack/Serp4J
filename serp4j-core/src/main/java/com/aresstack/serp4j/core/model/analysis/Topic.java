package com.aresstack.serp4j.core.model.analysis;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** A topic or cluster derived from the results: label, weight in {@code [0,1]} and the elements it covers. */
public final class Topic {

    private final String label;
    private final double weight;
    private final List<ElementId> elements;

    private Topic(Builder builder) {
        this.label = builder.label;
        this.weight = builder.weight;
        this.elements = Preconditions.immutableList(builder.elements, "elements");
    }

    public static Builder builder(String label, double weight) {
        return new Builder(label, weight);
    }

    public static Topic of(String label, double weight) {
        return builder(label, weight).build();
    }

    public String getLabel() {
        return label;
    }

    public double getWeight() {
        return weight;
    }

    public List<ElementId> getElements() {
        return elements;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Topic)) {
            return false;
        }
        Topic that = (Topic) o;
        return label.equals(that.label)
                && Double.compare(weight, that.weight) == 0
                && elements.equals(that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, weight, elements);
    }

    @Override
    public String toString() {
        return "Topic{label='" + label + "', weight=" + weight + ", elements=" + elements.size() + '}';
    }

    /** Builder for {@link Topic}. */
    public static final class Builder {
        private final String label;
        private final double weight;
        private final List<ElementId> elements = new ArrayList<ElementId>();

        private Builder(String label, double weight) {
            this.label = Preconditions.requireText(label, "label");
            this.weight = Preconditions.requireRange(weight, 0, 1, "weight");
        }

        public Builder element(ElementId element) {
            elements.add(Preconditions.requireNonNull(element, "element"));
            return this;
        }

        public Builder elements(Collection<ElementId> elements) {
            for (ElementId element : Preconditions.requireNonNull(elements, "elements")) {
                element(element);
            }
            return this;
        }

        public Topic build() {
            return new Topic(this);
        }
    }
}
