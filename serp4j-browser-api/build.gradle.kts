plugins {
    id("serp4j.java8")
    id("serp4j.published")
}

description = "Serp4J browser port: BrowserRuntime, BrowserSession, network observation (Java 8, no dependencies)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.browser")
}

dependencies {
    testImplementation(libs.archunit.junit5)
}
