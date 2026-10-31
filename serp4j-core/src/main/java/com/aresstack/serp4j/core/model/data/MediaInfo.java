package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.OptionalInt;

/** An image or thumbnail: URL and optional pixel dimensions. */
public final class MediaInfo {

    private final String url;
    private final Integer width;
    private final Integer height;

    private MediaInfo(String url, Integer width, Integer height) {
        this.url = url;
        this.width = width;
        this.height = height;
    }

    public static MediaInfo of(String url) {
        return new MediaInfo(Preconditions.requireText(url, "url"), null, null);
    }

    public static MediaInfo of(String url, int width, int height) {
        return new MediaInfo(Preconditions.requireText(url, "url"),
                Preconditions.requirePositive(width, "width"),
                Preconditions.requirePositive(height, "height"));
    }

    public String getUrl() {
        return url;
    }

    public OptionalInt getWidth() {
        return width == null ? OptionalInt.empty() : OptionalInt.of(width);
    }

    public OptionalInt getHeight() {
        return height == null ? OptionalInt.empty() : OptionalInt.of(height);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MediaInfo)) {
            return false;
        }
        MediaInfo that = (MediaInfo) o;
        return url.equals(that.url) && Objects.equals(width, that.width) && Objects.equals(height, that.height);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, width, height);
    }

    @Override
    public String toString() {
        return "MediaInfo{url='" + url + "'" + (width == null ? "" : ", " + width + "x" + height) + '}';
    }
}
