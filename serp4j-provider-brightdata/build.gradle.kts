plugins {
    id("serp4j.provider")
}

description = "Serp4J cloud adapter for the Bright Data SERP API (synchronous REST request API, Google web search, Java 8)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.provider.brightdata")
}

dependencies {
    api(project(":serp4j-core"))
    api(project(":serp4j-http-api"))
    // Mapping definition (ADR-019) and JSON trees; Gson stays behind serp4j-json.
    implementation(project(":serp4j-mapping"))
    implementation(project(":serp4j-json"))

    testImplementation(project(":serp4j-testkit"))
}
