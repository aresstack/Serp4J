package com.aresstack.serp4j.browser;

/** Outcome of a wait. */
public enum WaitOutcome {
    /** The condition held within the timeout. */
    SATISFIED,
    /** The timeout expired first. */
    TIMED_OUT;

    /**
     * @return whether the condition held
     */
    public boolean isSatisfied() {
        return this == SATISFIED;
    }
}
