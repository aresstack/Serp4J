plugins {
    id("serp4j.java21")
    id("serp4j.published")
    id("serp4j.browser-tests")
}

description = "Serp4J Playwright driver module: com.aresstack:playwright4j (GraalJS, no Node), Java 21. " +
    "Use as thin jar or through normal Maven resolution, not in a fat jar (Truffle)."

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.browser.playwright.driver.graal")
}

dependencies {
    implementation(project(":serp4j-browser-playwright"))
    // playwright4j replaces com.microsoft.playwright.impl.driver.Driver and embeds the Playwright Core driver;
    // serp4j-browser-playwright already excludes com.microsoft.playwright:driver and :driver-bundle (ADR-011).
    runtimeOnly(libs.playwright4j)

    "browserTestImplementation"(testFixtures(project(":serp4j-browser-playwright")))
}
