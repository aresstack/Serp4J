package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Paging information of a response: requested offset and count, page number, more-results flag, total estimate. */
public final class PageInfo {

    private final int offset;
    private final int requestedCount;
    private final Integer pageNumber;
    private final Boolean hasMore;
    private final Long totalResultsEstimate;

    private PageInfo(Builder builder) {
        this.offset = builder.offset;
        this.requestedCount = builder.requestedCount;
        this.pageNumber = builder.pageNumber;
        this.hasMore = builder.hasMore;
        this.totalResultsEstimate = builder.totalResultsEstimate;
    }

    public static Builder builder(int offset, int requestedCount) {
        return new Builder(offset, requestedCount);
    }

    public int getOffset() {
        return offset;
    }

    public int getRequestedCount() {
        return requestedCount;
    }

    public OptionalInt getPageNumber() {
        return pageNumber == null ? OptionalInt.empty() : OptionalInt.of(pageNumber);
    }

    public Optional<Boolean> getHasMore() {
        return Optional.ofNullable(hasMore);
    }

    public OptionalLong getTotalResultsEstimate() {
        return totalResultsEstimate == null ? OptionalLong.empty() : OptionalLong.of(totalResultsEstimate);
    }

    public Builder toBuilder() {
        Builder builder = new Builder(offset, requestedCount);
        builder.pageNumber = pageNumber;
        builder.hasMore = hasMore;
        builder.totalResultsEstimate = totalResultsEstimate;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PageInfo)) {
            return false;
        }
        PageInfo that = (PageInfo) o;
        return offset == that.offset
                && requestedCount == that.requestedCount
                && Objects.equals(pageNumber, that.pageNumber)
                && Objects.equals(hasMore, that.hasMore)
                && Objects.equals(totalResultsEstimate, that.totalResultsEstimate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, requestedCount, pageNumber, hasMore, totalResultsEstimate);
    }

    @Override
    public String toString() {
        return "PageInfo{offset=" + offset + ", requestedCount=" + requestedCount + ", pageNumber=" + pageNumber
                + ", hasMore=" + hasMore + ", totalResultsEstimate=" + totalResultsEstimate + '}';
    }

    /** Builder for {@link PageInfo}. */
    public static final class Builder {
        private final int offset;
        private final int requestedCount;
        private Integer pageNumber;
        private Boolean hasMore;
        private Long totalResultsEstimate;

        private Builder(int offset, int requestedCount) {
            this.offset = Preconditions.requireNonNegative(offset, "offset");
            this.requestedCount = Preconditions.requirePositive(requestedCount, "requestedCount");
        }

        /** @param pageNumber 1-based page number */
        public Builder pageNumber(int pageNumber) {
            this.pageNumber = Preconditions.requirePositive(pageNumber, "pageNumber");
            return this;
        }

        public Builder hasMore(boolean hasMore) {
            this.hasMore = hasMore;
            return this;
        }

        public Builder totalResultsEstimate(long totalResultsEstimate) {
            this.totalResultsEstimate = Preconditions.requireNonNegative(totalResultsEstimate, "totalResultsEstimate");
            return this;
        }

        public PageInfo build() {
            return new PageInfo(this);
        }
    }
}
