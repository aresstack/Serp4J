/*
 * Provider- und Engine-Adapter (WP4 §6.2): Java 8, publiziert, zusätzliches Source-Set `liveTest`
 * für Tests gegen echte APIs. Live-Tests laufen nur mit -Pserp4j.live=true und nie im Default-Build;
 * -Pserp4j.recordFixtures=true schaltet das Aufzeichnen echter Antworten als Fixtures ein (E-1).
 * Contract-Tests aus serp4j-testkit bindet jedes Modul selbst als testImplementation ein.
 */
plugins {
    id("serp4j.java8")
    id("serp4j.published")
}

val liveTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

configurations[liveTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[liveTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

val liveEnabled = providers.gradleProperty("serp4j.live").map { it.toBoolean() }.getOrElse(false)
val recordFixtures = providers.gradleProperty("serp4j.recordFixtures").map { it.toBoolean() }.getOrElse(false)

tasks.register<Test>("liveTest") {
    description = "Runs tests against real provider APIs (only with -Pserp4j.live=true)."
    group = JavaBasePlugin.VERIFICATION_GROUP
    testClassesDirs = liveTest.output.classesDirs
    classpath = liveTest.runtimeClasspath
    onlyIf { liveEnabled }
    systemProperty("serp4j.live", liveEnabled)
    systemProperty("serp4j.recordFixtures", recordFixtures)
}
