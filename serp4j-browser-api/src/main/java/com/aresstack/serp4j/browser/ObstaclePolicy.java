package com.aresstack.serp4j.browser;

import java.time.Duration;
import java.util.Optional;

/**
 * What a session does with a page obstacle such as a consent page or a challenge (WP3 B-9, §5.2). Serp4J never
 * solves CAPTCHAs or bypasses protections: {@link #failFast()} (default) reports the obstacle at once;
 * {@link #waitForHuman(Duration)} gives a human the chance to resolve it in the visible browser window and is only
 * allowed in headed mode, so it requires {@link BrowserCapability#HEADED_MODE}. Applied by
 * {@link BrowserSession#resolveObstacle(PageObstacle, WaitCondition)}. Immutable.
 */
public final class ObstaclePolicy {

    private static final ObstaclePolicy FAIL_FAST = new ObstaclePolicy(null);

    private final Duration humanTimeout;

    private ObstaclePolicy(Duration humanTimeout) {
        this.humanTimeout = humanTimeout;
    }

    /**
     * @return report obstacles at once (default)
     */
    public static ObstaclePolicy failFast() {
        return FAIL_FAST;
    }

    /**
     * @param timeout positive maximum time a human gets to resolve the obstacle in the visible window
     * @return wait for a human, visible mode only
     */
    public static ObstaclePolicy waitForHuman(Duration timeout) {
        return new ObstaclePolicy(Args.positive(timeout, "timeout"));
    }

    /**
     * @return whether the session waits for a human
     */
    public boolean isWaitForHuman() {
        return humanTimeout != null;
    }

    /**
     * @return the waiting time of {@link #waitForHuman(Duration)}; empty for {@link #failFast()}
     */
    public Optional<Duration> getHumanTimeout() {
        return Optional.ofNullable(humanTimeout);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ObstaclePolicy)) {
            return false;
        }
        ObstaclePolicy that = (ObstaclePolicy) o;
        return humanTimeout == null ? that.humanTimeout == null : humanTimeout.equals(that.humanTimeout);
    }

    @Override
    public int hashCode() {
        return humanTimeout == null ? 0 : humanTimeout.hashCode();
    }

    @Override
    public String toString() {
        return humanTimeout == null ? "FAIL_FAST" : "WAIT_FOR_HUMAN(" + humanTimeout + ")";
    }
}
