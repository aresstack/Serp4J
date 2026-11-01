package com.aresstack.serp4j.testkit.provider;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;

import java.util.function.Function;

/**
 * {@link com.aresstack.serp4j.core.port.SearchProvider} mock with one answer rule for every call; the rule can be
 * swapped while the test runs, e.g. to let a provider fail halfway through an orchestration test.
 *
 * <pre>{@code
 * MockSearchProvider brave = MockSearchProvider.builder(ProviderId.of("brave"))
 *         .latency(Duration.ofMillis(50))
 *         .answer(Outcome.syntheticResults(5))
 *         .build();
 * brave.setAnswer(Outcome.failure(SearchErrorCategory.RATE_LIMITED));
 * }</pre>
 *
 * <p>Default answer: three synthetic organic results ({@link SyntheticResults}).</p>
 */
public final class MockSearchProvider extends AbstractMockSearchProvider {

    private volatile Function<SearchRequest, Outcome> answer;

    private MockSearchProvider(Builder builder) {
        super(builder);
        this.answer = builder.answer;
    }

    public static Builder builder(ProviderId id) {
        return new Builder(id);
    }

    /** A mock with default capabilities that answers every call with three synthetic results. */
    public static MockSearchProvider create(ProviderId id) {
        return builder(id).build();
    }

    /** A mock that fails every call with {@code category}. */
    public static MockSearchProvider failing(ProviderId id, SearchErrorCategory category) {
        return builder(id).answer(Outcome.failure(category)).build();
    }

    /** Replaces the answer for all following calls. */
    public void setAnswer(Outcome outcome) {
        setAnswer(constant(outcome));
    }

    /** Replaces the answer rule for all following calls. */
    public void setAnswer(Function<SearchRequest, Outcome> rule) {
        if (rule == null) {
            throw new NullPointerException("rule must not be null");
        }
        this.answer = rule;
    }

    @Override
    protected Outcome nextOutcome(SearchRequest request, int callIndex) {
        return answer.apply(request);
    }

    private static Function<SearchRequest, Outcome> constant(final Outcome outcome) {
        if (outcome == null) {
            throw new NullPointerException("outcome must not be null");
        }
        return new Function<SearchRequest, Outcome>() {
            @Override
            public Outcome apply(SearchRequest request) {
                return outcome;
            }
        };
    }

    /** Builder; see {@link AbstractMockSearchProvider.AbstractBuilder} for the shared options. */
    public static final class Builder extends AbstractBuilder<Builder> {
        private Function<SearchRequest, Outcome> answer = constant(Outcome.syntheticResults(3));

        private Builder(ProviderId id) {
            super(id);
        }

        @Override
        protected Builder self() {
            return this;
        }

        /** Answers every call with {@code outcome}. */
        public Builder answer(Outcome outcome) {
            this.answer = constant(outcome);
            return this;
        }

        /** Chooses the outcome per request, e.g. by query or vertical. */
        public Builder answer(Function<SearchRequest, Outcome> rule) {
            if (rule == null) {
                throw new NullPointerException("rule must not be null");
            }
            this.answer = rule;
            return this;
        }

        public MockSearchProvider build() {
            return new MockSearchProvider(this);
        }
    }
}
