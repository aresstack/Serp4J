# Architecture Decision Records – Serp4J

Format: Kontext · Alternativen · Entscheidung · Konsequenzen. Status `Vorgeschlagen` wird mit Angelos Abnahme der
Architektur zu `Angenommen`. Die Gesamtarchitektur steht in [architecture.md](../architecture.md).

| ADR | Titel | Status |
|---|---|---|
| [001](ADR-001-modulschnitt-und-optionalitaet.md) | Modulschnitt und Optionalität nur über Modulgrenzen | Vorgeschlagen |
| [002](ADR-002-java-baselines.md) | Java 8 als Regel, Java 21 als begründete Ausnahme | Vorgeschlagen |
| [003](ADR-003-datenmodell-und-searchanalysis.md) | Dreiteiliges Datenmodell, SearchAnalysis-Typen im Core | Vorgeschlagen |
| [004](ADR-004-offene-wertetypen.md) | Offene Wertetypen statt Enums, API-Konventionen | Vorgeschlagen |
| [005](ADR-005-ai-mode-als-vertical.md) | Google AI Mode = Engine `google` + Vertical `AI_MODE` | Vorgeschlagen |
| [006](ADR-006-erweiterungsmechanismus.md) | Erweiterungsmechanismus, Rohdaten und Provider-Optionen | Vorgeschlagen |
| [007](ADR-007-provider-port-und-fehlermodell.md) | Asynchroner SearchProvider-Port, SearchContext, eine Exception mit Kategorie | Vorgeschlagen |
| [008](ADR-008-orchestrator-statt-composite.md) | Orchestrator statt Composite-Provider, SearchOutcome | Vorgeschlagen |
| [009](ADR-009-conversational-search-port.md) | Dialogsuche als eigener Port | Vorgeschlagen |
| [010](ADR-010-browser-api-eigenes-modul.md) | Browser-Port in eigenem Modul `serp4j-browser-api` | Vorgeschlagen |
| [011](ADR-011-playwright-treibermodule.md) | Playwright-Adapter in Java 8, Treiber als Laufzeitmodule | Vorgeschlagen |
| [012](ADR-012-transport-und-credentials.md) | HTTP-Transport- und Credential-Port; Konfiguration bleibt beim Host | Vorgeschlagen |
| [013](ADR-013-serialisierung-serp4j-json.md) | Kein `Serializable`, JSON über `serp4j-json` | Vorgeschlagen |
| [014](ADR-014-migration-charakterisieren-vor-verschieben.md) | Migration: charakterisieren, portieren, Parität, umschalten | Vorgeschlagen |
| [015](ADR-015-release-und-publishing.md) | Lockstep-SemVer, BOM, Maven Central | Vorgeschlagen |
| [016](ADR-016-google-browser-adapter-grenzen.md) | Grenzen browserbasierter Google-Adapter | Vorgeschlagen, Teil offen (E-3) |
