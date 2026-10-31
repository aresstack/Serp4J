plugins {
    id("serp4j.java8")
    id("serp4j.published")
}

description = "Serp4J testkit: mock providers, FakeHttpTransport, fixtures and contract tests (Java 8, test scope)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.testkit")
}

dependencies {
    api(project(":serp4j-core"))
    api(project(":serp4j-http-api"))

    // Contract tests are JUnit 5 classes; consumers bring JUnit Jupiter themselves (implementation-plan.md, Paket E).
    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.jupiter)
}
