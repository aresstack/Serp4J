plugins {
    id("serp4j.java8")
    id("serp4j.published")
}

description = "Serp4J core: provider-independent search domain model and ports (Java 8, no dependencies)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.core")
}

dependencies {
    testImplementation(libs.archunit.junit5)
}
