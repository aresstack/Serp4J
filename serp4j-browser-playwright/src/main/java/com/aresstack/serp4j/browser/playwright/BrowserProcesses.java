package com.aresstack.serp4j.browser.playwright;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Browser processes a worker launched, through {@code java.lang.ProcessHandle} (Java 9+), called reflectively so the
 * adapter stays Java 8 bytecode.
 *
 * <p><b>Ownership.</b> A process counts as the worker's only if it is provably its own: every Chromium-family browser
 * is launched with a unique {@link #newMarker() marker switch}, and the worker owns the descendants of this JVM whose
 * command line carries that marker, plus their ancestors up to this JVM (the Node driver process; with the GraalJS
 * driver the browser is a direct child of the JVM). Processes of other components, even other Playwright clients
 * launched at the same moment, never carry the marker. Descendants of an owned process are its own children and are
 * resolved only at kill time.
 *
 * <p><b>Identity.</b> Owned processes are kept as {@code ProcessHandle} objects, which carry the process start time:
 * {@code isAlive} and {@code destroyForcibly} of a handle never act on a later process that reused the pid.
 *
 * <p>Where command lines are not visible (Java 8, Windows, other browsers than Chromium) nothing is owned: hard kill
 * then only interrupts the owner thread, and crash detection relies on the driver's disconnect event.
 */
final class BrowserProcesses {

    private static final String MARKER_PREFIX = "--serp4j-owner=";
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

    /** A fresh marker switch for one browser launch; Chromium ignores unknown switches. */
    static String newMarker() {
        return MARKER_PREFIX + UUID.randomUUID();
    }

    /**
     * Live descendants of this JVM whose command line contains the marker: the browser processes of one launch.
     *
     * @return process handles, empty when not supported
     */
    static List<Object> marked(String marker) {
        if (HANDLE == null || marker == null) {
            return Collections.emptyList();
        }
        try {
            Object current = HANDLE.getMethod("current").invoke(null);
            Iterator<?> iterator = ((Stream<?>) HANDLE.getMethod("descendants").invoke(current)).iterator();
            List<Object> result = new ArrayList<Object>();
            while (iterator.hasNext()) {
                Object handle = iterator.next();
                if (commandLine(handle).contains(marker)) {
                    result.add(handle);
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Collections.emptyList();
        }
    }

    /** The processes between this JVM and the given ones (exclusive): the driver process of the Node driver. */
    static List<Object> ancestorsBelowJvm(Collection<Object> handles) {
        if (HANDLE == null || handles.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Object current = HANDLE.getMethod("current").invoke(null);
            Method parent = HANDLE.getMethod("parent");
            List<Object> result = new ArrayList<Object>();
            for (Object handle : handles) {
                Optional<?> next = (Optional<?>) parent.invoke(handle);
                while (next.isPresent() && !next.get().equals(current) && !handles.contains(next.get())) {
                    if (!result.contains(next.get())) {
                        result.add(next.get());
                    }
                    next = (Optional<?>) parent.invoke(next.get());
                }
                if (!next.isPresent()) {
                    // not below this JVM after all; claim nothing on this path
                    return Collections.emptyList();
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Forcibly ends the processes and their current descendants (hard kill after a close timeout, WP3 §6.3 rule 2).
     * A handle whose process has ended is skipped, also when its pid has been reused since.
     *
     * @return number of processes that were still alive and got killed
     */
    static int killTrees(Collection<Object> handles) {
        if (HANDLE == null || handles.isEmpty()) {
            return 0;
        }
        int killed = 0;
        try {
            Method descendants = HANDLE.getMethod("descendants");
            Method destroy = HANDLE.getMethod("destroyForcibly");
            Method isAlive = HANDLE.getMethod("isAlive");
            for (Object handle : new ArrayList<Object>(handles)) {
                if (!(Boolean) isAlive.invoke(handle)) {
                    continue;
                }
                Iterator<?> children = ((Stream<?>) descendants.invoke(handle)).iterator();
                while (children.hasNext()) {
                    Object child = children.next();
                    if ((Boolean) isAlive.invoke(child)) {
                        destroy.invoke(child);
                        killed++;
                    }
                }
                destroy.invoke(handle);
                killed++;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // best effort; the caller logs that the hard kill was attempted
        }
        return killed;
    }

    /**
     * Waits until none of the processes is alive any more, at most {@code timeoutMillis}.
     *
     * @return whether they all ended (also {@code true} when nothing is known)
     */
    static boolean awaitExit(Collection<Object> handles, long timeoutMillis) {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (!handles.isEmpty() && HANDLE != null && anyAlive(handles)) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /** Whether any of the processes is alive; {@code true} for an empty set (nothing known, nothing concluded). */
    static boolean anyAlive(Collection<Object> handles) {
        if (handles.isEmpty() || HANDLE == null) {
            return true;
        }
        try {
            Method isAlive = HANDLE.getMethod("isAlive");
            for (Object handle : handles) {
                if ((Boolean) isAlive.invoke(handle)) {
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    private static String commandLine(Object handle) throws ReflectiveOperationException {
        Object info = HANDLE.getMethod("info").invoke(handle);
        Class<?> infoType = Class.forName("java.lang.ProcessHandle$Info");
        Optional<?> line = (Optional<?>) infoType.getMethod("commandLine").invoke(info);
        return line.isPresent() ? line.get().toString() : "";
    }
}
