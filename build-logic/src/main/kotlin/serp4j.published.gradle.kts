/*
 * Publizierte Module (ADR-015, E-7): maven-publish, Signatur mit In-Memory-GPG-Schlüssel,
 * POM-Metadaten, Teilnahme an der nmcp-Aggregation des Root-Projekts.
 *
 * Properties (lokal in ~/.gradle/gradle.properties oder als ORG_GRADLE_PROJECT_<name> in CI):
 *   signingInMemoryKey, signingInMemoryKeyPassword, mavenCentralUsername, mavenCentralPassword
 * Fehlt der Schlüssel, wird nicht signiert (publishToMavenLocal funktioniert weiterhin).
 */
plugins {
    `maven-publish`
    signing
    id("com.gradleup.nmcp")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            plugins.withId("java-library") {
                from(components["java"])
            }
            plugins.withId("java-platform") {
                from(components["javaPlatform"])
            }
            pom {
                name.set(provider { project.name })
                description.set(provider { project.description ?: "Serp4J module ${project.name}" })
                url.set("https://github.com/aresstack/Serp4J")
                inceptionYear.set("2026")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("aresstack")
                        name.set("AresStack")
                        url.set("https://github.com/aresstack")
                    }
                }
                scm {
                    url.set("https://github.com/aresstack/Serp4J")
                    connection.set("scm:git:https://github.com/aresstack/Serp4J.git")
                    developerConnection.set("scm:git:ssh://git@github.com/aresstack/Serp4J.git")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/aresstack/Serp4J/issues")
                }
            }
        }
    }
}

// Zugangsdaten für die modulweisen Snapshot-Publikationen (publishAllPublicationsToCentralSnapshots);
// Releases laufen über die Aggregation im Root-Projekt, die dieselben Properties liest.
nmcp {
    centralPortal {
        username.set(providers.gradleProperty("mavenCentralUsername"))
        password.set(providers.gradleProperty("mavenCentralPassword"))
        publishingType.set("USER_MANAGED")
    }
}

val signingKey = providers.gradleProperty("signingInMemoryKey")
val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword")

signing {
    isRequired = signingKey.isPresent
    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.getOrElse(""))
    }
    sign(publishing.publications)
}
