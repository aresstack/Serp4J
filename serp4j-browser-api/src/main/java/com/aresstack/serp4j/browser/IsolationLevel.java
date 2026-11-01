package com.aresstack.serp4j.browser;

/** How sessions of a runtime are isolated from each other (WP3 B-7). */
public enum IsolationLevel {
    /** One browser context per session inside a shared browser process (Chromium). */
    CONTEXT,
    /** One browser process per session (Camoufox with a persistent context). */
    PROCESS
}
