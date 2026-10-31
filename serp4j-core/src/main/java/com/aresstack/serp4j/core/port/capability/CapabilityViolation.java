package com.aresstack.serp4j.core.port.capability;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.RequirementLevel;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;

import java.util.Objects;
import java.util.Optional;

/**
 * One aspect of a request a provider does not support (WP2 §6): a {@link SearchFeature} with the request's
 * requirement level, or the requested {@link SearchTarget} or {@link SearchVertical} (always required).
 */
public final class CapabilityViolation {

    private final SearchFeature feature;
    private final SearchTarget target;
    private final SearchVertical vertical;
    private final RequirementLevel level;
    private final String message;

    private CapabilityViolation(SearchFeature feature, SearchTarget target, SearchVertical vertical,
                                RequirementLevel level, String message) {
        this.feature = feature;
        this.target = target;
        this.vertical = vertical;
        this.level = level;
        this.message = Preconditions.requireText(message, "message");
    }

    public static CapabilityViolation ofFeature(SearchFeature feature, RequirementLevel level, String message) {
        return new CapabilityViolation(Preconditions.requireNonNull(feature, "feature"), null, null,
                Preconditions.requireNonNull(level, "level"), message);
    }

    public static CapabilityViolation ofTarget(SearchTarget target, String message) {
        return new CapabilityViolation(null, Preconditions.requireNonNull(target, "target"), null,
                RequirementLevel.REQUIRED, message);
    }

    public static CapabilityViolation ofVertical(SearchVertical vertical, String message) {
        return new CapabilityViolation(null, null, Preconditions.requireNonNull(vertical, "vertical"),
                RequirementLevel.REQUIRED, message);
    }

    public Optional<SearchFeature> getFeature() {
        return Optional.ofNullable(feature);
    }

    public Optional<SearchTarget> getTarget() {
        return Optional.ofNullable(target);
    }

    public Optional<SearchVertical> getVertical() {
        return Optional.ofNullable(vertical);
    }

    public RequirementLevel getLevel() {
        return level;
    }

    public boolean isRequired() {
        return level == RequirementLevel.REQUIRED;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CapabilityViolation)) {
            return false;
        }
        CapabilityViolation that = (CapabilityViolation) o;
        return feature == that.feature
                && Objects.equals(target, that.target)
                && Objects.equals(vertical, that.vertical)
                && level == that.level
                && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feature, target, vertical, level, message);
    }

    @Override
    public String toString() {
        Object subject = feature != null ? feature : target != null ? target : vertical;
        return "CapabilityViolation{" + level + " " + subject + ": " + message + '}';
    }
}
