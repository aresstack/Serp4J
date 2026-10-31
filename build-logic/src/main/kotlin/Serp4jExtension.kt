import org.gradle.api.provider.Property

/**
 * Modul-Metadaten, die ein Modul in seinem build.gradle.kts setzt:
 *
 * ```
 * serp4j {
 *     automaticModuleName.set("com.aresstack.serp4j.core")
 * }
 * ```
 */
abstract class Serp4jExtension {
    /** `Automatic-Module-Name` im Manifest; Konvention: die Paketwurzel des Moduls (architecture.md §2). */
    abstract val automaticModuleName: Property<String>
}
