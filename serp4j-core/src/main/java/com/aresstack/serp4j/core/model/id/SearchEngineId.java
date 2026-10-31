package com.aresstack.serp4j.core.model.id;

import com.aresstack.serp4j.core.internal.Codes;

/**
 * Identifier of a search engine whose index or result page is delivered, for example {@code google}.
 *
 * <p>Open value type (ADR-004) with constants for the well-known engines. The same engine can be served
 * by several providers (Google via Bright Data, DataForSEO or a browser), which is why provider and engine
 * are separate concepts. There is no {@code MULTI} pseudo engine.</p>
 */
public final class SearchEngineId {

    public static final SearchEngineId GOOGLE = of("google");
    public static final SearchEngineId BING = of("bing");
    public static final SearchEngineId BRAVE = of("brave");
    public static final SearchEngineId DUCKDUCKGO = of("duckduckgo");
    public static final SearchEngineId YAHOO = of("yahoo");
    public static final SearchEngineId YANDEX = of("yandex");
    public static final SearchEngineId BAIDU = of("baidu");
    public static final SearchEngineId NAVER = of("naver");
    public static final SearchEngineId SEZNAM = of("seznam");

    private final String code;

    private SearchEngineId(String code) {
        this.code = code;
    }

    /** Creates an identifier from its code; the code is trimmed and lower-cased. */
    public static SearchEngineId of(String code) {
        return new SearchEngineId(Codes.normalize(code, "search engine id"));
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof SearchEngineId && code.equals(((SearchEngineId) o).code));
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
