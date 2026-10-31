package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.Optional;

/** Spelling correction applied or suggested by the engine. */
public final class SpellCorrection {

    /** How the engine treated the corrected query. */
    public enum Type {
        /** Results are shown for the corrected query. */
        SHOWING_RESULTS_FOR,
        /** The engine only suggests the correction. */
        DID_YOU_MEAN,
        /** Results include the corrected query in addition to the original. */
        INCLUDING_RESULTS_FOR,
        /** No results for the original query; results are shown for the corrected one. */
        NO_RESULTS_FOR,
        /** Provider-specific kind, see {@link SpellCorrection#getProviderType()}. */
        OTHER
    }

    private final String correctedQuery;
    private final Type type;
    private final String providerType;

    private SpellCorrection(String correctedQuery, Type type, String providerType) {
        this.correctedQuery = correctedQuery;
        this.type = type;
        this.providerType = providerType;
    }

    public static SpellCorrection of(String correctedQuery, Type type) {
        return of(correctedQuery, type, null);
    }

    /** @param providerType the provider's own type code, kept for {@link Type#OTHER} and for reference */
    public static SpellCorrection of(String correctedQuery, Type type, String providerType) {
        return new SpellCorrection(Preconditions.requireText(correctedQuery, "correctedQuery"),
                Preconditions.requireNonNull(type, "type"), Preconditions.trimToNull(providerType));
    }

    public String getCorrectedQuery() {
        return correctedQuery;
    }

    public Type getType() {
        return type;
    }

    public Optional<String> getProviderType() {
        return Optional.ofNullable(providerType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SpellCorrection)) {
            return false;
        }
        SpellCorrection that = (SpellCorrection) o;
        return correctedQuery.equals(that.correctedQuery)
                && type == that.type
                && Objects.equals(providerType, that.providerType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correctedQuery, type, providerType);
    }

    @Override
    public String toString() {
        return "SpellCorrection{" + type + ": '" + correctedQuery + "'}";
    }
}
