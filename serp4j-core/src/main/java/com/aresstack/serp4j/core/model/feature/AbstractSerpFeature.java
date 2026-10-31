package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.data.ResultProvenance;
import com.aresstack.serp4j.core.model.data.SerpPosition;
import com.aresstack.serp4j.core.model.ext.Extensions;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Base of the typed features: identifier, position, provenance and extensions. Subclasses add their content and
 * implement {@link #contentEquals(AbstractSerpFeature)} and {@link #contentHashCode()}; equality covers the base
 * fields, the exact class and the content.
 */
public abstract class AbstractSerpFeature implements SerpFeature {

    private final ElementId id;
    private final SerpPosition position;
    private final List<ResultProvenance> provenance;
    private final Extensions extensions;

    protected AbstractSerpFeature(Builder<?> builder) {
        this.id = builder.id;
        this.position = builder.position;
        this.provenance = Preconditions.immutableList(builder.provenance, "provenance");
        this.extensions = builder.extensions;
    }

    @Override
    public final ElementId getId() {
        return id;
    }

    @Override
    public final Optional<SerpPosition> getPosition() {
        return Optional.ofNullable(position);
    }

    @Override
    public final List<ResultProvenance> getProvenance() {
        return provenance;
    }

    @Override
    public final Extensions getExtensions() {
        return extensions;
    }

    /** Compares the subclass content; {@code other} is guaranteed to be of the same class. */
    protected abstract boolean contentEquals(AbstractSerpFeature other);

    protected abstract int contentHashCode();

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        AbstractSerpFeature that = (AbstractSerpFeature) o;
        return id.equals(that.id)
                && Objects.equals(position, that.position)
                && provenance.equals(that.provenance)
                && extensions.equals(that.extensions)
                && contentEquals(that);
    }

    @Override
    public final int hashCode() {
        return 31 * Objects.hash(id, position, provenance, extensions) + contentHashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", type=" + getType() + ", position=" + position + '}';
    }

    /**
     * Base builder with the common fields.
     *
     * @param <B> the concrete builder type, returned from every setter
     */
    public abstract static class Builder<B extends Builder<B>> {
        private final ElementId id;
        private SerpPosition position;
        private final List<ResultProvenance> provenance = new ArrayList<ResultProvenance>();
        private Extensions extensions = Extensions.empty();

        protected Builder(ElementId id) {
            this.id = Preconditions.requireNonNull(id, "id");
        }

        /** Copies the common fields of {@code source}. */
        protected Builder(AbstractSerpFeature source) {
            this.id = source.id;
            this.position = source.position;
            this.provenance.addAll(source.provenance);
            this.extensions = source.extensions;
        }

        protected abstract B self();

        public final B position(SerpPosition position) {
            this.position = position;
            return self();
        }

        public final B provenance(ResultProvenance origin) {
            provenance.add(Preconditions.requireNonNull(origin, "origin"));
            return self();
        }

        public final B provenance(Collection<ResultProvenance> origins) {
            for (ResultProvenance origin : Preconditions.requireNonNull(origins, "origins")) {
                provenance(origin);
            }
            return self();
        }

        public final B clearProvenance() {
            provenance.clear();
            return self();
        }

        public final B extensions(Extensions extensions) {
            this.extensions = Preconditions.requireNonNull(extensions, "extensions");
            return self();
        }
    }
}
