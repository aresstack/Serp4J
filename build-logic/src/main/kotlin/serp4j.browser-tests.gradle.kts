/*
 * Browser-TCK gegen einen echten Browser (implementation-plan.md Paket F): eigenes Source-Set `browserTest` und
 * Task `browserTest`, der nur mit -Pserp4j.browserTests=true läuft und nie Teil von `check` ist. Mit der Property
 * schlägt ein fehlender Browser fehl, statt übersprungen zu werden (serp4j.browserTests.required).
 * Browserpfad optional über die Umgebungsvariable SERP4J_BROWSER_EXECUTABLE. Serp4J lädt nie Browser herunter.
 */
plugins {
    id("serp4j.java-library")
}

val browserTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[browserTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[browserTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

val browserTestsEnabled = providers.gradleProperty("serp4j.browserTests").map { it.toBoolean() }.getOrElse(false)

tasks.register<Test>("browserTest") {
    description = "Runs the browser TCK against a real browser (only with -Pserp4j.browserTests=true)."
    group = JavaBasePlugin.VERIFICATION_GROUP
    testClassesDirs = browserTest.output.classesDirs
    classpath = browserTest.runtimeClasspath
    onlyIf { browserTestsEnabled }
    systemProperty("serp4j.browserTests.required", browserTestsEnabled)
    // Browser tests start real processes; one JVM at a time keeps the machine predictable.
    maxParallelForks = 1
    outputs.upToDateWhen { false }
}
