package com.aresstack.serp4j.browser.playwright.driver.node;

import com.aresstack.serp4j.browser.playwright.spi.PlaywrightDriverModule;

/**
 * Registers the official Playwright Node driver ({@code com.microsoft.playwright:driver} and {@code :driver-bundle},
 * Node binaries for all platforms). Java 8. Playwright extracts the driver to a temporary directory on first use;
 * browsers are never downloaded by Serp4J.
 */
public final class NodeDriverModule implements PlaywrightDriverModule {

    static final String DRIVER_JAR_CLASS = "com/microsoft/playwright/impl/driver/jar/DriverJar.class";

    /** Service constructor. */
    public NodeDriverModule() {
    }

    @Override
    public String getId() {
        return "node";
    }

    @Override
    public String getDescription() {
        return "com.aresstack:serp4j-browser-playwright-driver-node (Playwright Node driver-bundle)";
    }

    @Override
    public String verify(ClassLoader classLoader) {
        if (classLoader.getResource(DRIVER_JAR_CLASS) == null) {
            return "com.microsoft.playwright:driver-bundle is missing from the class path (was it excluded?)";
        }
        return null;
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
