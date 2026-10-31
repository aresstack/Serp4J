# ADR-015 – Lockstep-SemVer, BOM, Maven Central

**Status:** Vorgeschlagen · **Quellen:** WP4 §6, §8, §9

## Entscheidung
- Gradle 8.14.x (läuft auf JDK 8 bis 24, Composite Build mit AskAIs Gradle-7.6.4/JDK-8-CI möglich), Kotlin DSL,
  Convention-Plugins in `build-logic`, Version Catalog. Wechsel auf Gradle 9, wenn AskAI umsteigt.
- `com.aresstack:serp4j-<modul>`, alle Module in **einer** Version (Lockstep), BOM `serp4j-bom`.
- 0.x bis AskAI produktiv auf Serp4J läuft; dann 1.0.0. Ab 1.0 `japicmp`; `@Experimental` ausgenommen.
- Maven Central über Central Publisher Portal; Plugin `com.vanniktech.maven.publish`, sofern E-7 kein anderes
  etabliertes Verfahren ergibt. Signatur über In-Memory-GPG aus GitHub-Secrets.
- Tag `vX.Y.Z` → Build, Tests (JDK 21 + JDK 8), Publish, GitHub Release; Snapshots bei Push auf `main`.
- MIT-Lizenz; Lizenz des Argos-Camoufox-Codes vor Übernahme prüfen.

## Alternativen (abgelehnt)
- Unabhängige Modulversionen: Kombinationsmatrix für Nutzer, kein Mehrwert in 0.x.
- Gradle 9: verlangt JVM 17 für den Daemon und bricht den Composite Build mit AskAI.

## Konsequenzen
- Nicht publiziert: `build-logic`, `serp4j-examples`, vorerst `serp4j-engine-google` (E-3/E-4).
