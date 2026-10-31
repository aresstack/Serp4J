package com.aresstack.serp4j.browser;

/**
 * Generic obstacle signal of a navigation (WP3 §5.2). The browser port reports only what every backend can see;
 * engine-specific detection (consent pages, challenge pages) belongs to the engine adapter.
 */
public enum PageObstacle {
    /** Nothing noticeable. */
    NONE,
    /** The main document answered with an HTTP status of 400 or higher. */
    HTTP_ERROR,
    /** The navigation ended on a different host than requested (for example a consent or challenge host). */
    FOREIGN_HOST_REDIRECT
}
