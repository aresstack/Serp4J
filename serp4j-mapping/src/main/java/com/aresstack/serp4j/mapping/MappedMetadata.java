package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.ext.ExtensionKey;
import com.aresstack.serp4j.core.model.ext.Extensions;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.metadata.ExecutionInfo;
import com.aresstack.serp4j.core.model.metadata.MonetaryAmount;
import com.aresstack.serp4j.core.model.metadata.PageInfo;
import com.aresstack.serp4j.core.model.metadata.ProviderUsage;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Metadata values found in a provider response – the fragments of {@code SearchMetadata} the response itself
 * carries. Provider, engine, vertical and paging request values are known to the provider, not to the response, so
 * the provider combines both (see {@link MappingResult#applyTo}). Immutable.
 */
public final class MappedMetadata {

    private static final MappedMetadata EMPTY = new Builder().build();

    private final String originalQuery;
    private final String effectiveQuery;
    private final Long totalResultsEstimate;
    private final Boolean hasMore;
    private final Integer pageNumber;
    private final String providerRequestId;
    private final Duration duration;
    private final BigDecimal cost;
    private final String currency;
    private final Long creditsUsed;
    private final Extensions extensions;

    private MappedMetadata(Builder b) {
        this.originalQuery = b.originalQuery;
        this.effectiveQuery = b.effectiveQuery;
        this.totalResultsEstimate = b.totalResultsEstimate;
        this.hasMore = b.hasMore;
        this.pageNumber = b.pageNumber;
        this.providerRequestId = b.providerRequestId;
        this.duration = b.duration;
        this.cost = b.cost;
        this.currency = b.currency;
        this.creditsUsed = b.creditsUsed;
        this.extensions = b.extensions.build();
    }

    public static MappedMetadata empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The query as the provider echoes it. */
    public Optional<String> getOriginalQuery() {
        return Optional.ofNullable(originalQuery);
    }

    /** The query the provider actually searched (for example after spell correction). */
    public Optional<String> getEffectiveQuery() {
        return Optional.ofNullable(effectiveQuery);
    }

    public OptionalLong getTotalResultsEstimate() {
        return totalResultsEstimate == null ? OptionalLong.empty() : OptionalLong.of(totalResultsEstimate);
    }

    public Optional<Boolean> getHasMore() {
        return Optional.ofNullable(hasMore);
    }

    public OptionalInt getPageNumber() {
        return pageNumber == null ? OptionalInt.empty() : OptionalInt.of(pageNumber);
    }

    public Optional<String> getProviderRequestId() {
        return Optional.ofNullable(providerRequestId);
    }

    /** Execution time reported by the provider. */
    public Optional<Duration> getDuration() {
        return Optional.ofNullable(duration);
    }

    public Optional<BigDecimal> getCost() {
        return Optional.ofNullable(cost);
    }

    /** ISO-4217 currency of {@link #getCost()}. */
    public Optional<String> getCurrency() {
        return Optional.ofNullable(currency);
    }

    public OptionalLong getCreditsUsed() {
        return creditsUsed == null ? OptionalLong.empty() : OptionalLong.of(creditsUsed);
    }

    /** Declared extension values plus the unmapped rest of the response root. */
    public Extensions getExtensions() {
        return extensions;
    }

    /** {@link QueryInfo} from the echoed queries; {@code fallbackQuery} (the request's query) if none was echoed. */
    public QueryInfo toQueryInfo(String fallbackQuery) {
        String original = originalQuery != null ? originalQuery : Objects.requireNonNull(fallbackQuery, "fallbackQuery");
        return effectiveQuery != null ? QueryInfo.of(original, effectiveQuery) : QueryInfo.of(original);
    }

    /** {@link ExecutionInfo} with request id and duration, if the response carried any of them. */
    public Optional<ExecutionInfo> toExecutionInfo() {
        if (providerRequestId == null && duration == null) {
            return Optional.empty();
        }
        ExecutionInfo.Builder builder = ExecutionInfo.builder();
        if (providerRequestId != null) {
            builder.providerRequestId(providerRequestId);
        }
        if (duration != null) {
            builder.duration(duration);
        }
        return Optional.of(builder.build());
    }

    /**
     * {@link ProviderUsage} with cost (only together with a currency) and credits, if the response carried any.
     */
    public Optional<ProviderUsage> toUsage() {
        boolean hasCost = cost != null && currency != null;
        if (!hasCost && creditsUsed == null) {
            return Optional.empty();
        }
        ProviderUsage.Builder builder = ProviderUsage.builder();
        if (hasCost) {
            builder.cost(MonetaryAmount.of(cost, currency));
        }
        if (creditsUsed != null) {
            builder.creditsUsed(creditsUsed);
        }
        return Optional.of(builder.build());
    }

    /** Sets page number, "has more" and total estimate on a page builder created by the provider from the request. */
    public PageInfo.Builder applyTo(PageInfo.Builder page) {
        Objects.requireNonNull(page, "page");
        if (pageNumber != null) {
            page.pageNumber(pageNumber);
        }
        if (hasMore != null) {
            page.hasMore(hasMore);
        }
        if (totalResultsEstimate != null) {
            page.totalResultsEstimate(totalResultsEstimate);
        }
        return page;
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.originalQuery = originalQuery;
        b.effectiveQuery = effectiveQuery;
        b.totalResultsEstimate = totalResultsEstimate;
        b.hasMore = hasMore;
        b.pageNumber = pageNumber;
        b.providerRequestId = providerRequestId;
        b.duration = duration;
        b.cost = cost;
        b.currency = currency;
        b.creditsUsed = creditsUsed;
        b.extensions = extensions.toBuilder();
        return b;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MappedMetadata)) {
            return false;
        }
        MappedMetadata m = (MappedMetadata) o;
        return Objects.equals(originalQuery, m.originalQuery) && Objects.equals(effectiveQuery, m.effectiveQuery)
                && Objects.equals(totalResultsEstimate, m.totalResultsEstimate) && Objects.equals(hasMore, m.hasMore)
                && Objects.equals(pageNumber, m.pageNumber) && Objects.equals(providerRequestId, m.providerRequestId)
                && Objects.equals(duration, m.duration) && Objects.equals(cost, m.cost)
                && Objects.equals(currency, m.currency) && Objects.equals(creditsUsed, m.creditsUsed)
                && extensions.equals(m.extensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalQuery, effectiveQuery, totalResultsEstimate, hasMore, pageNumber,
                providerRequestId, duration, cost, currency, creditsUsed, extensions);
    }

    @Override
    public String toString() {
        return "MappedMetadata{originalQuery=" + originalQuery + ", totalResultsEstimate=" + totalResultsEstimate
                + ", providerRequestId=" + providerRequestId + ", cost=" + cost + " " + currency + ", extensions="
                + extensions + '}';
    }

    /** Builder; {@code null} clears a value. Used by the engine and by {@link com.aresstack.serp4j.mapping.custom.MetadataMapper}s. */
    public static final class Builder {
        private String originalQuery;
        private String effectiveQuery;
        private Long totalResultsEstimate;
        private Boolean hasMore;
        private Integer pageNumber;
        private String providerRequestId;
        private Duration duration;
        private BigDecimal cost;
        private String currency;
        private Long creditsUsed;
        private Extensions.Builder extensions = Extensions.builder();

        private Builder() {
        }

        public Builder originalQuery(String value) {
            this.originalQuery = blankToNull(value);
            return this;
        }

        public Builder effectiveQuery(String value) {
            this.effectiveQuery = blankToNull(value);
            return this;
        }

        public Builder totalResultsEstimate(Long value) {
            if (value != null && value < 0) {
                throw new IllegalArgumentException("totalResultsEstimate must not be negative");
            }
            this.totalResultsEstimate = value;
            return this;
        }

        public Builder hasMore(Boolean value) {
            this.hasMore = value;
            return this;
        }

        public Builder pageNumber(Integer value) {
            if (value != null && value < 1) {
                throw new IllegalArgumentException("pageNumber must be at least 1");
            }
            this.pageNumber = value;
            return this;
        }

        public Builder providerRequestId(String value) {
            this.providerRequestId = blankToNull(value);
            return this;
        }

        public Builder duration(Duration value) {
            if (value != null && value.isNegative()) {
                throw new IllegalArgumentException("duration must not be negative");
            }
            this.duration = value;
            return this;
        }

        public Builder cost(BigDecimal value) {
            this.cost = value;
            return this;
        }

        /** @param value three-letter ISO-4217 code, case-insensitive */
        public Builder currency(String value) {
            String code = blankToNull(value);
            if (code != null) {
                code = code.toUpperCase(java.util.Locale.ROOT);
                if (!code.matches("[A-Z]{3}")) {
                    throw new IllegalArgumentException("currency must be a three-letter ISO-4217 code but was '" + value + "'");
                }
            }
            this.currency = code;
            return this;
        }

        public Builder creditsUsed(Long value) {
            if (value != null && value < 0) {
                throw new IllegalArgumentException("creditsUsed must not be negative");
            }
            this.creditsUsed = value;
            return this;
        }

        public <T> Builder extension(ExtensionKey<T> key, T value) {
            extensions.put(key, value);
            return this;
        }

        public Builder unmapped(String namespace, StructuredValue remainder) {
            extensions.unmapped(namespace, remainder);
            return this;
        }

        public MappedMetadata build() {
            return new MappedMetadata(this);
        }

        private static String blankToNull(String s) {
            if (s == null) {
                return null;
            }
            String t = s.trim();
            return t.isEmpty() ? null : t;
        }
    }
}
