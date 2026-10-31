package com.aresstack.serp4j.core.model;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.analysis.SearchAnalysis;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;

import java.util.Objects;
import java.util.Optional;

/**
 * The result of a search: {@link SearchData} (mandatory, may be empty), {@link SearchMetadata} (optional in the
 * type, mandatory for provider answers) and {@link SearchAnalysis} (optional, never set by a provider) – ADR-003.
 */
public final class SearchResponse {

    private final SearchData data;
    private final SearchMetadata metadata;
    private final SearchAnalysis analysis;

    private SearchResponse(Builder builder) {
        this.data = builder.data;
        this.metadata = builder.metadata;
        this.analysis = builder.analysis;
    }

    public static Builder builder(SearchData data) {
        return new Builder(data);
    }

    /** Response with data and metadata, the usual shape of a provider answer. */
    public static SearchResponse of(SearchData data, SearchMetadata metadata) {
        return builder(data).metadata(Preconditions.requireNonNull(metadata, "metadata")).build();
    }

    public SearchData getData() {
        return data;
    }

    public Optional<SearchMetadata> getMetadata() {
        return Optional.ofNullable(metadata);
    }

    public Optional<SearchAnalysis> getAnalysis() {
        return Optional.ofNullable(analysis);
    }

    /** Copy with {@code analysis} attached; data and metadata are unchanged. */
    public SearchResponse withAnalysis(SearchAnalysis analysis) {
        return toBuilder().analysis(Preconditions.requireNonNull(analysis, "analysis")).build();
    }

    /** Copy without analysis. */
    public SearchResponse withoutAnalysis() {
        return analysis == null ? this : toBuilder().analysis(null).build();
    }

    /** Copy whose metadata carries no {@code RawPayload} (for logging and persistence). */
    public SearchResponse withoutRawPayload() {
        if (metadata == null || !metadata.getRawPayload().isPresent()) {
            return this;
        }
        return toBuilder().metadata(metadata.withoutRawPayload()).build();
    }

    public Builder toBuilder() {
        Builder builder = new Builder(data);
        builder.metadata = metadata;
        builder.analysis = analysis;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchResponse)) {
            return false;
        }
        SearchResponse that = (SearchResponse) o;
        return data.equals(that.data)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(analysis, that.analysis);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, metadata, analysis);
    }

    @Override
    public String toString() {
        return "SearchResponse{data=" + data + ", metadata=" + metadata + ", analysis=" + analysis + '}';
    }

    /** Builder for {@link SearchResponse}. */
    public static final class Builder {
        private final SearchData data;
        private SearchMetadata metadata;
        private SearchAnalysis analysis;

        private Builder(SearchData data) {
            this.data = Preconditions.requireNonNull(data, "data");
        }

        public Builder metadata(SearchMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder analysis(SearchAnalysis analysis) {
            this.analysis = analysis;
            return this;
        }

        public SearchResponse build() {
            return new SearchResponse(this);
        }
    }
}
