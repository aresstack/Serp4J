package com.aresstack.serp4j.core.port;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationSourceTest {

    @Test
    void cancelRunsRemainingCallbacksWhenOneThrowsAnErrorAndRethrowsIt() {
        CancellationSource source = new CancellationSource();
        List<String> events = new ArrayList<String>();
        source.getSignal().onCancel(() -> {
            events.add("first");
            throw new AssertionError("callback error");
        });
        source.getSignal().onCancel(() -> events.add("second"));

        assertThatThrownBy(source::cancel).isInstanceOf(AssertionError.class).hasMessage("callback error");

        assertThat(events).containsExactly("first", "second");
        assertThat(source.isCancelled()).isTrue();
        assertThat(source.getSignal().isCancelled()).isTrue();
        source.cancel();
        assertThat(events).containsExactly("first", "second");
    }

    @Test
    void cancelRethrowsTheFirstThrowableAndDropsLaterOnes() {
        CancellationSource source = new CancellationSource();
        List<String> events = new ArrayList<String>();
        source.getSignal().onCancel(() -> {
            throw new IllegalStateException("first failure");
        });
        source.getSignal().onCancel(() -> {
            throw new AssertionError("second failure");
        });
        source.getSignal().onCancel(() -> events.add("third"));

        assertThatThrownBy(source::cancel).isInstanceOf(IllegalStateException.class).hasMessage("first failure");

        assertThat(events).containsExactly("third");
    }
}
