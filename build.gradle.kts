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
    // Alle Module, die maven-publish anwenden (Convention-Plugin serp4j.published), werden gebündelt.
    publishAllProjectsProbablyBreakingProjectIsolation()
}
