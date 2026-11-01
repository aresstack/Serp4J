package com.aresstack.serp4j.core.model.analysis;

import com.aresstack.serp4j.core.internal.Codes;
import com.aresstack.serp4j.core.internal.Preconditions;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Who derived an analysis and when (M14): analyzer identifier and version, optionally the model used. Analysis is
 * never delivered by a search provider; it is always derived afterwards (ADR-003).
 */
public final class AnalysisProvenance {

    private final String analyzerId;
    private final String version;
    private final String model;
    private final Instant analyzedAt;

    private AnalysisProvenance(Builder builder) {
        this.analyzerId = builder.analyzerId;
        this.version = builder.version;
        this.model = builder.model;
        this.analyzedAt = builder.analyzedAt;
    }

    /** @param analyzerId code of the analyzer, format {@code [a-z0-9][a-z0-9._-]*} */
    public static Builder builder(String analyzerId) {
        return new Builder(analyzerId);
    }

    public static AnalysisProvenance of(String analyzerId) {
        return builder(analyzerId).build();
    }

    /** Returns a builder pre-filled with this instance's values. */
    public Builder toBuilder() {
        Builder builder = new Builder(analyzerId);
        builder.version = version;
        builder.model = model;
        builder.analyzedAt = analyzedAt;
        return builder;
    }

    public String getAnalyzerId() {
        return analyzerId;
    }

    public Optional<String> getVersion() {
        return Optional.ofNullable(version);
    }

    public Optional<String> getModel() {
        return Optional.ofNullable(model);
    }

    public Optional<Instant> getAnalyzedAt() {
        return Optional.ofNullable(analyzedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnalysisProvenance)) {
            return false;
        }
        AnalysisProvenance that = (AnalysisProvenance) o;
        return analyzerId.equals(that.analyzerId)
                && Objects.equals(version, that.version)
                && Objects.equals(model, that.model)
                && Objects.equals(analyzedAt, that.analyzedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(analyzerId, version, model, analyzedAt);
    }

    @Override
    public String toString() {
        return "AnalysisProvenance{analyzerId='" + analyzerId + "', version=" + version
                + ", model=" + model + ", analyzedAt=" + analyzedAt + '}';
    }

    /** Builder for {@link AnalysisProvenance}. */
    public static final class Builder {
        private final String analyzerId;
        private String version;
        private String model;
        private Instant analyzedAt;

        private Builder(String analyzerId) {
            this.analyzerId = Codes.normalize(analyzerId, "analyzer id");
        }

        public Builder version(String version) {
            this.version = Preconditions.trimToNull(version);
            return this;
        }

        public Builder model(String model) {
            this.model = Preconditions.trimToNull(model);
            return this;
        }

        public Builder analyzedAt(Instant analyzedAt) {
            this.analyzedAt = analyzedAt;
            return this;
        }

        public AnalysisProvenance build() {
            return new AnalysisProvenance(this);
        }
    }
}
