package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;
import java.util.Optional;

/** A related search suggestion with an optional link to its result page. */
public final class RelatedQuery {

    private final String text;
    private final String url;

    private RelatedQuery(String text, String url) {
        this.text = text;
        this.url = url;
    }

    public static RelatedQuery of(String text) {
        return of(text, null);
    }

    public static RelatedQuery of(String text, String url) {
        return new RelatedQuery(Preconditions.requireText(text, "text"), Preconditions.trimToNull(url));
    }

    public String getText() {
        return text;
    }

    public Optional<String> getUrl() {
        return Optional.ofNullable(url);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RelatedQuery)) {
            return false;
        }
        RelatedQuery that = (RelatedQuery) o;
        return text.equals(that.text) && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, url);
    }

    @Override
    public String toString() {
        return "RelatedQuery{text='" + text + "'}";
    }
}
