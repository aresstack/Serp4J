# ADR-009 – Dialogsuche als eigener Port

**Status:** Vorgeschlagen · **Quellen:** Angelo Punkt 2; WP1 D10, §4.11; WP2 E10, E11, §11; WP3 B-8, §7.3

## Kontext
Google AI Mode führt einen Dialog mit Folgefragen. Normale Provider dürfen dadurch nicht komplizierter werden.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Session-Parameter im `SearchRequest` | presst Dialog in den zustandslosen Port. Abgelehnt (Angelos Vorgabe). |
| B. Default-Methoden im `SearchProvider` | jeder Provider erbt Dialogbegriffe. Abgelehnt. |
| C. Eigener Port `ConversationalSearchProvider` mit gemeinsamer Basis `ProviderPort` | normale Provider unberührt, Fähigkeit über `SearchCapabilities.conversational` sichtbar. |

## Entscheidung
Option C mit:
- `ConversationalSearchProvider.openSession(SearchSessionOptions, SearchContext)`, optional `resumeSession(SessionHandle, …)`.
- `SearchSession.send(SearchConversationRequest, SearchContext)` → `ConversationTurn` (enthält eine normale `SearchResponse`).
  Höchstens ein laufender Turn (`SESSION_BUSY`), `expectedTurnIndex` für optimistische Kontrolle, kein stilles Neuöffnen.
- Zustände `OPEN, BUSY, ACTIVE, EXPIRED, FAILED, CLOSED`; Browserverlust → `FAILED` + `SESSION_LOST`.
- `ConversationContext` in `SearchMetadata` (SessionId, Turn-Index, Provider-Konversations-ID, Antwort-Element).
- Typnamen: `SearchSessionId` (WP1), `ConversationTurn`/`SearchConversation` (WP2). Kein eigener Final-Typ.
- Kein Composite für Sessions; `SessionPlan` nur `single`/`fallback` beim Öffnen.
- Alles `@Experimental` bis 1.0. Google AI Mode: `resumable = false`, bis Spike S3 anderes belegt.

## Konsequenzen
- Konsumenten, die nur `SearchResponse` verstehen, können jeden Turn lesen.
- Testkit liefert `ScriptedConversationalProvider` und `ConversationalSearchProviderContractTest`.
