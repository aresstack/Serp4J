plugins {
    id("serp4j.java8")
    id("serp4j.published")
}

description = "Serp4J JSON integration: JSON text <-> StructuredValue (the only module that depends on Gson)"

serp4j {
    automaticModuleName.set("com.aresstack.serp4j.json")
}

dependencies {
    api(project(":serp4j-core"))
    implementation(libs.gson)
    testImplementation(libs.archunit.junit5)
}
