package com.aresstack.serp4j.core.model.data;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.Optional;

/** Additional link attached to a result or knowledge panel (deep links, profiles). */
public final class SiteLink {

    private final String url;
    private final String title;
    private final String description;

    private SiteLink(String url, String title, String description) {
        this.url = url;
        this.title = title;
        this.description = description;
    }

    public static SiteLink of(String url) {
        return of(url, null, null);
    }

    public static SiteLink of(String url, String title) {
        return of(url, title, null);
    }

    public static SiteLink of(String url, String title, String description) {
        return new SiteLink(Preconditions.requireText(url, "url"), Preconditions.trimToNull(title),
                Preconditions.trimToNull(description));
    }

    public String getUrl() {
        return url;
    }

    public Optional<String> getTitle() {
        return Optional.ofNullable(title);
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SiteLink)) {
            return false;
        }
        SiteLink that = (SiteLink) o;
        return url.equals(that.url)
                && Objects.equals(title, that.title)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, title, description);
    }

    @Override
    public String toString() {
        return "SiteLink{url='" + url + "', title=" + title + '}';
    }
}
