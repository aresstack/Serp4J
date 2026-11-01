package com.aresstack.serp4j.browser;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileAndObstacleOptionsTest {

    @Test
    void defaults_are_ephemeral_and_fail_fast_without_implied_capabilities() {
        BrowserSessionOptions options = BrowserSessionOptions.defaults();
        assertThat(options.getProfileMode()).isEqualTo(ProfileMode.ephemeral());
        assertThat(options.getProfileMode().isPersistent()).isFalse();
        assertThat(options.getObstaclePolicy()).isEqualTo(ObstaclePolicy.failFast());
        assertThat(options.getRequiredCapabilities()).isEmpty();
    }

    @Test
    void persistent_profile_and_waiting_for_a_human_imply_capabilities() {
        BrowserSessionOptions options = BrowserSessionOptions.builder()
                .profileMode(ProfileMode.persistentExclusive("google-de"))
                .obstaclePolicy(ObstaclePolicy.waitForHuman(Duration.ofMinutes(2)))
                .build();
        assertThat(options.getRequiredCapabilities())
                .containsExactlyInAnyOrder(BrowserCapability.PERSISTENT_PROFILE, BrowserCapability.HEADED_MODE);
        assertThat(options.getProfileMode().getProfileKey()).contains("google-de");
        assertThat(options.getObstaclePolicy().getHumanTimeout()).contains(Duration.ofMinutes(2));
    }

    @Test
    void implied_capabilities_do_not_stick_when_the_mode_changes() {
        BrowserSessionOptions persistent = BrowserSessionOptions.builder()
                .profileMode(ProfileMode.persistentExclusive("p")).build();
        BrowserSessionOptions ephemeral = persistent.toBuilder().profileMode(ProfileMode.ephemeral()).build();
        assertThat(ephemeral.getRequiredCapabilities()).isEmpty();
        assertThat(ephemeral).isEqualTo(BrowserSessionOptions.defaults());
    }

    @Test
    void profile_keys_are_safe_directory_names() {
        assertThatThrownBy(() -> ProfileMode.persistentExclusive("../etc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProfileMode.persistentExclusive("a/b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProfileMode.persistentExclusive(".hidden"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProfileMode.persistentExclusive("")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ProfileMode.persistentExclusive("Google_DE-1.0").toString())
                .isEqualTo("PERSISTENT_EXCLUSIVE(Google_DE-1.0)");
    }

    @Test
    void wait_for_human_needs_a_positive_timeout_and_obstacle_exceptions_never_carry_none() {
        assertThatThrownBy(() -> ObstaclePolicy.waitForHuman(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageObstacleException(PageObstacle.NONE, false, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ObstaclePolicy.waitForHuman(Duration.ofSeconds(5)).toString()).isEqualTo("WAIT_FOR_HUMAN(PT5S)");
    }
}
