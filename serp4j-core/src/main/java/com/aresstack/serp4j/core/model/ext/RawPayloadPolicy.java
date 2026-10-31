package com.aresstack.serp4j.core.model.ext;

/**
 * Controls whether a provider keeps the raw response ({@link RawPayload}) in {@code SearchMetadata} (ADR-006).
 * The library default is {@link #NONE}; hosts that need the raw answer (AskAI) request {@link #ALWAYS}.
 */
public enum RawPayloadPolicy {
    /** Never keep the raw response (default). */
    NONE,
    /** Keep the raw response only when the request fails or the response cannot be mapped completely. */
    ON_ERROR,
    /** Always keep the raw response. */
    ALWAYS
}
