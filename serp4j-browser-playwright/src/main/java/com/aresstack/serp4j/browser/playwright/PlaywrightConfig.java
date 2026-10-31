package com.aresstack.serp4j.browser.playwright;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Immutable configuration snapshot of a {@link PlaywrightBrowserRuntime}. */
final class PlaywrightConfig {

    final PlaywrightBrowserType browserType;
    final String channel;
    final Path executablePath;
    final boolean headless;
    final List<String> launchArgs;
    final String wsEndpoint;
    final int workers;
    final int maxConcurrentSessions;
    final Duration startTimeout;
    final Duration closeTimeout;
    final Map<String, String> driverEnvironment;
    final boolean shutdownHook;
    final ClassLoader classLoader;

    PlaywrightConfig(PlaywrightBrowserType browserType, String channel, Path executablePath, boolean headless,
                     List<String> launchArgs, String wsEndpoint, int workers, int maxConcurrentSessions,
                     Duration startTimeout, Duration closeTimeout, Map<String, String> driverEnvironment,
                     boolean shutdownHook, ClassLoader classLoader) {
        this.browserType = browserType;
        this.channel = channel;
        this.executablePath = executablePath;
        this.headless = headless;
        this.launchArgs = Collections.unmodifiableList(launchArgs);
        this.wsEndpoint = wsEndpoint;
        this.workers = workers;
        this.maxConcurrentSessions = maxConcurrentSessions;
        this.startTimeout = startTimeout;
        this.closeTimeout = closeTimeout;
        this.driverEnvironment = Collections.unmodifiableMap(driverEnvironment);
        this.shutdownHook = shutdownHook;
        this.classLoader = classLoader;
    }

    boolean isRemote() {
        return wsEndpoint != null;
    }

    String backendId() {
        String type = browserType.name().toLowerCase(java.util.Locale.ROOT);
        return isRemote() ? "remote-playwright-" + type : "playwright-" + type;
    }
}
