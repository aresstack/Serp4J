/*
 * BOM (ADR-015): Constraints auf alle publizierten Module in der gemeinsamen Version.
 * Externe Bibliotheken (Gson, AHC, Playwright) werden bewusst nicht aufgenommen (WP4 §8.3).
 * Die Liste ergibt sich aus den Modulen mit dem Convention-Plugin serp4j.published.
 */
plugins {
    `java-platform`
    id("serp4j.published")
}

description = "Serp4J Bill of Materials"

rootProject.subprojects
    .filter { it != project }
    .forEach { evaluationDependsOn(it.path) }

dependencies {
    constraints {
        rootProject.subprojects
            .filter { it != project && it.plugins.hasPlugin("serp4j.published") }
            .sortedBy { it.name }
            .forEach { api(project(it.path)) }
    }
}
