package com.aresstack.serp4j.browser.playwright.spi;

/**
 * Marker and self-check of a Playwright driver module, registered with {@link java.util.ServiceLoader} under
 * {@code META-INF/services/com.aresstack.serp4j.browser.playwright.spi.PlaywrightDriverModule}. The runtime requires
 * exactly one registered module (ADR-011).
 *
 * <p>Implementations need a public no-argument constructor and must not start processes.</p>
 */
public interface PlaywrightDriverModule {

    /**
     * @return short id such as {@code node} or {@code graal}
     */
    String getId();

    /**
     * @return human-readable description for readiness messages, for example the artifact
     */
    String getDescription();

    /**
     * Checks that the driver can work in this JVM, without starting it.
     *
     * @param classLoader class loader the Playwright API was loaded with
     * @return {@code null} if the driver looks usable, otherwise a reason without secrets
     */
    String verify(ClassLoader classLoader);
}
