package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.Objects;
import java.util.Optional;

/**
 * A cited source inside a feature (AI answer reference, featured-snippet source, knowledge-panel description
 * source). Not a hit of its own; if the same URL is also a result item, {@link #getMatchingResult()} points to it.
 */
public final class SourceReference {

    private final String url;
    private final String title;
    private final String snippet;
    private final String domain;
    private final String sourceName;
    private final ElementId matchingResult;

    private SourceReference(Builder builder) {
        this.url = builder.url;
        this.title = builder.title;
        this.snippet = builder.snippet;
        this.domain = builder.domain;
        this.sourceName = builder.sourceName;
        this.matchingResult = builder.matchingResult;
    }

    public static Builder builder(String url) {
        return new Builder(url);
    }

    public static SourceReference of(String url) {
        return builder(url).build();
    }

    public static SourceReference of(String url, String title) {
        return builder(url).title(title).build();
    }

    public String getUrl() {
        return url;
    }

    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    public Optional<String> getSnippet() {
        return Optional.ofNullable(snippet);
    }

    public Optional<String> getDomain() {
        return Optional.ofNullable(domain);
    }

    public Optional<String> getSourceName() {
        return Optional.ofNullable(sourceName);
    }

    public Optional<ElementId> getMatchingResult() {
        return Optional.ofNullable(matchingResult);
    }

    public Builder toBuilder() {
        Builder builder = new Builder(url);
        builder.title = title;
        builder.snippet = snippet;
        builder.domain = domain;
        builder.sourceName = sourceName;
        builder.matchingResult = matchingResult;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SourceReference)) {
            return false;
        }
        SourceReference that = (SourceReference) o;
        return url.equals(that.url)
                && Objects.equals(title, that.title)
                && Objects.equals(snippet, that.snippet)
                && Objects.equals(domain, that.domain)
                && Objects.equals(sourceName, that.sourceName)
                && Objects.equals(matchingResult, that.matchingResult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, title, snippet, domain, sourceName, matchingResult);
    }

    @Override
    public String toString() {
        return "SourceReference{url='" + url + "', title=" + title + ", matchingResult=" + matchingResult + '}';
    }

    /** Builder for {@link SourceReference}. */
    public static final class Builder {
        private final String url;
        private String title;
        private String snippet;
        private String domain;
        private String sourceName;
        private ElementId matchingResult;

        private Builder(String url) {
            this.url = Preconditions.requireText(url, "url");
        }

        public Builder title(String title) {
            this.title = Preconditions.trimToNull(title);
            return this;
        }

        public Builder snippet(String snippet) {
            this.snippet = Preconditions.trimToNull(snippet);
            return this;
        }

        public Builder domain(String domain) {
            this.domain = Preconditions.trimToNull(domain);
            return this;
        }

        public Builder sourceName(String sourceName) {
            this.sourceName = Preconditions.trimToNull(sourceName);
            return this;
        }

        public Builder matchingResult(ElementId matchingResult) {
            this.matchingResult = matchingResult;
            return this;
        }

        public SourceReference build() {
            return new SourceReference(this);
        }
    }
}
