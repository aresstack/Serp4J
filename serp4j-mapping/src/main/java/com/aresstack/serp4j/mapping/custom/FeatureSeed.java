package com.aresstack.serp4j.mapping.custom;

import com.aresstack.serp4j.core.model.data.SerpPosition;
import com.aresstack.serp4j.core.model.ext.Extensions;
import com.aresstack.serp4j.core.model.feature.AbstractSerpFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.Objects;
import java.util.Optional;

/**
 * What the engine has already determined for a feature before a {@link FeatureMapper} runs: the element id to use,
 * the resolved type, the section, the declarative position and the declared extension values. Immutable.
 */
public final class FeatureSeed {

    private final ElementId id;
    private final SerpFeatureType type;
    private final String sectionId;
    private final SerpPosition position;
    private final Extensions extensions;

    public FeatureSeed(ElementId id, SerpFeatureType type, String sectionId, SerpPosition position,
                       Extensions extensions) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.sectionId = Objects.requireNonNull(sectionId, "sectionId");
        this.position = position;
        this.extensions = Objects.requireNonNull(extensions, "extensions");
    }

    /** The id the feature must use. */
    public ElementId getId() {
        return id;
    }

    public SerpFeatureType getType() {
        return type;
    }

    public String getSectionId() {
        return sectionId;
    }

    public Optional<SerpPosition> getPosition() {
        return Optional.ofNullable(position);
    }

    /** Extension values declared in the definition for this element (without the unmapped rest). */
    public Extensions getExtensions() {
        return extensions;
    }

    /** Sets position (if any) and the declared extensions on a feature builder. */
    public <B extends AbstractSerpFeature.Builder<B>> B applyTo(B builder) {
        Objects.requireNonNull(builder, "builder");
        if (position != null) {
            builder.position(position);
        }
        return builder.extensions(extensions);
    }

    @Override
    public String toString() {
        return "FeatureSeed{" + id + ", " + type + ", section=" + sectionId + '}';
    }
}
