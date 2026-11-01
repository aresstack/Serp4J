# Mapping-Definitionen – Format und Semantik (`serp4j-mapping`)

> Referenz zu [ADR-019](../architecture/adr/ADR-019-response-mapping.md) und architecture.md §5.6. Gilt für
> Definitionsformat 1 (Paket MP). Javadoc der Klassen in `com.aresstack.serp4j.mapping` ist die API-Referenz;
> dieses Dokument beschreibt die JSON-Datei.

## Ablauf

```java
// 1. Definition lesen (serp4j-json ist die einzige Stelle mit Gson)
MappingDefinition definition = MappingDefinitions.from(
        JsonTrees.readResource(MyProvider.class, "mapping/my-web-v1.json"));

// 2. Engine binden: Definition + Konverter + Custom Mapper GENAU dieser Definition
MappingEngine engine = MappingEngine.builder(definition)
        .converters(ValueConverters.standard().toBuilder().register("myArea", v -> …).build())
        .customMappers(CustomMapperRegistry.builder(definition)
                .featureMapper("serp", SerpFeatureType.KNOWLEDGE_PANEL, new MyKnowledgeGraphMapper())
                .build())
        .provider(ProviderId.of("my"))
        .build();                                 // prüft Konverternamen und Zugehörigkeit der Registry

// 3. Je Antwort
MappingResult result = engine.map(JsonTrees.read(httpResponse.textBody()));
SearchData data = result.getData();
SearchMetadata metadata = result.applyTo(SearchMetadata.forProvider(id, engine, vertical,
        result.getMetadata().toQueryInfo(request.getQuery()))).build();
```

Engine und Definition sind unveränderlich und threadsicher; eine Engine je Definition, typischerweise einmal pro
Provider-Instanz erzeugt. Eine Registry gehört zu genau einer Definition: `MappingEngine.Builder.build()` lehnt eine
Registry ab, die für eine andere Definition gebaut wurde.

## Wurzel

```json
{
  "id": "brave-web",            // Code [a-z0-9][a-z0-9._-]*
  "version": 1,                  // Version der Definition, >= 1
  "namespace": "brave",          // Namensraum für Extensions und unmapped-Reste
  "description": "…",            // optional
  "metadata": { … },             // Felder → Metadatenziele (Pfade relativ zur Wurzel)
  "sections": [ … ],             // in dieser Reihenfolge abgearbeitet
  "keepUnmappedRoot": true       // Rest der Wurzel in den Metadaten-Extensions erhalten (Default true)
}
```

Unbekannte Schlüssel sind ein Fehler (Tippfehler fallen beim Laden auf). Alle Fehler einer Datei werden gesammelt
und zusammen gemeldet (`InvalidMappingDefinitionException.getProblems()`).

## Sektionen

| Schlüssel | Bedeutung |
|---|---|
| `id` | Code, eindeutig in der Definition; Custom Mapper werden daran registriert |
| `kind` | `items` (Default), `features` oder `elements` (polymorph) |
| `path` | Pfad zu den Elementen, z. B. `$.web.results[*]`; ein Pfad ohne `[*]` liefert ein Element |
| `type` | Typ der Elemente ohne Diskriminator; bei `elements` mit Präfix `item:`/`feature:` |
| `discriminator` | Pfad im Element, dessen Wert den Typ bestimmt (z. B. `type`) |
| `discriminatorMap` | Diskriminatorwert → Typ; bei `elements` Pflichtpräfix `item:organic`, `feature:knowledge_panel` |
| `skipWhen` | Diskriminatorwerte, die bewusst nicht gemappt werden; diese Elemente bleiben im Wurzel-Rest |
| `position` | `fields` (Default), `index` oder `none` |
| `fields` | gemeinsame Felder; gelten für alle Typen der Sektion, die das Ziel unterstützen |
| `fieldsByType` | Felder je Typcode (ohne Präfix); gewinnen bei gleichem Ziel über `fields` |
| `keepUnmapped` | nicht konsumierte Felder je Element erhalten (Default `true`) |

Typbestimmung: ohne Diskriminator gilt `type`. Mit Diskriminator gilt der Eintrag in `discriminatorMap`; fehlt der
Diskriminatorwert im Element, gilt `type`. Ein Wert, der weder in `discriminatorMap` noch in `skipWhen` steht, ist
**unbekannt**: das Element wird `GenericSerpFeature` mit dem Wert als Typcode und vollem Inhalt, dazu eine Warnung
`unknown_element`.

Element-Ids sind `<sektion>#<n>` mit `n` = 1-basierte Stelle in der Quellliste, also auf die Quelle zurückführbar.

Positionen: `index` setzt `rankInGroup` auf die 1-basierte Stelle unter den Elementen **desselben Typs** in der
Sektion (Quellreihenfolge, übersprungene Elemente zählen mit); Positionsfelder ergänzen oder überschreiben das.
`fields` nimmt nur die Felder `rankInGroup`, `rankAbsolute`, `page`, `area`. `none` verbietet Positionsfelder.

## Felder

Drei Schreibweisen:

```json
"title": "name"                                   // ein Quellpfad
"title": ["title", "name"]                         // Alternativen, erster Treffer mit Wert gewinnt
"rankInGroup": { "from": ["rank", "position"], "convert": "int", "default": 1, "required": true }
```

- Quellpfade sind relativ zum Element (bei `metadata` zur Wurzel). JSON-`null` zählt als „kein Wert“.
- `convert`: Name eines Konverters. Ohne Angabe gilt der Standardkonverter des Zieltyps (Text → `string`, Zahl →
  `int`/`long`/`decimal`, Wahrheitswert → `boolean`). Ein Wert, den der Konverter ablehnt, fehlt mit Warnung
  `mapping_incomplete`; das Element wird trotzdem gemappt.
- `default`: beliebiger JSON-Wert, wird wie ein Quellwert konvertiert; nur `default` ohne `from` ergibt eine Konstante.
- `required`: fehlt der Wert, wird das Element mit Warnung übersprungen; das Element steht in den Details der Warnung.
  Bei Items ist `url` immer Pflicht.
- Listenziele (`additionalSnippets`, `images`, `queries`) sammeln alle Treffer eines Pfads mit `[*]`; ein Array als
  Treffer wird aufgefächert.

### Ziele

| Bereich | Ziele |
|---|---|
| Items (`SearchResultItem`) | `url`, `title`, `snippet`, `additionalSnippets`, `displayUrl`, `domain`, `siteName`, `language`, `publishedDate` (Text oder mit `isoDate`/`epoch*` ein Zeitpunkt), `thumbnail` (URL) |
| Position (Items und Features) | `rankInGroup`, `rankAbsolute`, `page`, `area` (`main`, `top`, `sidebar`, `bottom`) |
| `knowledge_panel` | `title` (Pflicht), `subtitle`, `description`, `websiteUrl`, `images` |
| `featured_snippet` | `text`, `sourceUrl` (beide Pflicht), `sourceTitle`, `sourceDomain` |
| `answer_box`, `calculator`, `weather` | `text` (Pflicht), `sourceUrl`, `sourceTitle` |
| `related_searches` | `queries` (Liste, Pflicht) |
| alle anderen Feature-Typen | `title`; das Feature wird `GenericSerpFeature` mit vollem Elementinhalt |
| Metadaten | `originalQuery`, `effectiveQuery`, `totalResultsEstimate`, `hasMore`, `pageNumber`, `providerRequestId`, `durationMillis`, `cost`, `currency`, `credits` (Kontingent-Einheiten, auch gebrochen) |
| Extensions (überall) | `<namespace>:<name>`, Namensraum = `namespace` der Definition |

Fehlen einem deklarativ gebauten Feature seine Pflichtwerte, bleibt es als `GenericSerpFeature` erhalten (Warnung).
Fakten, Fragen mit Antwortquellen, KI-Antworten mit Quellen, Ableitungen aus mehreren Feldern und Bedingungen sind
**nicht** deklarativ (ADR-019, „Grenzen der deklarativen Ebene“): dafür gibt es `FeatureMapper`, `ItemMapper` und
`MetadataMapper`.

Extension-Werte werden unter dem Typ ihres Werts abgelegt: Text als `String`, Zahlen ohne Konverter als
`BigDecimal`, mit `int`/`long` als `Integer`/`Long`, Wahrheitswerte als `Boolean`, Zeitpunkte als `Instant`, Objekte
und Arrays als `StructuredValue`. Das Provider-Modul veröffentlicht passende Schlüssel, z. B.
`ExtensionKey.of("brave", "age", String.class)`.

## Pfadsprache

`$.a.b[0].c[*]`, Namen mit Sonderzeichen in `'…'` (`$.'@type'`), `$` allein ist das Element selbst, ein führendes
`$` ist optional. **Keine** Filter, keine rekursive Suche, keine Ausdrücke (Risiko R15). Ein Schritt, der nicht passt,
liefert keinen Treffer, nie einen Fehler.

## Standardkonverter

`string`, `int`, `long`, `decimal`, `boolean`, `stripHtml`, `host`, `isoDate`, `epochSeconds`, `epochMillis`,
`raw`. Eigene Konverter: `ValueConverters.standard().toBuilder().register(name, converter)`; vorhandene Namen lassen
sich nicht überschreiben.

## Unbekannte Daten (Prinzip 5)

- Jedes Element merkt sich die konsumierten **Blätter** (nicht nur den ersten Schlüssel); der Rest steht als
  `StructuredValue` in `Extensions.getUnmapped(<namespace>)` des Elements. Leere Objekte/Arrays und `null`-Felder
  fallen weg, weil sie keine Information tragen.
- Der Rest der Wurzel (inkl. `skipWhen`-Elementen) steht in `MappedMetadata.getExtensions()`.
- Übersprungene Elemente stehen in den Details ihrer Warnung.
- `keepUnmapped: false` verwirft den Rest einer Sektion bewusst (sichtbar in der Definition, nicht still).

## Custom Mapper (Ebene 2)

| Interface | läuft | Rückgabe |
|---|---|---|
| `ItemMapper` je Sektion | nach den deklarativen Feldern | das Item (meist `item.toBuilder()…build()`), gleiche Id |
| `FeatureMapper` je (Sektion, Typ) | statt der deklarativen Felder | Feature mit `seed.getId()`; `null` = deklarativ weiter |
| `MetadataMapper` | nach den deklarativen Metadaten | schreibt in `MappedMetadata.Builder` |

Mapper lesen über `SourceElement` (`get`, `text`, `select`, `elements`, `consume`), damit Gelesenes als konsumiert
gilt; `elements(path)` liefert verschachtelte Elemente, deren nicht gelesene Felder im Rest bleiben. Bei einem
`FeatureMapper` zählen die gewöhnlichen deklarativen Felder des Features erst als konsumiert, wenn der Mapper `null`
liefert; was er selbst nicht liest, bleibt also im Rest. Wirft ein Mapper,
bleibt das deklarative Ergebnis samt Rest erhalten und es gibt eine Warnung.

## Prüfen gegen Fixtures (Prinzip 6)

`com.aresstack.serp4j.mapping.verify` (ohne JUnit):

- `MappingDefinitionVerifier.coverage(definition, fixtures)` – jeder Sektionspfad, jeder Quellpfad (je Typ) und jeder
  Diskriminatorwert muss in mindestens einer Fixture treffen;
- `MappingDefinitionVerifier.verifyUnknownTypeSurvives(engine, fixture)` – ein eingeschleustes Element unbekannten
  Typs muss als `GenericSerpFeature` mit Warnung überleben;
- `MappingSnapshot.render(result)` – deterministischer Golden-Output.

`MappingDefinitionContractTest` in `serp4j-testkit` baut darauf auf. Definitionen werden nur gegen **echte**,
anonymisierte Fixtures geschrieben (E-1).
