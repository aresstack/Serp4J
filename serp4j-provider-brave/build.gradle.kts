plugins {
    id("serp4j.provider")
}

description = "Serp4J cloud adapter for the Brave Search API (web search, Java 8)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.provider.brave")
}

dependencies {
    api(project(":serp4j-core"))
    // api: BraveSearchProvider.Builder.transport(HttpTransport) nimmt den Transport des Hosts entgegen (ADR-017).
    api(project(":serp4j-http-api"))
    // Mapping und JSON bleiben Implementierungsdetail (architecture.md §3, Regel 3).
    implementation(project(":serp4j-mapping"))
    implementation(project(":serp4j-json"))

    testImplementation(project(":serp4j-testkit"))
    testImplementation(project(":serp4j-mapping"))
    testImplementation(project(":serp4j-json"))
    "liveTestImplementation"(project(":serp4j-http-ahc"))
}
