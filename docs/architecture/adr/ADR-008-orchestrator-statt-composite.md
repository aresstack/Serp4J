# ADR-008 – Orchestrator statt Composite-Provider, SearchOutcome

**Status:** Vorgeschlagen · **Quellen:** WP2 E5, E7, §7.2, §9, F4, F8; WP5 §2.2; WP4 §3.1

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
- `SearchMetadata` der gemergten Antwort trägt nur `contributingProviders`, `complete` und Warnungen; jedes Element
  trägt `List<ResultProvenance>`.
- `UrlDeduplicatingMerger`: Round-Robin nach Originalrang; Duplikate werden zusammengeführt (alle Herkünfte bleiben);
  Features werden nicht dedupliziert; Analysis wird nicht gemergt. `UrlCanonicalizer` erhält die Query und entfernt
  nur bekannte Tracking-Parameter und das Fragment.
- Optionale `SearchAnalyzer` nach dem Merge (`SearchPlan#analyzers`).

## Konsequenzen
- AskAI ruft künftig `SearchUseCase` statt eines Composite-Providers auf.
- Behebt drei Fehler des Bestands (Fehler verschluckt, Herkunft verloren, Query-Dedupe).
