package com.aresstack.serp4j.browser.playwright;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkTrackerTest {

    @Test
    void far_oversized_bodies_are_not_loaded_but_moderately_large_ones_are_cut() {
        int max = 1024;
        assertThat(NetworkTracker.tooLargeToLoad(-1L, max)).as("unknown size").isFalse();
        assertThat(NetworkTracker.tooLargeToLoad(64L * 1024, max)).as("cut to the limit").isFalse();
        assertThat(NetworkTracker.tooLargeToLoad(200L * 1024 * 1024, max)).as("200 MiB").isTrue();
        assertThat(NetworkTracker.tooLargeToLoad(NetworkTracker.LOAD_CEILING_BYTES + 1, max)).isTrue();
        int large = 8 * 1024 * 1024;
        assertThat(NetworkTracker.tooLargeToLoad(30L * 1024 * 1024, large)).isFalse();
        assertThat(NetworkTracker.tooLargeToLoad(33L * 1024 * 1024, large)).isTrue();
    }

    @Test
    void content_length_is_parsed_leniently() {
        assertThat(NetworkTracker.declaredLength(" 42 ")).isEqualTo(42L);
        assertThat(NetworkTracker.declaredLength(null)).isEqualTo(-1L);
        assertThat(NetworkTracker.declaredLength("x")).isEqualTo(-1L);
    }
}
