plugins {
    id("serp4j.java21")
    id("serp4j.published")
    id("serp4j.browser-tests")
}

description = "Serp4J Playwright extension: frame for browser-based search providers that use a Playwright Browser " +
    "owned by the host (ADR-020); Java 21, Playwright Java API without driver"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.playwright")
}

dependencies {
    api(project(":serp4j-core"))
    // The host initialises Playwright and starts the browser; this module only uses the Java API. The driver
    // (com.microsoft.playwright:driver-bundle or com.aresstack:playwright4j) is the host's choice.
    api(libs.playwright) {
        exclude(group = "com.microsoft.playwright", module = "driver")
        exclude(group = "com.microsoft.playwright", module = "driver-bundle")
    }

    testImplementation(project(":serp4j-testkit"))
    testImplementation(libs.archunit.junit5)

    // Real-browser tests launch Playwright's Chromium with the official driver (CI installs it explicitly).
    "browserTestRuntimeOnly"(libs.playwright.driver)
    "browserTestRuntimeOnly"(libs.playwright.driver.bundle)
}
