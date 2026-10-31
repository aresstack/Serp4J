package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Position of an element on the result page (WP1 D6). Providers use different rank semantics, therefore rank is
 * not a single number: {@link #getRankInGroup()} is the 1-based rank within the element's type or block,
 * {@link #getRankAbsolute()} the 1-based rank across the whole page, {@link #getPage()} the 1-based page number and
 * {@link #getArea()} the region of the page. Ranks of different providers are not comparable without an explicit
 * strategy.
 */
public final class SerpPosition {

    private final Integer rankInGroup;
    private final Integer rankAbsolute;
    private final Integer page;
    private final SerpArea area;

    private SerpPosition(Builder builder) {
        this.rankInGroup = builder.rankInGroup;
        this.rankAbsolute = builder.rankAbsolute;
        this.page = builder.page;
        this.area = builder.area;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SerpPosition ofRankInGroup(int rankInGroup) {
        return builder().rankInGroup(rankInGroup).build();
    }

    public OptionalInt getRankInGroup() {
        return rankInGroup == null ? OptionalInt.empty() : OptionalInt.of(rankInGroup);
    }

    public OptionalInt getRankAbsolute() {
        return rankAbsolute == null ? OptionalInt.empty() : OptionalInt.of(rankAbsolute);
    }

    public OptionalInt getPage() {
        return page == null ? OptionalInt.empty() : OptionalInt.of(page);
    }

    public SerpArea getArea() {
        return area;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.rankInGroup = rankInGroup;
        builder.rankAbsolute = rankAbsolute;
        builder.page = page;
        builder.area = area;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SerpPosition)) {
            return false;
        }
        SerpPosition that = (SerpPosition) o;
        return Objects.equals(rankInGroup, that.rankInGroup)
                && Objects.equals(rankAbsolute, that.rankAbsolute)
                && Objects.equals(page, that.page)
                && area == that.area;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rankInGroup, rankAbsolute, page, area);
    }

    @Override
    public String toString() {
        return "SerpPosition{rankInGroup=" + rankInGroup + ", rankAbsolute=" + rankAbsolute
                + ", page=" + page + ", area=" + area + '}';
    }

    /** Builder; all ranks are 1-based. */
    public static final class Builder {
        private Integer rankInGroup;
        private Integer rankAbsolute;
        private Integer page;
        private SerpArea area = SerpArea.MAIN;

        private Builder() {
        }

        public Builder rankInGroup(int rankInGroup) {
            this.rankInGroup = Preconditions.requirePositive(rankInGroup, "rankInGroup");
            return this;
        }

        public Builder rankAbsolute(int rankAbsolute) {
            this.rankAbsolute = Preconditions.requirePositive(rankAbsolute, "rankAbsolute");
            return this;
        }

        public Builder page(int page) {
            this.page = Preconditions.requirePositive(page, "page");
            return this;
        }

        public Builder area(SerpArea area) {
            this.area = Preconditions.requireNonNull(area, "area");
            return this;
        }

        public SerpPosition build() {
            return new SerpPosition(this);
        }
    }
}
