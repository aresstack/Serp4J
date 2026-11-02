plugins {
    id("serp4j.provider")
}

description = "Serp4J provider for the AskAI/Argos browser web search: talks MCP (streamable HTTP) to the " +
    "browser sidecar's web_search tool (Java 8, no dependency on AskAI)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.provider.argos")
}

dependencies {
    api(project(":serp4j-core"))
    api(project(":serp4j-http-api"))
    // JSON-RPC-Nachrichten des MCP-Protokolls; Gson bleibt hinter serp4j-json verborgen.
    implementation(project(":serp4j-json"))
    testImplementation(project(":serp4j-testkit"))
}
