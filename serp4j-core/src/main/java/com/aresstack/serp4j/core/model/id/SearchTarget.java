package com.aresstack.serp4j.core.model.id;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Objects;

/**
 * What a caller wants: a {@link SearchEngineId} combined with a {@link SearchVertical}, for example
 * {@code google/web} or {@code google/ai_mode}. Providers declare the targets they can serve in their
 * capabilities; selection therefore happens over the pair (provider, target), never over the engine alone.
 */
public final class SearchTarget {

    private final SearchEngineId engine;
    private final SearchVertical vertical;

    private SearchTarget(SearchEngineId engine, SearchVertical vertical) {
        this.engine = engine;
        this.vertical = vertical;
    }

    public static SearchTarget of(SearchEngineId engine, SearchVertical vertical) {
        return new SearchTarget(Preconditions.requireNonNull(engine, "engine"),
                Preconditions.requireNonNull(vertical, "vertical"));
    }

    /** Shortcut for {@code of(engine, SearchVertical.WEB)}. */
    public static SearchTarget web(SearchEngineId engine) {
        return of(engine, SearchVertical.WEB);
    }

    public SearchEngineId getEngine() {
        return engine;
    }

    public SearchVertical getVertical() {
        return vertical;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchTarget)) {
            return false;
        }
        SearchTarget that = (SearchTarget) o;
        return engine.equals(that.engine) && vertical.equals(that.vertical);
    }

    @Override
    public int hashCode() {
        return Objects.hash(engine, vertical);
    }

    @Override
    public String toString() {
        return engine.getCode() + "/" + vertical.getCode();
    }
}
