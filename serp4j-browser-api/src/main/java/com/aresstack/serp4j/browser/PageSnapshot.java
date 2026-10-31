package com.aresstack.serp4j.browser;

import java.time.Instant;
import java.util.Optional;

/** Rendered state of a page (WP3 §5.1). Immutable. */
public final class PageSnapshot {

    private final String url;
    private final String title;
    private final String html;
    private final boolean htmlTruncated;
    private final String text;
    private final boolean textTruncated;
    private final Instant capturedAt;

    private PageSnapshot(Builder builder) {
        this.url = Args.notBlank(builder.url, "url");
        this.title = builder.title == null ? "" : builder.title;
        this.html = builder.html;
        this.htmlTruncated = builder.htmlTruncated;
        this.text = builder.text;
        this.textTruncated = builder.textTruncated;
        this.capturedAt = Args.notNull(builder.capturedAt, "capturedAt");
    }

    /**
     * @param url        page URL
     * @param capturedAt capture time
     * @return a builder
     */
    public static Builder builder(String url, Instant capturedAt) {
        return new Builder(url, capturedAt);
    }

    /**
     * @return page URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return document title; empty if none
     */
    public String getTitle() {
        return title;
    }

    /**
     * @return serialized DOM if requested
     */
    public Optional<String> getHtml() {
        return Optional.ofNullable(html);
    }

    /**
     * @return whether the HTML was cut at {@link SnapshotOptions#getMaxChars()}
     */
    public boolean isHtmlTruncated() {
        return htmlTruncated;
    }

    /**
     * @return visible text of the body if requested
     */
    public Optional<String> getText() {
        return Optional.ofNullable(text);
    }

    /**
     * @return whether the text was cut at {@link SnapshotOptions#getMaxChars()}
     */
    public boolean isTextTruncated() {
        return textTruncated;
    }

    /**
     * @return capture time
     */
    public Instant getCapturedAt() {
        return capturedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PageSnapshot)) {
            return false;
        }
        PageSnapshot that = (PageSnapshot) o;
        return htmlTruncated == that.htmlTruncated && textTruncated == that.textTruncated && url.equals(that.url)
                && title.equals(that.title) && (html == null ? that.html == null : html.equals(that.html))
                && (text == null ? that.text == null : text.equals(that.text)) && capturedAt.equals(that.capturedAt);
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + title.hashCode();
        result = 31 * result + (html == null ? 0 : html.hashCode());
        result = 31 * result + (text == null ? 0 : text.hashCode());
        return 31 * result + capturedAt.hashCode();
    }

    /** Shows sizes only, never page content. */
    @Override
    public String toString() {
        return "PageSnapshot{url=" + url + ", title=" + title
                + ", html=" + (html == null ? "none" : html.length() + " chars" + (htmlTruncated ? " (truncated)" : ""))
                + ", text=" + (text == null ? "none" : text.length() + " chars" + (textTruncated ? " (truncated)" : ""))
                + ", capturedAt=" + capturedAt + '}';
    }

    /** Builder for {@link PageSnapshot}. */
    public static final class Builder {
        private final String url;
        private final Instant capturedAt;
        private String title;
        private String html;
        private boolean htmlTruncated;
        private String text;
        private boolean textTruncated;

        private Builder(String url, Instant capturedAt) {
            this.url = url;
            this.capturedAt = capturedAt;
        }

        /**
         * @param title document title
         * @return this builder
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Sets the HTML, truncated to {@code maxChars}.
         *
         * @param html     serialized DOM
         * @param maxChars limit
         * @return this builder
         */
        public Builder html(String html, int maxChars) {
            this.htmlTruncated = html != null && html.length() > maxChars;
            this.html = htmlTruncated ? html.substring(0, maxChars) : html;
            return this;
        }

        /**
         * Sets the visible text, truncated to {@code maxChars}.
         *
         * @param text     visible text
         * @param maxChars limit
         * @return this builder
         */
        public Builder text(String text, int maxChars) {
            this.textTruncated = text != null && text.length() > maxChars;
            this.text = textTruncated ? text.substring(0, maxChars) : text;
            return this;
        }

        /**
         * @return the snapshot
         */
        public PageSnapshot build() {
            return new PageSnapshot(this);
        }
    }
}
