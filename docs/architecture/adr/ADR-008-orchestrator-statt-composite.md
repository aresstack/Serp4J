# ADR-008 – Orchestrator statt Composite-Provider, SearchOutcome

**Status:** Angenommen mit Nachbesserung (Angelo, 2026-10-31, Review-Punkt B) · **Quellen:** WP2 E5, E7, §7.2, §9, F4, F8; WP5 §2.2; WP4 §3.1

## Kontext
AskAIs `CompositeWebSearchProvider` gibt sich als Provider aus (`COMPOSITE`/`MULTI`), verschluckt Fehler
(`exceptionally → null`), verwirft Rohantworten und Herkunft, und die URL-Kanonisierung ignoriert die Query.
WP4 hatte noch einen `CompositeSearchProvider` im Orchestrierungsmodul skizziert.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Composite als `SearchProvider` | bequem, aber eine gemischte Antwort hat mehrere Herkünfte; Fehler einzelner Provider passen nicht in eine `SearchResponse`. |
| B. Application-Service `SearchOrchestrator` mit `SearchPlan` und `SearchOutcome` | Herkunft und Fehler bleiben sichtbar; kein stilles Fallback. |

## Entscheidung
Option B in `serp4j-orchestration`:
- `SearchPlan`: `single`, `fallback(…)`, `fanOut(…)`, `byCapability(target, mode)`; `FailurePolicy` `FAIL_FAST`,
  `TOLERATE_PARTIAL`, `REQUIRE_ALL`; kein Fallback ohne ausdrückliche Anforderung.
- `SearchOutcome`: gemergte Antwort, `List<ProviderAttempt>` (Status, Kategorie, Dauer, Trefferzahl, Skip-Grund),
  `isPartial()`, unveränderte Einzelantworten (inkl. ihrer `RawPayload`s).
- **Metadaten bei mehreren Providern (Review-Punkt B):** `SearchMetadata.getProvider()` ist `Optional<ProviderId>`.
  Antwort eines einzelnen Providers: gesetzt (Pflicht im Provider-Vertrag, Contract-Test). Zusammengeführte Antwort:
  leer, keine Pseudo-ID. `getContributingProviders()` ist nie leer und nennt alle beteiligten Provider (bei
  Einzelantwort genau einen). `getEngine()`/`getVertical()` sind bei Merge nur gesetzt, wenn alle Beteiligten denselben
  Wert haben; `QueryInfo` bleibt immer Pflicht. Antwortet bei `FALLBACK`/`FAN_OUT` nur ein Provider erfolgreich, ist die
  Antwort eine Einzelantwort. Jedes Element trägt `List<ResultProvenance>` (leer bei Einzelantwort).
- `UrlDeduplicatingMerger`: Round-Robin nach Originalrang; Duplikate werden zusammengeführt (alle Herkünfte bleiben);
  Features werden nicht dedupliziert; Analysis wird nicht gemergt. `UrlCanonicalizer` erhält die Query und entfernt
  nur bekannte Tracking-Parameter und das Fragment.
- Optionale `SearchAnalyzer` nach dem Merge (`SearchPlan#analyzers`).

## Konsequenzen
- AskAI ruft künftig `SearchUseCase` statt eines Composite-Providers auf.
- Behebt drei Fehler des Bestands (Fehler verschluckt, Herkunft verloren, Query-Dedupe).
