package com.aresstack.serp4j.provider.dataforseo;

/** Result function of the DataForSEO organic live endpoints. HTML is deliberately not offered here. */
public enum DataForSeoFunction {
    /** Compact list: organic, paid and (Google) featured snippet only. */
    REGULAR("regular"),
    /** Full SERP structure including all SERP features. Default. */
    ADVANCED("advanced");

    private final String pathSegment;

    DataForSeoFunction(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    /** Path segment in {@code /v3/serp/{engine}/organic/live/{segment}}. */
    public String getPathSegment() {
        return pathSegment;
    }
}
