plugins {
    id("serp4j.java8")
    id("serp4j.published")
}

description = "Serp4J HTTP transport adapter based on async-http-client 2.x (Java 8)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.http.ahc")
}

dependencies {
    api(project(":serp4j-http-api"))
    // api: AhcHttpTransport.wrap(AsyncHttpClient) nimmt einen vom Host erzeugten Client entgegen (ADR-017).
    api(libs.asynchttpclient)
}

// Default-User-Agent "Serp4J/<version>" (HttpTransport-Javadoc, architecture.md §9.1).
val moduleVersion = project.version.toString()
tasks.processResources {
    inputs.property("version", moduleVersion)
    filesMatching("**/serp4j-http-ahc.properties") {
        expand("version" to moduleVersion)
    }
}
