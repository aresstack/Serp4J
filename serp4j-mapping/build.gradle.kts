plugins {
    id("serp4j.java8")
    id("serp4j.published")
}

description = "Serp4J response mapping: declarative mapping definitions, mapping engine and custom mappers " +
    "(Java 8, depends only on serp4j-core)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.mapping")
}

dependencies {
    api(project(":serp4j-core"))
    // Tests read definitions and responses from JSON resources; the main code never sees Gson.
    testImplementation(project(":serp4j-json"))
    testImplementation(libs.archunit.junit5)
}
