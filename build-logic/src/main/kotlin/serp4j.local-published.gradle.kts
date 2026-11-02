/*
 * Nur lokal publizierte Module (ADR-016/ADR-018, E-3/E-4): die Google-Module bleiben in 0.x unpubliziert auf
 * Maven Central, sollen aber von Hosts wie AskAI über publishToMavenLocal eingebunden werden können.
 * Bewusst ohne nmcp und ohne Signatur: diese Module nehmen weder an der Central-Aggregation noch am
 * Snapshot-Upload teil und stehen nicht in der BOM (die BOM listet nur serp4j.published).
 */
plugins {
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set(provider { project.name })
                description.set(provider { project.description ?: "Serp4J module ${project.name}" })
                url.set("https://github.com/aresstack/Serp4J")
            }
        }
    }
}

// Schutz gegen versehentliche Remote-Publikation: nur mavenLocal ist erlaubt.
tasks.withType<PublishToMavenRepository>().configureEach {
    enabled = false
}
