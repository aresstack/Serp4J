plugins {
    id("serp4j.java8")
    id("serp4j.published")
}

description = "Serp4J HTTP transport port and request authentication strategies (Java 8, no dependencies)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.http")
}
