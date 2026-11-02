/*
 * Java-21-Module (ADR-002, ADR-020): z. B. serp4j-playwright, dessen Browser vom Java-21-Host kommt.
 */
plugins {
    id("serp4j.java-library")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}
