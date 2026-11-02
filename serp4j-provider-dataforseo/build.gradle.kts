plugins {
    id("serp4j.provider")
}

description = "Serp4J provider adapter for the DataForSEO SERP API v3 (Google/Bing organic, live regular/advanced; Java 8)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.provider.dataforseo")
}

dependencies {
    api(project(":serp4j-core"))
    api(project(":serp4j-http-api"))
    // Regel 3 (architecture.md §3): Mapping und JSON nur als implementation, nie in der öffentlichen API.
    implementation(project(":serp4j-mapping"))
    implementation(project(":serp4j-json"))

    testImplementation(project(":serp4j-testkit"))
}
