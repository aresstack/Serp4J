package com.aresstack.serp4j.browser.playwright;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Liveness of locally launched browser processes through {@code java.lang.ProcessHandle} (Java 9+), called
 * reflectively so the adapter stays Java 8 bytecode. Not every driver notices promptly that its browser died (the
 * GraalJS driver may wait for a reply on a dead pipe until the call times out); the process state is the reliable
 * signal. On Java 8 nothing is tracked and detection relies on the driver's disconnect event alone.
 */
final class BrowserProcesses {

    private static final Class<?> HANDLE = load();

    private BrowserProcesses() {
    }

    private static Class<?> load() {
        try {
            return Class.forName("java.lang.ProcessHandle");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** Pids and command lines of all live descendants of this JVM; empty when not supported. */
    static Map<Long, String> descendants() {
        if (HANDLE == null) {
            return Collections.emptyMap();
        }
        try {
            Object current = HANDLE.getMethod("current").invoke(null);
            Stream<?> stream = (Stream<?>) HANDLE.getMethod("descendants").invoke(current);
            Map<Long, String> result = new LinkedHashMap<Long, String>();
            Iterator<?> iterator = stream.iterator();
            while (iterator.hasNext()) {
                Object handle = iterator.next();
                result.put((Long) HANDLE.getMethod("pid").invoke(handle), command(handle));
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Collections.emptyMap();
        }
    }

    /**
     * Browser processes that appeared since {@code before}. Concurrent launches in the same JVM may add processes of
     * other browsers; that only makes {@link #anyAlive} more conservative, never wrong about a live browser.
     */
    static Set<Long> startedSince(Map<Long, String> before) {
        Set<Long> result = new LinkedHashSet<Long>();
        for (Map.Entry<Long, String> process : descendants().entrySet()) {
            String command = process.getValue().toLowerCase(Locale.ROOT);
            boolean browser = command.contains("chrom") || command.contains("headless_shell")
                    || command.contains("msedge") || command.contains("firefox") || command.contains("webkit");
            if (browser && !before.containsKey(process.getKey())) {
                result.add(process.getKey());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** Whether any of the processes is alive; {@code true} for an empty set (nothing known, nothing concluded). */
    static boolean anyAlive(Set<Long> pids) {
        if (pids.isEmpty() || HANDLE == null) {
            return true;
        }
        try {
            Method of = HANDLE.getMethod("of", long.class);
            Method isAlive = HANDLE.getMethod("isAlive");
            for (Long pid : pids) {
                Optional<?> handle = (Optional<?>) of.invoke(null, pid);
                if (handle.isPresent() && (Boolean) isAlive.invoke(handle.get())) {
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    private static String command(Object handle) throws ReflectiveOperationException {
        Object info = HANDLE.getMethod("info").invoke(handle);
        Class<?> infoType = Class.forName("java.lang.ProcessHandle$Info");
        Optional<?> line = (Optional<?>) infoType.getMethod("commandLine").invoke(info);
        if (line.isPresent()) {
            return line.get().toString();
        }
        Optional<?> executable = (Optional<?>) infoType.getMethod("command").invoke(info);
        return executable.isPresent() ? executable.get().toString() : "";
    }
}
