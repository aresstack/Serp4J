# ADR-015 – Lockstep-SemVer, BOM, Maven Central

**Status:** Angenommen (Angelo, 2026-10-31) · **Quellen:** WP4 §6, §8, §9

## Entscheidung
- Gradle 8.14.x (läuft auf JDK 8 bis 24, Composite Build mit AskAIs Gradle-7.6.4/JDK-8-CI möglich), Kotlin DSL,
  Convention-Plugins in `build-logic`, Version Catalog. Wechsel auf Gradle 9, wenn AskAI umsteigt.
- `com.aresstack:serp4j-<modul>`, alle Module in **einer** Version (Lockstep), BOM `serp4j-bom`.
- 0.x bis AskAI produktiv auf Serp4J läuft; dann 1.0.0. Ab 1.0 `japicmp`; `@Experimental` ausgenommen.
- Maven Central über Central Publisher Portal mit **`com.gradleup.nmcp`** und dem Aggregations-Plugin
  `com.gradleup.nmcp.aggregation` (0.1.5), wie in `playwright4j` (`PUBLISHING.md`) und `win-directml-java`
  (`build.gradle`) bereits bewährt (E-7, Angelo 2026-10-31). Das Release-Rezept wird aus `playwright4j/PUBLISHING.md`
  übernommen. Signatur über In-Memory-GPG aus GitHub-Secrets.
- Tag `vX.Y.Z` → Build, Tests (JDK 21 + JDK 8), Publish, GitHub Release; Snapshots bei Push auf `main`.
- MIT-Lizenz; Lizenz des Argos-Camoufox-Codes vor Übernahme prüfen.

## Alternativen (abgelehnt)
- Unabhängige Modulversionen: Kombinationsmatrix für Nutzer, kein Mehrwert in 0.x.
- `com.vanniktech.maven.publish` (ursprüngliche Empfehlung): technisch gleichwertig, aber ein zweites Verfahren neben
  dem in den Schwesterprojekten etablierten.
- Gradle 9: verlangt JVM 17 für den Daemon und bricht den Composite Build mit AskAI.

## Konsequenzen
- Nicht publiziert: `build-logic`, `serp4j-examples` und die Google-Module `serp4j-engine-google-common`, `-http`,
  `-browser` (E-3/E-4: in 0.x nicht veröffentlichen, vor 1.0 neu entscheiden).
