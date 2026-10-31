package com.aresstack.serp4j.core.port.capability;

/** What a provider's responses typically contain (WP2 §6); used for provider selection and expectations. */
public enum ResponseFeature {
    ORGANIC_RESULTS,
    /** General SERP elements beyond organic hits. */
    SERP_FEATURES,
    KNOWLEDGE_GRAPH,
    AI_OVERVIEW,
    /** Answer text of an AI search (AI Mode or similar). */
    AI_ANSWER,
    CITED_SOURCES,
    RELATED_QUERIES,
    TOTAL_RESULT_COUNT,
    /** Cost and quota information in the metadata. */
    PROVIDER_USAGE,
    /** The raw provider payload can be attached to the metadata. */
    RAW_PAYLOAD
}
