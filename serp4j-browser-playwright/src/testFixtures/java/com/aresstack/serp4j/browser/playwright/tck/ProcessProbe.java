package com.aresstack.serp4j.browser.playwright.tck;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Process inspection for the browser TCK through {@code java.lang.ProcessHandle} (Java 9+), called reflectively so
 * that the fixtures stay Java 8 bytecode. On Java 8 {@link #isSupported()} is {@code false}.
 */
final class ProcessProbe {

    private static final Class<?> HANDLE = load();

    private ProcessProbe() {
    }

    private static Class<?> load() {
        try {
            return Class.forName("java.lang.ProcessHandle");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    static boolean isSupported() {
        return HANDLE != null;
    }

    /** Pid and command line of every live descendant of this JVM. */
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
                result.put(pid(handle), command(handle));
            }
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean isAlive(long pid) {
        Object handle = handle(pid);
        if (handle == null) {
            return false;
        }
        try {
            return (Boolean) HANDLE.getMethod("isAlive").invoke(handle);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static void kill(long pid) {
        Object handle = handle(pid);
        if (handle == null) {
            return;
        }
        try {
            HANDLE.getMethod("destroyForcibly").invoke(handle);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object handle(long pid) {
        try {
            Optional<?> handle = (Optional<?>) HANDLE.getMethod("of", long.class).invoke(null, pid);
            return handle.isPresent() ? handle.get() : null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long pid(Object handle) throws ReflectiveOperationException {
        return (Long) HANDLE.getMethod("pid").invoke(handle);
    }

    private static String command(Object handle) throws ReflectiveOperationException {
        Object info = HANDLE.getMethod("info").invoke(handle);
        Class<?> infoType = Class.forName("java.lang.ProcessHandle$Info");
        Method commandLine = infoType.getMethod("commandLine");
        Optional<?> line = (Optional<?>) commandLine.invoke(info);
        if (line.isPresent()) {
            return line.get().toString();
        }
        Method command = infoType.getMethod("command");
        Optional<?> executable = (Optional<?>) command.invoke(info);
        return executable.isPresent() ? executable.get().toString() : "";
    }
}
