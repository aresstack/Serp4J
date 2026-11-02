plugins {
    id("serp4j.java8")
    id("serp4j.local-published")
}

// ADR-016/ADR-018, E-3/E-4: @Experimental, in 0.x NICHT publiziert.
description = "Serp4J Google web search through the optional browser port (Quick Web Search, #6; Java 8)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.engine.google.browser")
}

dependencies {
    api(project(":serp4j-core"))
    api(project(":serp4j-browser-api"))
    // Optional: HttpTransport für die /goto-Auflösung der Treffer-URLs.
    api(project(":serp4j-http-api"))
    implementation(project(":serp4j-engine-google-common"))

    testImplementation(project(":serp4j-testkit"))
    testImplementation(project(":serp4j-browser-testkit"))
}

sourceSets.test {
    resources.srcDir(rootProject.file("serp4j-engine-google-common/src/test/resources"))
}
