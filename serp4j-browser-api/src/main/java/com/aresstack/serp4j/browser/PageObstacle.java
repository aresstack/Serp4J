package com.aresstack.serp4j.browser;

/**
 * Obstacle on a page (WP3 §5.2). {@link NavigationResult#getObstacle()} reports only the generic signals every
 * backend can see ({@link #NONE}, {@link #HTTP_ERROR}, {@link #FOREIGN_HOST_REDIRECT}); the engine-specific kinds
 * ({@link #CONSENT}, {@link #CHALLENGE}, {@link #BLOCKED}, {@link #UNKNOWN}) are detected by the engine adapter and
 * handed to {@link BrowserSession#resolveObstacle(PageObstacle, WaitCondition)}.
 */
public enum PageObstacle {
    /** Nothing noticeable. */
    NONE,
    /** The main document answered with an HTTP status of 400 or higher. */
    HTTP_ERROR,
    /** The navigation ended on a different host than requested (for example a consent or challenge host). */
    FOREIGN_HOST_REDIRECT,
    /** A consent page (detected by the engine adapter). */
    CONSENT,
    /** A challenge such as a CAPTCHA or rate-limit page (detected by the engine adapter); never solved by Serp4J. */
    CHALLENGE,
    /** Access was denied (detected by the engine adapter). */
    BLOCKED,
    /** Some other obstacle the engine adapter cannot classify. */
    UNKNOWN
}
