package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserException;
import com.aresstack.serp4j.browser.BrowserProfileInUseException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Exclusive lease of a persistent profile directory (WP3 §6.3 rule 6): a file lock on {@code .serp4j-profile.lock}
 * inside the directory, held for the lifetime of the session. Works across processes; within one JVM a second
 * lease fails through {@link OverlappingFileLockException}. The operating system drops the lock if the process dies.
 */
final class ProfileLease {

    static final String LOCK_FILE = ".serp4j-profile.lock";

    private final String profileKey;
    private final Path directory;
    private final FileChannel channel;
    private final FileLock lock;

    private ProfileLease(String profileKey, Path directory, FileChannel channel, FileLock lock) {
        this.profileKey = profileKey;
        this.directory = directory;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * @throws BrowserProfileInUseException if another session holds the profile
     */
    static ProfileLease acquire(Path root, String profileKey) {
        Path directory = root.resolve(profileKey).normalize();
        if (!directory.getParent().equals(root.normalize())) {
            throw new IllegalArgumentException("profile key escapes the profile directory: " + profileKey);
        }
        FileChannel channel = null;
        try {
            Files.createDirectories(directory);
            channel = FileChannel.open(directory.resolve(LOCK_FILE), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                lock = null;
            }
            if (lock == null) {
                channel.close();
                throw new BrowserProfileInUseException(profileKey, "browser profile '" + profileKey
                        + "' is used by another session");
            }
            return new ProfileLease(profileKey, directory, channel, lock);
        } catch (IOException e) {
            closeQuietly(channel);
            throw new BrowserException("cannot lease browser profile '" + profileKey + "' in " + directory + ": " + e,
                    e);
        }
    }

    Path getDirectory() {
        return directory;
    }

    String getProfileKey() {
        return profileKey;
    }

    /** Releases the lease. Idempotent. */
    void release() {
        try {
            if (lock.isValid()) {
                lock.release();
            }
        } catch (IOException e) {
            // closing the channel releases the lock as well
        }
        closeQuietly(channel);
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                // nothing left to do
            }
        }
    }
}
