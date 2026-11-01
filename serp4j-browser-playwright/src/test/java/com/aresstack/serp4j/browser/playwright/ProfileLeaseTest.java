package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserProfileInUseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
    void a_relative_profile_root_such_as_the_working_directory_works() throws Exception {
        String key = "serp4j-lease-test-" + System.nanoTime();
        ProfileLease lease = ProfileLease.acquire(Paths.get(""), key);
        try {
            assertThat(lease.getDirectory()).isEqualTo(Paths.get("").toAbsolutePath().resolve(key));
        } finally {
            lease.release();
            Files.deleteIfExists(lease.getDirectory().resolve(ProfileLease.LOCK_FILE));
            Files.deleteIfExists(lease.getDirectory());
        }
        assertThatThrownBy(() -> ProfileLease.acquire(Paths.get("."), ".."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void profile_directory_and_cookie_snapshot_are_private_to_the_owner() throws Exception {
        assumeTrue(Files.getFileAttributeView(root, PosixFileAttributeView.class) != null, "POSIX only");
        ProfileLease lease = ProfileLease.acquire(root, "private");
        try {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(lease.getDirectory())))
                    .isEqualTo("rwx------");
            Path file = lease.getDirectory().resolve("snapshot.tmp");
            Files.write(file, new byte[] {1});
            PrivateFiles.createPrivateFile(file);
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
            assertThat(Files.size(file)).isZero();
        } finally {
            lease.release();
        }
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
