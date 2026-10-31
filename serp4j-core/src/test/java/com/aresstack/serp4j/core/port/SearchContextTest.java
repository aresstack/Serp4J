package com.aresstack.serp4j.core.port;

import com.aresstack.serp4j.core.model.id.ProviderId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchContextTest {

    @Test
    void defaultsHaveNoDeadlineAndGeneratedCorrelationId() {
        SearchContext context = SearchContext.defaults();

        assertThat(context.getDeadline()).isEmpty();
        assertThat(context.getCancellation()).isSameAs(CancellationSignal.NONE);
        assertThat(context.getCorrelationId()).isNotBlank();
        assertThat(context.getBudget()).isEmpty();
        assertThat(context.getRemainingTime(Instant.now())).isEmpty();
        assertThat(SearchContext.builder().build().getCorrelationId()).isNotEqualTo(context.getCorrelationId());
    }

    @Test
    void deadlineDerivationKeepsTheEarlierOne() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        SearchContext context = SearchContext.builder().timeout(now, Duration.ofSeconds(30))
                .correlationId("corr-1").attribute("tenant", "t1").build();

        assertThat(context.getDeadline()).contains(now.plusSeconds(30));
        assertThat(context.getRemainingTime(now.plusSeconds(10))).contains(Duration.ofSeconds(20));
        assertThat(context.getRemainingTime(now.plusSeconds(40))).contains(Duration.ZERO);
        assertThat(context.isExpired(now.plusSeconds(30))).isTrue();
        assertThat(context.withDeadline(now.plusSeconds(5)).getDeadline()).contains(now.plusSeconds(5));
        assertThat(context.withDeadline(now.plusSeconds(60)).getDeadline()).contains(now.plusSeconds(30));
        assertThat(context.withDeadline(now.plusSeconds(5)).getCorrelationId()).isEqualTo("corr-1");
        assertThat(context.getAttributes()).containsEntry("tenant", "t1");
    }

    @Test
    void cancellationSourceRunsCallbacksOnceAndImmediatelyWhenAlreadyCancelled() {
        CancellationSource source = new CancellationSource();
        List<String> events = new ArrayList<String>();
        source.getSignal().onCancel(() -> events.add("first"));
        source.getSignal().onCancel(() -> {
            throw new IllegalStateException("boom");
        });
        source.getSignal().onCancel(() -> events.add("third"));

        assertThat(source.getSignal().isCancelled()).isFalse();
        assertThatThrownBy(source::cancel).isInstanceOf(IllegalStateException.class).hasMessage("boom");
        source.cancel();

        assertThat(events).containsExactly("first", "third");
        assertThat(source.getSignal().isCancelled()).isTrue();
        source.getSignal().onCancel(() -> events.add("late"));
        assertThat(events).containsExactly("first", "third", "late");
    }

    @Test
    void budgetIsCheckedThroughTheContext() {
        SearchBudget twoCalls = new SearchBudget() {
            private int remaining = 2;

            @Override
            public synchronized boolean tryAcquire(ProviderId provider, int units) {
                if (remaining >= units) {
                    remaining -= units;
                    return true;
                }
                return false;
            }
        };
        SearchContext context = SearchContext.builder().budget(twoCalls).build();

        assertThat(context.getBudget().get().tryAcquire(ProviderId.of("a"), 1)).isTrue();
        assertThat(context.getBudget().get().tryAcquire(ProviderId.of("b"), 2)).isFalse();
    }
}
