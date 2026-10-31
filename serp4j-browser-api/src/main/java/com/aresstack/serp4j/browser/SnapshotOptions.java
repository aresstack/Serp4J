package com.aresstack.serp4j.browser;

/** What {@link BrowserSession#snapshot(SnapshotOptions)} captures. Immutable. */
public final class SnapshotOptions {

    /** Default for {@link #getMaxChars()}: 2 Mi characters. */
    public static final int DEFAULT_MAX_CHARS = 2 * 1024 * 1024;

    private static final SnapshotOptions URL_AND_TITLE = new SnapshotOptions(false, false, DEFAULT_MAX_CHARS);

    private final boolean includeHtml;
    private final boolean includeText;
    private final int maxChars;

    private SnapshotOptions(boolean includeHtml, boolean includeText, int maxChars) {
        this.includeHtml = includeHtml;
        this.includeText = includeText;
        this.maxChars = Args.positive(maxChars, "maxChars");
    }

    /**
     * @return URL and title only
     */
    public static SnapshotOptions urlAndTitle() {
        return URL_AND_TITLE;
    }

    /**
     * @return a copy that also captures the serialized DOM
     */
    public SnapshotOptions withHtml() {
        return new SnapshotOptions(true, includeText, maxChars);
    }

    /**
     * @return a copy that also captures the visible text of the body
     */
    public SnapshotOptions withText() {
        return new SnapshotOptions(includeHtml, true, maxChars);
    }

    /**
     * @param maxChars positive limit for HTML and text, each
     * @return a copy with the limit
     */
    public SnapshotOptions maxChars(int maxChars) {
        return new SnapshotOptions(includeHtml, includeText, maxChars);
    }

    /**
     * @return whether the serialized DOM is captured
     */
    public boolean isIncludeHtml() {
        return includeHtml;
    }

    /**
     * @return whether the visible text is captured
     */
    public boolean isIncludeText() {
        return includeText;
    }

    /**
     * @return limit for HTML and text, each; longer content is truncated
     */
    public int getMaxChars() {
        return maxChars;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SnapshotOptions)) {
            return false;
        }
        SnapshotOptions that = (SnapshotOptions) o;
        return includeHtml == that.includeHtml && includeText == that.includeText && maxChars == that.maxChars;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (includeHtml ? 1 : 0) + (includeText ? 1 : 0)) + maxChars;
    }

    @Override
    public String toString() {
        return "SnapshotOptions{html=" + includeHtml + ", text=" + includeText + ", maxChars=" + maxChars + '}';
    }
}
