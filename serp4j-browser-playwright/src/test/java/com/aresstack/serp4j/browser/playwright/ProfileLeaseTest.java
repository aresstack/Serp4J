package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserProfileInUseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileLeaseTest {

    @TempDir
    Path root;

    @Test
    void a_profile_is_leased_exclusively_until_released() {
        ProfileLease first = ProfileLease.acquire(root, "google-de");
        assertThat(first.getDirectory()).isEqualTo(root.resolve("google-de"));
        assertThat(Files.isRegularFile(first.getDirectory().resolve(ProfileLease.LOCK_FILE))).isTrue();
        assertThatThrownBy(() -> ProfileLease.acquire(root, "google-de"))
                .isInstanceOfSatisfying(BrowserProfileInUseException.class,
                        e -> assertThat(e.getProfileKey()).isEqualTo("google-de"));
        ProfileLease other = ProfileLease.acquire(root, "google-at");
        other.release();
        first.release();
        first.release();
        ProfileLease again = ProfileLease.acquire(root, "google-de");
        again.release();
    }

    @Test
    void persistent_profiles_are_a_capability_of_local_runtimes_with_a_profile_directory() {
        PlaywrightBrowserRuntime without = PlaywrightBrowserRuntime.builder().build();
        PlaywrightBrowserRuntime with = PlaywrightBrowserRuntime.builder().profileDirectory(root).build();
        try {
            assertThat(without.getInfo().supports(com.aresstack.serp4j.browser.BrowserCapability.PERSISTENT_PROFILE))
                    .isFalse();
            assertThat(with.getInfo().supports(com.aresstack.serp4j.browser.BrowserCapability.PERSISTENT_PROFILE))
                    .isTrue();
        } finally {
            without.close();
            with.close();
        }
        assertThatThrownBy(() -> RemotePlaywrightRuntime.builder("ws://127.0.0.1:1/x").profileDirectory(root).build())
                .isInstanceOf(IllegalStateException.class);
    }
}
