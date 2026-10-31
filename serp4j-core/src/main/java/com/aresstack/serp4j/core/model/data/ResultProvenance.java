package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ElementId;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;

import java.util.Objects;
import java.util.Optional;

/**
 * Origin of an element in a <em>merged</em> response (ADR-008): the provider and engine that delivered it and its
 * original identifier and position. Elements of a single-provider response carry no provenance; a merger adds one
 * entry per contributing source, so a de-duplicated element keeps all its origins.
 */
public final class ResultProvenance {

    private final ProviderId provider;
    private final SearchEngineId engine;
    private final ElementId originalId;
    private final SerpPosition originalPosition;

    private ResultProvenance(Builder builder) {
        this.provider = builder.provider;
        this.engine = builder.engine;
        this.originalId = builder.originalId;
        this.originalPosition = builder.originalPosition;
    }

    public static Builder builder(ProviderId provider) {
        return new Builder(provider);
    }

    public ProviderId getProvider() {
        return provider;
    }

    public Optional<SearchEngineId> getEngine() {
        return Optional.ofNullable(engine);
    }

    public Optional<ElementId> getOriginalId() {
        return Optional.ofNullable(originalId);
    }

    public Optional<SerpPosition> getOriginalPosition() {
        return Optional.ofNullable(originalPosition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResultProvenance)) {
            return false;
        }
        ResultProvenance that = (ResultProvenance) o;
        return provider.equals(that.provider)
                && Objects.equals(engine, that.engine)
                && Objects.equals(originalId, that.originalId)
                && Objects.equals(originalPosition, that.originalPosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, engine, originalId, originalPosition);
    }

    @Override
    public String toString() {
        return "ResultProvenance{provider=" + provider + ", engine=" + engine
                + ", originalId=" + originalId + ", originalPosition=" + originalPosition + '}';
    }

    /** Builder for {@link ResultProvenance}. */
    public static final class Builder {
        private final ProviderId provider;
        private SearchEngineId engine;
        private ElementId originalId;
        private SerpPosition originalPosition;

        private Builder(ProviderId provider) {
            this.provider = Preconditions.requireNonNull(provider, "provider");
        }

        public Builder engine(SearchEngineId engine) {
            this.engine = engine;
            return this;
        }

        public Builder originalId(ElementId originalId) {
            this.originalId = originalId;
            return this;
        }

        public Builder originalPosition(SerpPosition originalPosition) {
            this.originalPosition = originalPosition;
            return this;
        }

        public ResultProvenance build() {
            return new ResultProvenance(this);
        }
    }
}
