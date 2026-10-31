package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.Optional;

/**
 * The query as requested and as actually executed. Mandatory in every {@link SearchMetadata}, also in merged
 * responses (ADR-008).
 */
public final class QueryInfo {

    private final String originalQuery;
    private final String effectiveQuery;
    private final SpellCorrection spellCorrection;

    private QueryInfo(String originalQuery, String effectiveQuery, SpellCorrection spellCorrection) {
        this.originalQuery = originalQuery;
        this.effectiveQuery = effectiveQuery;
        this.spellCorrection = spellCorrection;
    }

    public static QueryInfo of(String originalQuery) {
        return of(originalQuery, null, null);
    }

    public static QueryInfo of(String originalQuery, String effectiveQuery) {
        return of(originalQuery, effectiveQuery, null);
    }

    public static QueryInfo of(String originalQuery, String effectiveQuery, SpellCorrection spellCorrection) {
        return new QueryInfo(Preconditions.requireText(originalQuery, "originalQuery"),
                Preconditions.trimToNull(effectiveQuery), spellCorrection);
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    /** The query the engine actually used, when it differs from the original. */
    public Optional<String> getEffectiveQuery() {
        return Optional.ofNullable(effectiveQuery);
    }

    public Optional<SpellCorrection> getSpellCorrection() {
        return Optional.ofNullable(spellCorrection);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueryInfo)) {
            return false;
        }
        QueryInfo that = (QueryInfo) o;
        return originalQuery.equals(that.originalQuery)
                && Objects.equals(effectiveQuery, that.effectiveQuery)
                && Objects.equals(spellCorrection, that.spellCorrection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalQuery, effectiveQuery, spellCorrection);
    }

    @Override
    public String toString() {
        return "QueryInfo{original='" + originalQuery + "', effective=" + effectiveQuery + '}';
    }
}
