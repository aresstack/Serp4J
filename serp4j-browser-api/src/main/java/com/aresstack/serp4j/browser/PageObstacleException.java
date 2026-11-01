package com.aresstack.serp4j.browser;

/**
 * A page obstacle was not resolved: either reported at once ({@link ObstaclePolicy#failFast()}) or a human did not
 * resolve it within the waiting time ({@link ObstaclePolicy#waitForHuman(java.time.Duration)}). The session stays
 * usable.
 */
public final class PageObstacleException extends BrowserException {

    private static final long serialVersionUID = 1L;

    private final PageObstacle obstacle;
    private final boolean waitedForHuman;

    /**
     * @param obstacle       the obstacle, not {@link PageObstacle#NONE}
     * @param waitedForHuman whether the session waited for a human before giving up
     * @param message        message
     */
    public PageObstacleException(PageObstacle obstacle, boolean waitedForHuman, String message) {
        super(message);
        if (Args.notNull(obstacle, "obstacle") == PageObstacle.NONE) {
            throw new IllegalArgumentException("obstacle must not be NONE");
        }
        this.obstacle = obstacle;
        this.waitedForHuman = waitedForHuman;
    }

    /**
     * @return the unresolved obstacle
     */
    public PageObstacle getObstacle() {
        return obstacle;
    }

    /**
     * @return whether a human had the chance to resolve it
     */
    public boolean isWaitedForHuman() {
        return waitedForHuman;
    }
}
