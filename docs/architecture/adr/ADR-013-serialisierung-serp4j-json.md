# ADR-013 – Kein `Serializable`, JSON über `serp4j-json`

**Status:** Angenommen (2026-11-01, Paket J2) · **Quellen:** WP1 D11, Q5; WP2 §3.2, §11.3; WP4 §4.2

## Kontext
WP1 lehnte `Serializable` ab, WP2 setzte es bei `ProviderId` und `SessionHandle`. Konsumenten wollen Antworten cachen
oder Dialogverläufe speichern.

## Entscheidung
- Kein Domain- oder Port-Typ implementiert `java.io.Serializable`; keine Fremd-Annotationen im Core.
- `serp4j-json` (Gson als `implementation`) liest und schreibt `SearchResponse`, `SearchConversation` und
  `SessionHandle` mit stabilem, versioniertem Schema (`formatVersion`). Unbekannte Felder werden beim Lesen toleriert.
- `SessionHandle` ist ein Wertobjekt mit opakem String-Token (ohne Klartext-Secrets).
- Eine JSON-Bibliothek für das ganze Projekt: Gson.

### Ergänzung bei Annahme (Paket J2)
Umgesetzt als `com.aresstack.serp4j.json.Serp4jJson` auf Basis von `JsonTrees` (J1); das Modell wird über
`StructuredValue` geschrieben und gelesen, Gson bleibt in `json.internal`.

1. **`formatVersion`.** Jedes Dokument beginnt mit `formatVersion` (ganze Zahl) und `type`
   (`search_response`, `search_conversation`, `session_handle`). Der Writer schreibt immer die aktuelle Version
   (`CURRENT_FORMAT_VERSION` = 1). Der Reader liest alle Versionen aus `SUPPORTED_FORMAT_VERSIONS` (bekannte ältere
   Versionen werden in das aktuelle Modell überführt). Fehlt die Version, ist sie keine ganze Zahl oder unbekannt –
   auch höher als unterstützt –, wird das Dokument mit `UnsupportedFormatVersionException` abgelehnt. Es wird nie
   geraten. Ein falscher `type` ergibt `Serp4jJsonException`.
2. **`RawPayload`.** Standardmäßig wird `SearchMetadata.getRawPayload()` *nicht* geschrieben: Persistenz ohne
   Rohdaten, der Aufrufer muss `withoutRawPayload()` nicht kennen. Aufnahme nur per Opt-in
   `Serp4jJson.builder().includeRawPayload(true)` (Body Base64, Header ohne sensitive Header, Charset, Status). Ein im
   Dokument vorhandener Raw Payload wird beim Lesen immer übernommen.
3. **Grenzen des Formats.** Provider-spezifische `ProviderRequestOptions` eines Konversationsschritts sind
   Laufzeitkonfiguration und werden nicht geschrieben. Typisierte Extensions werden für die Werttypen `String`,
   `Boolean`, `Integer`, `Long`, `Double`, `BigDecimal` und `StructuredValue` geschrieben; andere Typen lassen das
   Schreiben mit `Serp4jJsonException` scheitern statt still Daten zu verlieren. `Extensions.unmapped` wird immer
   geschrieben. Features tragen einen `kind`-Diskriminator je Feature-Klasse des Core.
4. **Fehler.** Ungültiger JSON-Text ergibt `JsonTreeException`, ein gültiges JSON, das kein gültiges Modelldokument
   ist (Pflichtfeld fehlt, falscher Typ, unbekannter Enum- oder `kind`-Wert, verletzte Modell-Invariante), ergibt
   `Serp4jJsonException` (Untertyp) mit JSON-Pfad, z. B. `$.data.results[0].url`.

## Konsequenzen
- Java-Serialisierung (Sicherheitsrisiko, Versionsbrüche) entfällt.
- `serp4j-json` ist nicht im kritischen Pfad (Paket J im Plan).
- Jede inkompatible Formatänderung erhöht `formatVersion` und bringt eine Lese-Migration der Vorversion mit.
