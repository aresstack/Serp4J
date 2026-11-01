package com.aresstack.serp4j.browser.playwright;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Owner-only permissions for profile directories and the cookie snapshot, which hold session and authentication
 * cookies. POSIX file systems get {@code rwx------}/{@code rw-------}; elsewhere (Windows) read and write access is
 * limited to the owner through {@link File}, on top of the user profile's own ACLs.
 */
final class PrivateFiles {

    private static final Set<PosixFilePermission> DIRECTORY = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE = PosixFilePermissions.fromString("rw-------");

    private PrivateFiles() {
    }

    static void restrictDirectory(Path directory) throws IOException {
        if (isPosix(directory)) {
            Files.setPosixFilePermissions(directory, DIRECTORY);
        } else {
            ownerOnly(directory.toFile(), true);
        }
    }

    /** Creates an empty file readable and writable by the owner only, replacing a leftover from an earlier run. */
    static void createPrivateFile(Path file) throws IOException {
        Files.deleteIfExists(file);
        if (isPosix(file.getParent())) {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(FILE));
        } else {
            Files.createFile(file);
            ownerOnly(file.toFile(), false);
        }
    }

    private static boolean isPosix(Path path) {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class) != null;
    }

    private static void ownerOnly(File file, boolean executable) {
        file.setReadable(false, false);
        file.setReadable(true, true);
        file.setWritable(false, false);
        file.setWritable(true, true);
        if (executable) {
            file.setExecutable(false, false);
            file.setExecutable(true, true);
        }
    }
}
