plugins {
    id("serp4j.java8")
    id("serp4j.published")
    // Shared base of the browser TCK runs in the driver modules (Playwright runtime + process checks).
    `java-test-fixtures`
}

description = "Serp4J browser adapter over the Playwright Java API (Java 8, without driver; add exactly one of " +
    "serp4j-browser-playwright-driver-node or -driver-graal at runtime)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.browser.playwright")
}

dependencies {
    api(project(":serp4j-browser-api"))
    // The Playwright Java API is Java 8 bytecode; the JS driver is chosen by a driver module (ADR-011).
    implementation(libs.playwright) {
        exclude(group = "com.microsoft.playwright", module = "driver")
        exclude(group = "com.microsoft.playwright", module = "driver-bundle")
    }
    testImplementation(libs.archunit.junit5)

    testFixturesApi(project(":serp4j-browser-testkit"))
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter)
}

// Test fixtures are not published.
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
