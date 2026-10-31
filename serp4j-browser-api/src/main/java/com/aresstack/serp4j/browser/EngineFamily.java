package com.aresstack.serp4j.browser;

/** Rendering engine family of a browser backend. */
public enum EngineFamily {
    /** Chromium, Chrome, Edge. */
    CHROMIUM,
    /** Firefox and forks such as Camoufox. */
    FIREFOX,
    /** WebKit. */
    WEBKIT,
    /** A test double without a real engine. */
    NONE
}
