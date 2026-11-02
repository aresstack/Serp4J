plugins {
    id("serp4j.java8")
}

// ADR-016/ADR-018, E-3/E-4: @Experimental, in 0.x NICHT publiziert.
description = "Serp4J Google web search over a cookie-free HTTP GET (Quick Web Search, #6; Java 8)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.engine.google.http")
}

dependencies {
    api(project(":serp4j-core"))
    api(project(":serp4j-http-api"))
    implementation(project(":serp4j-engine-google-common"))

    testImplementation(project(":serp4j-testkit"))
}

// Die aufgezeichneten Google-Fixtures liegen einmal in serp4j-engine-google-common.
sourceSets.test {
    resources.srcDir(rootProject.file("serp4j-engine-google-common/src/test/resources"))
}
