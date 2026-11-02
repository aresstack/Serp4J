plugins {
    id("serp4j.java8")
    id("serp4j.published")
}

description = "Serp4J browser backend for a portable Camoufox (Firefox build) over the Playwright adapter (Java 8, ADR-020)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.browser.camoufox")
}

dependencies {
    api(project(":serp4j-browser-api"))
    implementation(project(":serp4j-browser-playwright"))
}
