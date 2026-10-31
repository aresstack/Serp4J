# ADR-007 – Asynchroner SearchProvider-Port, SearchContext, eine Exception mit Kategorie

**Status:** Angenommen (Angelo, 2026-10-31) · **Quellen:** WP2 E2, E6, E9, §5–7; WP5 F8; WP3 §7.3

## Kontext
AskAIs Port ist asynchron (`CompletableFuture`). Das Modul hat eine einzige untypisierte Exception; die AskAI-Runtime eine
Klassenhierarchie. Ausführungsdetails (Deadline, Abbruch, Budget) gehören nicht ins Domain-Modell.

## Entscheidung
- `SearchProvider extends ProviderPort`: `CompletableFuture<SearchResponse> search(SearchRequest, SearchContext)`.
  Synchron werden nur Programmierfehler geworfen (null-Argumente). Threadsicherheit ist Pflicht.
- Kein `supports()`: Die Antwort steckt in `SearchCapabilities.check(request)`. Pflicht-/Wunschmerkmale über
  `RequirementLevel` (REQUIRED nicht erfüllbar → `UNSUPPORTED`; OPTIONAL nicht erfüllbar → ignorieren + Warnung).
- `SearchContext`: Deadline, `CancellationSignal`, Korrelations-ID, optionales `SearchBudget`, Attribute.
- **Eine** `SearchProviderException` mit `SearchErrorCategory` (WP2 §7.1 plus `SESSION_LOST`), `isRetryable()`,
  `isFallbackEligible()` an der Kategorie, `getRetryAfter()`, `getProviderStatusCode()`, `getDetails()`.
  `SearchSessionException` ergänzt `SearchSessionId`.

## Alternativen
| Option | Bewertung |
|---|---|
| Klassenhierarchie (AskAI-Runtime, WP5 F8) | jede neue Fehlerart bricht `catch`-Blöcke; Abbildung auf Fallback-Eignung verstreut. Abgelehnt. |
| Synchroner Port | Fan-out braucht Nebenläufigkeit; Bestand ist async. Abgelehnt. |
| `CompletionStage` | sauberer, aber unpraktisch für Java-8-Aufrufer und Bestand. Abgelehnt. |

## Konsequenzen
- Die AskAI-Bridge bildet Kategorien 1:1 auf ihre Exceptions ab.
- Transportfehler (`HttpTransportException`) verlassen nie den Adapter.
