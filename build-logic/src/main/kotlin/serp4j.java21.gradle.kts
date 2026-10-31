/*
 * Java-21-Module (ADR-002): nur für reine Laufzeitmodule ohne eigenen Port, z. B.
 * serp4j-browser-playwright-driver-graal.
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
