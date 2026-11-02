plugins {
    id("serp4j.java8")
    id("serp4j.local-published")
}

// ADR-016/ADR-018, E-3/E-4: im Repository, @Experimental, in 0.x NICHT publiziert (kein serp4j.published).
description = "Serp4J Google engine common parts: URL builder, obstacle detector and SERP HTML parser (Java 8, jsoup)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.engine.google")
}

dependencies {
    api(project(":serp4j-core"))
    // Nur intern: keine jsoup-Typen in öffentlichen Signaturen.
    implementation(libs.jsoup)
}

dependencies {
    testImplementation(project(":serp4j-testkit"))
}
