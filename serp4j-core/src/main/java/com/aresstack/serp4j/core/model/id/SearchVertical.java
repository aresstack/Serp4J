package com.aresstack.serp4j.core.model.id;

import com.aresstack.serp4j.core.internal.Codes;

/**
 * The kind of results or surface within an engine: web, news, images, videos, shopping, local or the
 * conversational AI surface.
 *
 * <p>Open value type (ADR-004), because providers know further verticals (Scholar, Maps, …). Google AI
 * Mode is modelled as engine {@link SearchEngineId#GOOGLE} with vertical {@link #AI_MODE} (ADR-005);
 * whether a provider offers AI Mode as a dialogue is expressed by the port it implements, not by the
 * vertical.</p>
 */
public final class SearchVertical {

    public static final SearchVertical WEB = of("web");
    public static final SearchVertical NEWS = of("news");
    public static final SearchVertical IMAGES = of("images");
    public static final SearchVertical VIDEOS = of("videos");
    public static final SearchVertical SHOPPING = of("shopping");
    public static final SearchVertical LOCAL = of("local");
    /** Conversational AI surface of an engine (Google AI Mode), see ADR-005. */
    public static final SearchVertical AI_MODE = of("ai_mode");

    private final String code;

    private SearchVertical(String code) {
        this.code = code;
    }

    /** Creates a vertical from its code; the code is trimmed and lower-cased. */
    public static SearchVertical of(String code) {
        return new SearchVertical(Codes.normalize(code, "search vertical"));
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof SearchVertical && code.equals(((SearchVertical) o).code));
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
