plugins {
    id("serp4j.java8")
    id("serp4j.published")
}

description = "Serp4J browser testkit: FakeBrowserRuntime and the BrowserRuntime contract test (TCK), Java 8"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.browser.testkit")
}

dependencies {
    api(project(":serp4j-browser-api"))
    // The TCK is a JUnit 5 test class; consumers bring their own JUnit (implementation-plan.md, Paket E/F).
    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.jupiter)
}
