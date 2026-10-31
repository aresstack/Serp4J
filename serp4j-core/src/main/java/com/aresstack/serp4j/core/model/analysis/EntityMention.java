package com.aresstack.serp4j.core.model.analysis;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** A named entity recognised in the results, with the elements it was found in. */
public final class EntityMention {

    private final String name;
    private final String type;
    private final Double confidence;
    private final List<ElementId> elements;

    private EntityMention(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.confidence = builder.confidence;
        this.elements = Preconditions.immutableList(builder.elements, "elements");
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String getName() {
        return name;
    }

    /** Entity type code as reported by the analyzer (person, organisation, place, …). */
    public Optional<String> getType() {
        return Optional.ofNullable(type);
    }

    public OptionalDouble getConfidence() {
        return confidence == null ? OptionalDouble.empty() : OptionalDouble.of(confidence);
    }

    /** Elements of the response the entity was found in. */
    public List<ElementId> getElements() {
        return elements;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EntityMention)) {
            return false;
        }
        EntityMention that = (EntityMention) o;
        return name.equals(that.name)
                && Objects.equals(type, that.type)
                && Objects.equals(confidence, that.confidence)
                && elements.equals(that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, confidence, elements);
    }

    @Override
    public String toString() {
        return "EntityMention{name='" + name + "', type=" + type + ", confidence=" + confidence + '}';
    }

    /** Builder for {@link EntityMention}. */
    public static final class Builder {
        private final String name;
        private String type;
        private Double confidence;
        private final List<ElementId> elements = new ArrayList<ElementId>();

        private Builder(String name) {
            this.name = Preconditions.requireText(name, "name");
        }

        public Builder type(String type) {
            this.type = Preconditions.trimToNull(type);
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = Preconditions.requireRange(confidence, 0, 1, "confidence");
            return this;
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

        public EntityMention build() {
            return new EntityMention(this);
        }
    }
}
