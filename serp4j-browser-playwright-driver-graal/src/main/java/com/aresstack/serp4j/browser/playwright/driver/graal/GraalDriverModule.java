package com.aresstack.serp4j.browser.playwright.driver.graal;

import com.aresstack.serp4j.browser.playwright.spi.PlaywrightDriverModule;

/**
 * Registers {@code com.aresstack:playwright4j}: the Playwright Core driver on GraalJS in a second Java process,
 * without Node. Java 21. Without a JVMCI-enabled JDK GraalJS runs in interpreter mode (slower, warning in the log);
 * fat jars do not work with Truffle (WP3 §1.3).
 */
public final class GraalDriverModule implements PlaywrightDriverModule {

    static final String GRAAL_MAIN_CLASS = "com/aresstack/playwright4j/driver/GraalDriverMain.class";
    static final String CORE_PACKAGE = "driver/package/cli.js";

    /** Service constructor. */
    public GraalDriverModule() {
    }

    @Override
    public String getId() {
        return "graal";
    }

    @Override
    public String getDescription() {
        return "com.aresstack:serp4j-browser-playwright-driver-graal (playwright4j, GraalJS)";
    }

    @Override
    public String verify(ClassLoader classLoader) {
        if (Runtime.version().feature() < 21) {
            return "playwright4j needs Java 21, running on " + Runtime.version();
        }
        if (classLoader.getResource(GRAAL_MAIN_CLASS) == null) {
            return "com.aresstack:playwright4j is missing from the class path";
        }
        if (classLoader.getResource(CORE_PACKAGE) == null) {
            return "the Playwright Core driver package embedded in playwright4j is missing";
        }
        return null;
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
