package com.aresstack.serp4j.core.port;

/** Technical kind of a provider; closed set (ADR-004). */
public enum ProviderKind {
    /** Calls a hosted search API over HTTP (Brave, Bright Data, DataForSEO). */
    CLOUD_API,
    /** Drives a browser against a search engine's web UI. */
    BROWSER,
    /** Runs in-process without network, e.g. an index or a fixture replay. */
    LOCAL,
    /** Test double. */
    MOCK
}
