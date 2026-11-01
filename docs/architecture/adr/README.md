# Architecture Decision Records – Serp4J

Format: Kontext · Alternativen · Entscheidung · Konsequenzen. Angelo hat das Architekturkonzept am 2026-10-31
grundsätzlich angenommen und die drei Review-Punkte A–C sowie die Entscheidungen E-1 bis E-8 vorgegeben; sie sind in
den ADRs eingearbeitet. `Vorgeschlagen` steht nur noch bei Entscheidungen, die Angelo nicht ausdrücklich genannt oder noch nicht abgenommen hat. Die Gesamtarchitektur steht in [architecture.md](../architecture.md).

| ADR | Titel | Status |
|---|---|---|
| [001](ADR-001-modulschnitt-und-optionalitaet.md) | Modulschnitt und Optionalität nur über Modulgrenzen | Angenommen |
| [002](ADR-002-java-baselines.md) | Java 8 als Regel, Java 21 als begründete Ausnahme | Angenommen |
| [003](ADR-003-datenmodell-und-searchanalysis.md) | Dreiteiliges Datenmodell, SearchAnalysis-Typen im Core | Angenommen |
| [004](ADR-004-offene-wertetypen.md) | Offene Wertetypen statt Enums, API-Konventionen | Angenommen |
| [005](ADR-005-ai-mode-als-vertical.md) | Google AI Mode = Engine `google` + Vertical `AI_MODE` | Angenommen |
| [006](ADR-006-erweiterungsmechanismus.md) | Erweiterungsmechanismus, Rohdaten und Provider-Optionen | Vorgeschlagen |
| [007](ADR-007-provider-port-und-fehlermodell.md) | Asynchroner SearchProvider-Port, SearchContext, eine Exception mit Kategorie | Angenommen |
| [008](ADR-008-orchestrator-statt-composite.md) | Orchestrator statt Composite-Provider, SearchOutcome | Angenommen |
| [009](ADR-009-conversational-search-port.md) | Dialogsuche als eigener Port | Angenommen |
| [010](ADR-010-browser-api-eigenes-modul.md) | Browser-Port in eigenem Modul `serp4j-browser-api` | Angenommen |
| [011](ADR-011-playwright-treibermodule.md) | Playwright-Adapter in Java 8, Treiber als Laufzeitmodule | Angenommen |
| [012](ADR-012-transport-und-credentials.md) | HTTP-Transport- und Credential-Port; Konfiguration bleibt beim Host | Angenommen |
| [013](ADR-013-serialisierung-serp4j-json.md) | Kein `Serializable`, JSON über `serp4j-json` | Angenommen |
| [014](ADR-014-migration-charakterisieren-vor-verschieben.md) | Migration: charakterisieren, portieren, Parität, umschalten | Angenommen |
| [015](ADR-015-release-und-publishing.md) | Lockstep-SemVer, BOM, Maven Central | Angenommen |
| [016](ADR-016-google-browser-adapter-grenzen.md) | Grenzen direkter Google-Zugriffe (Browser und HTTP) | Angenommen |
| [017](ADR-017-lifecycle-und-ownership.md) | Lifecycle und Ownership gemeinsam genutzter Infrastruktur | Angenommen |
| [018](ADR-018-google-zugriffswege.md) | Zwei Zugriffswege auf Google: HTTP und Browser | Angenommen |
| [019](ADR-019-response-mapping.md) | Response-Mapping in zwei Ebenen: deklarative Definitionen plus Custom Mapper, Modul `serp4j-mapping` | Angenommen |
