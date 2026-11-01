package com.aresstack.serp4j.testkit.provider;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@link com.aresstack.serp4j.core.port.SearchProvider} mock that plays a fixed script: the n-th call that passes
 * the contract checks gets the n-th {@link Outcome}. After the script, every call gets the fallback outcome – by
 * default an {@code INTERNAL} failure that names the exhausted script, so unexpected extra calls are visible.
 *
 * <pre>{@code
 * ScriptedSearchProvider flaky = ScriptedSearchProvider.builder(ProviderId.of("flaky"))
 *         .then(Outcome.failure(SearchErrorCategory.TIMEOUT))
 *         .then(Outcome.failure(SearchErrorCategory.UNAVAILABLE))
 *         .then(Outcome.syntheticResults(2))
 *         .build();
 * }</pre>
 */
public final class ScriptedSearchProvider extends AbstractMockSearchProvider {

    private final List<Outcome> script;
    private final Outcome afterScript;

    private ScriptedSearchProvider(Builder builder) {
        super(builder);
        this.script = Collections.unmodifiableList(new ArrayList<Outcome>(builder.script));
        this.afterScript = builder.afterScript != null ? builder.afterScript
                : Outcome.failure(SearchErrorCategory.INTERNAL,
                "script of " + script.size() + " outcome(s) exhausted (ScriptedSearchProvider)");
    }

    public static Builder builder(ProviderId id) {
        return new Builder(id);
    }

    @Override
    protected Outcome nextOutcome(SearchRequest request, int callIndex) {
        return callIndex < script.size() ? script.get(callIndex) : afterScript;
    }

    /** The script as configured. */
    public List<Outcome> getScript() {
        return script;
    }

    /**
     * Number of scripted outcomes not consumed yet. Calls rejected before the outcome (closed, unsupported,
     * cancelled, infrastructure unavailable) do not consume a step.
     */
    public int getRemainingSteps() {
        return Math.max(0, script.size() - getOutcomeCount());
    }

    @Override
    public String toString() {
        return "ScriptedSearchProvider{" + getDescriptor().getId() + ", steps=" + script.size() + '}';
    }

    /** Builder; see {@link AbstractMockSearchProvider.AbstractBuilder} for the shared options. */
    public static final class Builder extends AbstractBuilder<Builder> {
        private final List<Outcome> script = new ArrayList<Outcome>();
        private Outcome afterScript;

        private Builder(ProviderId id) {
            super(id);
        }

        @Override
        protected Builder self() {
            return this;
        }

        /** Appends the next step(s). */
        public Builder then(Outcome... outcomes) {
            for (Outcome outcome : Arrays.asList(outcomes)) {
                if (outcome == null) {
                    throw new NullPointerException("outcome must not be null");
                }
                script.add(outcome);
            }
            return this;
        }

        /** Outcome for every call after the script; default an {@code INTERNAL} failure. */
        public Builder thenAlways(Outcome outcome) {
            if (outcome == null) {
                throw new NullPointerException("outcome must not be null");
            }
            this.afterScript = outcome;
            return this;
        }

        public ScriptedSearchProvider build() {
            return new ScriptedSearchProvider(this);
        }
    }
}
