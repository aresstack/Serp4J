// Serp4J – Gradle Multi-Project (ADR-001, ADR-015, architecture.md §1).
//
// Alle geplanten Module sind hier aufgeführt. Ein Modul wird nur in den Build aufgenommen, wenn sein
// Verzeichnis ein build.gradle.kts enthält. Spätere Pakete (B–H, MP, T, F, …) legen deshalb nur ihr
// eigenes Verzeichnis an und müssen diese Datei nicht anfassen.

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Beschafft fehlende JDKs (8 für den Java-8-Testlauf, 21 als Compiler-Toolchain) automatisch.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "serp4j"

val plannedModules = listOf(
    // Basis
    "serp4j-bom",
    "serp4j-core",
    "serp4j-orchestration",
    "serp4j-json",
    "serp4j-mapping",
    // HTTP
    "serp4j-http-api",
    "serp4j-http-ahc",
    "serp4j-http-jdk",
    // Cloud-Adapter
    "serp4j-provider-brave",
    "serp4j-provider-brightdata",
    "serp4j-provider-dataforseo",
    "serp4j-provider-serpapi",
    "serp4j-provider-argos",
    // Browser (ADR-020): Playwright-Integration für lokale Provider; der Host übergibt den Browser
    "serp4j-playwright",
    // Tests und Beispiele
    "serp4j-testkit",
    "serp4j-examples",
)

plannedModules
    .filter { file("$it/build.gradle.kts").isFile }
    .forEach { include(it) }
