plugins {
    id("serp4j.java8")
    id("serp4j.published")
}

description = "Serp4J HTTP transport port, request authentication strategies and result URL resolution (Java 8)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.http")
}

dependencies {
    // api: ResultUrlEnricher arbeitet providerneutral auf SearchResponse/SearchContext (nur Domain, keine Provider).
    api(project(":serp4j-core"))
}
