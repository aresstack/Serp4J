import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.external.javadoc.StandardJavadocDocletOptions

/*
 * Basis aller Java-Module (WP4 §6.2): java-library, UTF-8, -Xlint:all -Werror, Javadoc ohne Fehler,
 * reproduzierbare Archive, Automatic-Module-Name, JUnit Jupiter + AssertJ als Test-Abhängigkeiten.
 */
plugins {
    `java-library`
}

val serp4j = extensions.create<Serp4jExtension>("serp4j")
serp4j.automaticModuleName.convention("com.aresstack." + project.name.replace('-', '.'))

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = true
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        addBooleanOption("Xdoclint:all,-missing", true)
        addBooleanOption("Xwerror", true)
        addBooleanOption("quiet", true)
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Automatic-Module-Name" to serp4j.automaticModuleName,
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "AresStack",
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}
