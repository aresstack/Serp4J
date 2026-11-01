package com.aresstack.serp4j.browser;

import java.time.Duration;
import java.util.Optional;

/** Options of {@link BrowserSession#click}, {@link BrowserSession#type} and {@link BrowserSession#press}. Immutable. */
public final class InteractionOptions {

    private static final InteractionOptions DEFAULTS = new InteractionOptions(null, null);

    private final Duration timeout;
    private final Duration typingDelay;

    private InteractionOptions(Duration timeout, Duration typingDelay) {
        this.timeout = timeout;
        this.typingDelay = typingDelay;
    }

    /**
     * @return session default timeout, text replaced at once
     */
    public static InteractionOptions defaults() {
        return DEFAULTS;
    }

    /**
     * @param timeout positive timeout for finding an actionable element
     * @return a copy with the timeout
     */
    public InteractionOptions timeout(Duration timeout) {
        return new InteractionOptions(Args.positive(timeout, "timeout"), typingDelay);
    }

    /**
     * @param typingDelay delay between key strokes; {@code null} replaces the value at once
     * @return a copy with the typing delay
     */
    public InteractionOptions typingDelay(Duration typingDelay) {
        return new InteractionOptions(timeout, typingDelay == null ? null : Args.notNegative(typingDelay, "typingDelay"));
    }

    /**
     * @return the timeout; empty = session default
     */
    public Optional<Duration> getTimeout() {
        return Optional.ofNullable(timeout);
    }

    /**
     * @return delay between key strokes; empty = replace the value at once
     */
    public Optional<Duration> getTypingDelay() {
        return Optional.ofNullable(typingDelay);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InteractionOptions)) {
            return false;
        }
        InteractionOptions that = (InteractionOptions) o;
        return (timeout == null ? that.timeout == null : timeout.equals(that.timeout))
                && (typingDelay == null ? that.typingDelay == null : typingDelay.equals(that.typingDelay));
    }

    @Override
    public int hashCode() {
        return 31 * (timeout == null ? 0 : timeout.hashCode()) + (typingDelay == null ? 0 : typingDelay.hashCode());
    }

    @Override
    public String toString() {
        return "InteractionOptions{timeout=" + timeout + ", typingDelay=" + typingDelay + '}';
    }
}
