/*
 * Java-8-Baseline (ADR-002): Toolchain JDK 21 kompiliert mit --release 8 (prüft gegen die Java-8-API),
 * zusätzlicher Testlauf `testJava8` auf einem JDK 8, der Teil von `check` ist.
 * Abschaltbar mit -Pserp4j.java8Tests=false (z. B. offline ohne JDK 8).
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
    options.release.set(8)
    // JDK 21 warns that source/target 8 are obsolete; the warning is expected and must not fail -Werror.
    options.compilerArgs.add("-Xlint:-options")
}

val testJava8 by tasks.registering(Test::class) {
    description = "Runs the unit tests on a JDK 8 runtime (ADR-002)."
    group = JavaBasePlugin.VERIFICATION_GROUP
    val testSourceSet = sourceSets.named("test").get()
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    })
    shouldRunAfter(tasks.named("test"))
}

val java8TestsEnabled = providers.gradleProperty("serp4j.java8Tests").map { it.toBoolean() }.getOrElse(true)
if (java8TestsEnabled) {
    tasks.named("check") {
        dependsOn(testJava8)
    }
}
