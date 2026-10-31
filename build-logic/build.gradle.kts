plugins {
    `kotlin-dsl`
}

dependencies {
    // Macht die Plugin-IDs com.gradleup.nmcp / com.gradleup.nmcp.aggregation in Convention-Plugins verfügbar.
    implementation(plugin(libs.plugins.nmcp.module))
}

fun DependencyHandler.plugin(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
