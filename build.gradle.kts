// Root-Projekt: wird nicht publiziert. Sammelt die Publikationen aller Module für das Central Portal
// (com.gradleup.nmcp, ADR-015 / E-7). Modulspezifische Konfiguration liegt in build-logic.
plugins {
    alias(libs.plugins.nmcp.aggregation)
}

nmcpAggregation {
    centralPortal {
        username = providers.gradleProperty("mavenCentralUsername")
        password = providers.gradleProperty("mavenCentralPassword")
        // Freigabe im Portal von Hand; "AUTOMATIC" veröffentlicht nach erfolgreicher Validierung sofort.
        publishingType = "USER_MANAGED"
    }
}

// Gebündelt werden nur Module mit dem Convention-Plugin serp4j.published (direkt oder über serp4j.provider).
// Nicht publishAllProjects…: das würde auch die nur lokal publizierten Google-Module (serp4j.local-published,
// ADR-016) einsammeln.
val centralModules = subprojects.filter { sub ->
    val script = sub.buildFile.takeIf { it.isFile }?.readText() ?: ""
    script.contains("id(\"serp4j.published\")") || script.contains("id(\"serp4j.provider\")")
}
dependencies {
    centralModules.forEach { nmcpAggregation(project(it.path)) }
}
