package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.ext.Extensions;
import com.aresstack.serp4j.core.model.id.ElementId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A single linked document on the result page – organic hit, ad, news article, video, image, … – distinguished
 * by {@link ResultType} (WP1 D3). Only the URL is mandatory; items without a title are kept.
 */
public final class SearchResultItem implements SearchElement {

    private final ElementId id;
    private final ResultType type;
    private final String url;
    private final String title;
    private final String snippet;
    private final List<String> additionalSnippets;
    private final List<TextRange> highlights;
    private final String displayUrl;
    private final String domain;
    private final String siteName;
    private final PublishedDate publishedDate;
    private final String language;
    private final MediaInfo thumbnail;
    private final Rating rating;
    private final List<SiteLink> siteLinks;
    private final SerpPosition position;
    private final List<ResultProvenance> provenance;
    private final Extensions extensions;

    private SearchResultItem(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.url = builder.url;
        this.title = builder.title;
        this.snippet = builder.snippet;
        this.additionalSnippets = Preconditions.immutableList(builder.additionalSnippets, "additionalSnippets");
        this.highlights = Preconditions.immutableList(builder.highlights, "highlights");
        this.displayUrl = builder.displayUrl;
        this.domain = builder.domain;
        this.siteName = builder.siteName;
        this.publishedDate = builder.publishedDate;
        this.language = builder.language;
        this.thumbnail = builder.thumbnail;
        this.rating = builder.rating;
        this.siteLinks = Preconditions.immutableList(builder.siteLinks, "siteLinks");
        this.position = builder.position;
        this.provenance = Preconditions.immutableList(builder.provenance, "provenance");
        this.extensions = builder.extensions;
    }

    public static Builder builder(ElementId id, ResultType type, String url) {
        return new Builder(id, type, url);
    }

    @Override
    public ElementId getId() {
        return id;
    }

    public ResultType getType() {
        return type;
    }

    /** Target URL as delivered (may be a redirect or tracking URL); normalisation is a mapper or merger concern. */
    public String getUrl() {
        return url;
    }

    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    /** Plain-text snippet without markup; highlighted ranges are in {@link #getHighlights()}. */
    public Optional<String> getSnippet() {
        return Optional.ofNullable(snippet);
    }

    public List<String> getAdditionalSnippets() {
        return additionalSnippets;
    }

    public List<TextRange> getHighlights() {
        return highlights;
    }

    public Optional<String> getDisplayUrl() {
        return Optional.ofNullable(displayUrl);
    }

    public Optional<String> getDomain() {
        return Optional.ofNullable(domain);
    }

    public Optional<String> getSiteName() {
        return Optional.ofNullable(siteName);
    }

    public Optional<PublishedDate> getPublishedDate() {
        return Optional.ofNullable(publishedDate);
    }

    public Optional<String> getLanguage() {
        return Optional.ofNullable(language);
    }

    public Optional<MediaInfo> getThumbnail() {
        return Optional.ofNullable(thumbnail);
    }

    public Optional<Rating> getRating() {
        return Optional.ofNullable(rating);
    }

    public List<SiteLink> getSiteLinks() {
        return siteLinks;
    }

    @Override
    public Optional<SerpPosition> getPosition() {
        return Optional.ofNullable(position);
    }

    @Override
    public List<ResultProvenance> getProvenance() {
        return provenance;
    }

    @Override
    public Extensions getExtensions() {
        return extensions;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(id, type, url);
        builder.title = title;
        builder.snippet = snippet;
        builder.additionalSnippets.addAll(additionalSnippets);
        builder.highlights.addAll(highlights);
        builder.displayUrl = displayUrl;
        builder.domain = domain;
        builder.siteName = siteName;
        builder.publishedDate = publishedDate;
        builder.language = language;
        builder.thumbnail = thumbnail;
        builder.rating = rating;
        builder.siteLinks.addAll(siteLinks);
        builder.position = position;
        builder.provenance.addAll(provenance);
        builder.extensions = extensions;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchResultItem)) {
            return false;
        }
        SearchResultItem that = (SearchResultItem) o;
        return id.equals(that.id)
                && type.equals(that.type)
                && url.equals(that.url)
                && Objects.equals(title, that.title)
                && Objects.equals(snippet, that.snippet)
                && additionalSnippets.equals(that.additionalSnippets)
                && highlights.equals(that.highlights)
                && Objects.equals(displayUrl, that.displayUrl)
                && Objects.equals(domain, that.domain)
                && Objects.equals(siteName, that.siteName)
                && Objects.equals(publishedDate, that.publishedDate)
                && Objects.equals(language, that.language)
                && Objects.equals(thumbnail, that.thumbnail)
                && Objects.equals(rating, that.rating)
                && siteLinks.equals(that.siteLinks)
                && Objects.equals(position, that.position)
                && provenance.equals(that.provenance)
                && extensions.equals(that.extensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, url, title, snippet, additionalSnippets, highlights, displayUrl, domain,
                siteName, publishedDate, language, thumbnail, rating, siteLinks, position, provenance, extensions);
    }

    @Override
    public String toString() {
        return "SearchResultItem{id=" + id + ", type=" + type + ", url='" + url + "', title=" + title
                + ", position=" + position + '}';
    }

    /** Builder for {@link SearchResultItem}; blank texts are treated as absent. */
    public static final class Builder {
        private final ElementId id;
        private final ResultType type;
        private final String url;
        private String title;
        private String snippet;
        private final List<String> additionalSnippets = new ArrayList<String>();
        private final List<TextRange> highlights = new ArrayList<TextRange>();
        private String displayUrl;
        private String domain;
        private String siteName;
        private PublishedDate publishedDate;
        private String language;
        private MediaInfo thumbnail;
        private Rating rating;
        private final List<SiteLink> siteLinks = new ArrayList<SiteLink>();
        private SerpPosition position;
        private final List<ResultProvenance> provenance = new ArrayList<ResultProvenance>();
        private Extensions extensions = Extensions.empty();

        private Builder(ElementId id, ResultType type, String url) {
            this.id = Preconditions.requireNonNull(id, "id");
            this.type = Preconditions.requireNonNull(type, "type");
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

        public Builder additionalSnippet(String snippet) {
            String value = Preconditions.trimToNull(snippet);
            if (value != null) {
                additionalSnippets.add(value);
            }
            return this;
        }

        public Builder additionalSnippets(Collection<String> snippets) {
            for (String snippet : Preconditions.requireNonNull(snippets, "snippets")) {
                additionalSnippet(snippet);
            }
            return this;
        }

        public Builder highlight(TextRange range) {
            highlights.add(Preconditions.requireNonNull(range, "range"));
            return this;
        }

        public Builder highlights(Collection<TextRange> ranges) {
            for (TextRange range : Preconditions.requireNonNull(ranges, "ranges")) {
                highlight(range);
            }
            return this;
        }

        public Builder displayUrl(String displayUrl) {
            this.displayUrl = Preconditions.trimToNull(displayUrl);
            return this;
        }

        public Builder domain(String domain) {
            this.domain = Preconditions.trimToNull(domain);
            return this;
        }

        public Builder siteName(String siteName) {
            this.siteName = Preconditions.trimToNull(siteName);
            return this;
        }

        public Builder publishedDate(PublishedDate publishedDate) {
            this.publishedDate = publishedDate;
            return this;
        }

        public Builder language(String language) {
            this.language = Preconditions.trimToNull(language);
            return this;
        }

        public Builder thumbnail(MediaInfo thumbnail) {
            this.thumbnail = thumbnail;
            return this;
        }

        public Builder rating(Rating rating) {
            this.rating = rating;
            return this;
        }

        public Builder siteLink(SiteLink link) {
            siteLinks.add(Preconditions.requireNonNull(link, "link"));
            return this;
        }

        public Builder siteLinks(Collection<SiteLink> links) {
            for (SiteLink link : Preconditions.requireNonNull(links, "links")) {
                siteLink(link);
            }
            return this;
        }

        public Builder position(SerpPosition position) {
            this.position = position;
            return this;
        }

        public Builder provenance(ResultProvenance origin) {
            provenance.add(Preconditions.requireNonNull(origin, "origin"));
            return this;
        }

        public Builder provenance(Collection<ResultProvenance> origins) {
            for (ResultProvenance origin : Preconditions.requireNonNull(origins, "origins")) {
                provenance(origin);
            }
            return this;
        }

        public Builder clearProvenance() {
            provenance.clear();
            return this;
        }

        public Builder extensions(Extensions extensions) {
            this.extensions = Preconditions.requireNonNull(extensions, "extensions");
            return this;
        }

        public SearchResultItem build() {
            return new SearchResultItem(this);
        }
    }
}
