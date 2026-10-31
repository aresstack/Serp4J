# ADR-013 – Kein `Serializable`, JSON über `serp4j-json`

**Status:** Vorgeschlagen · **Quellen:** WP1 D11, Q5; WP2 §3.2, §11.3; WP4 §4.2

## Kontext
WP1 lehnte `Serializable` ab, WP2 setzte es bei `ProviderId` und `SessionHandle`. Konsumenten wollen Antworten cachen
oder Dialogverläufe speichern.

## Entscheidung
- Kein Domain- oder Port-Typ implementiert `java.io.Serializable`; keine Fremd-Annotationen im Core.
- `serp4j-json` (Gson als `implementation`) liest und schreibt `SearchResponse`, `SearchConversation` und
  `SessionHandle` mit stabilem, versioniertem Schema (`formatVersion`). Unbekannte Felder werden beim Lesen toleriert.
- `SessionHandle` ist ein Wertobjekt mit opakem String-Token (ohne Klartext-Secrets).
- Eine JSON-Bibliothek für das ganze Projekt: Gson.

## Konsequenzen
- Java-Serialisierung (Sicherheitsrisiko, Versionsbrüche) entfällt.
- `serp4j-json` ist nicht im kritischen Pfad (Paket J im Plan).
