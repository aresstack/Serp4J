# ADR-019 – Response-Mapping in zwei Ebenen: deklarative Definitionen plus Custom Mapper

**Status:** Angenommen (Angelo, 2026-10-31, nach Prüfung von Commit e6f7ab7) ·
**Quellen:** Angelos Mapping-Vorschlag (Vorbild WD4J: Gson `@JsonAdapter`, polymorphe Adapter, `GsonMapperFactory`);
WP1 §5 (Elementtypen je Provider), ADR-006 (Extensions, `StructuredValue`, `GenericSerpFeature`), E-1 (echte Fixtures);
Prototyp [`spikes/mapping-prototype`](../../../spikes/mapping-prototype/README.md)

## Kontext
Jeder Provider liefert dieselbe Information in anderer Struktur: Brave verschachtelt nach Ergebnisart (`web.results[]`),
DataForSEO liefert eine flache, polymorphe `items[]`-Liste mit `type`-Diskriminator und `rank_group`/`rank_absolute`,
Bright Data je Engine ein anderes JSON. In AskAI sind die Mapper heute handgeschrieben; drei der dort gefundenen Fehler
(Provider immer als `GOOGLE` etikettiert, Rohantwort kommt nie an, Deduplizierung verwirft die Query) liegen genau in
diesem Code. Serp4J braucht eine Mapping-Infrastruktur, die

- das Routine-Mapping (Feldnamen, Alternativen, Defaults, Typkonvertierung, Pfade) **ohne Java-Code** je Provider
  beschreibt, damit eine Feldumbenennung beim Provider eine Ressourcenänderung und kein Code-Release ist,
- komplexe und polymorphe Strukturen (Knowledge Graph, People also ask, KI-Antworten, Rangsemantik) **in Java** löst,
- unbekannte Daten nie still verliert (ADR-006),
- mit echten Provider-Fixtures testbar ist (E-1),
- Nutzern von Serp4J für eigene Provider zur Verfügung steht,
- den Core frei von Gson und jeder anderen JSON-Bibliothek lässt.

Angelos sieben Prinzipien sind damit verbindlich:

1. Jeder Provider hat seinen eigenen Response-Mapper.
2. Es gibt einen gemeinsamen Mechanismus für deklaratives Feld-Mapping.
3. Komplexe Transformationen laufen über Java-Adapter.
4. Mapping-Konfigurationen sind unabhängig von den Domain-Klassen.
5. Unbekannte Daten gehen nie still verloren.
6. Mapper sind mit echten Provider-JSON-Fixtures testbar.
7. Das Domain-Modell ist unabhängig von Gson und jeder JSON-Bibliothek.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Handgeschriebene Mapper je Provider (heute in AskAI) | volle Freiheit, aber jede Feldumbenennung ist Code; die drei AskAI-Bugs stammen aus diesem Muster; nichts Gemeinsames, das Nutzer für eigene Provider verwenden könnten |
| B. Gson-DTOs mit `@JsonAdapter`/`TypeAdapterFactory` je Provider (WD4J-Muster) | bewährt in WD4J, aber: DTO-Klassen je Provider und Version, Polymorphie bleibt Code, Konfiguration ist an Gson und an Klassen gebunden (verletzt Prinzip 4; in `serp4j-core` undenkbar, Prinzip 7) |
| C. Vollständige JSONPath-/Transformationsbibliothek (Jayway JsonPath, Jolt, JSLT) | mächtig, aber zusätzliche Abhängigkeit mit eigener JSON-Bindung, Java-8-Frage je Bibliothek; Filterausdrücke in Konfiguration sind schwer zu testen und zu debuggen |
| D. Mapping-Engine als interne Komponente jedes Provider-Moduls | keine neue Modulgrenze, aber Dreifachkopie des Mechanismus und für Nutzer unerreichbar (verletzt Prinzip 2) |
| E. Mapping-Engine in `serp4j-core` | ein Modul weniger, aber der Core enthielte Konfigurationslesen und Engine-Logik; der Core bleibt Modell + Ports (ADR-001) |
| F. **Eigenes Modul `serp4j-mapping`** (Java 8, hängt nur an `serp4j-core`), zwei Ebenen: deklarative Definition als Ressource + Custom Mapper in Java, beide über `StructuredValue` | die Modulgrenze erzwingt die Trennung „Mechanismus ↔ Provider-Wissen", Nutzer bekommen den Mechanismus ohne Provider-Code, Gson bleibt in `serp4j-json` |

## Entscheidung
Option F.

### 1. Modul `serp4j-mapping`
- Java 8, `api`-Abhängigkeit nur `serp4j-core`, keine Drittabhängigkeit. Paketwurzel `com.aresstack.serp4j.mapping`.
- Eingabe ist immer ein `StructuredValue` (ADR-006), nie Gson. `serp4j-json` erhält dafür eine öffentliche
  Umwandlung JSON-Text ↔ `StructuredValue` (`JsonTrees`). Damit ist Gson im gesamten Projekt nur noch in
  `serp4j-json` eine Abhängigkeit; Provider-Module nutzen `serp4j-json` und `serp4j-mapping` als `implementation`
  (nie `api`). Regel 3 in architecture.md §3 wird entsprechend gefasst.
- HTML-Quellen (Google-Parser in `engine-google-common`) können denselben Weg gehen, indem der Parser einen
  `StructuredValue`-Baum erzeugt; das ist erlaubt, nicht Pflicht.

### 2. Ebene 1: deklarative `MappingDefinition`
Eine Definition ist eine versionierte JSON-Ressource im Provider-Modul
(`…/provider/<name>/mapping/<name>-<vertical>-v<n>.json`), gelesen über `serp4j-json` in einen `StructuredValue` und
von `MappingDefinitions.from(StructuredValue)` validiert. Sie kennt:

- `metadata`: Feldzuordnungen Wurzel → `SearchMetadata`-Felder und Extensions;
- `sections`: je ein Pfad, der Elemente liefert, mit `kind` = `items` | `features` | `elements` (polymorph),
  `discriminator` + `discriminatorMap` (Diskriminatorwert → `ResultType` bzw. `SerpFeatureType`; in polymorphen
  Sektionen mit Präfix `item:`/`feature:`), `skipWhen`, `position` (`index` | `fields` | `none`), gemeinsame `fields`
  und typspezifische `fieldsByType`, `keepUnmapped`;
- je Feld: Zielname, **Liste alternativer Quellpfade** (erster Treffer gewinnt), `convert`, `default`, `required`;
  Ziele mit Namensraum (`brave:thumbnail`) landen als Extension;
- Pfade in einer **eigenen, bewusst kleinen Sprache**: `$.a.b[0].c[*]`, Namen in `'…'` zitierbar. Keine Filter, keine
  rekursive Suche, keine Ausdrücke. Alles darüber hinaus ist Java (Ebene 2);
- Konverter als benannte, registrierbare Funktionen (`string`, `int`, `long`, `decimal`, `boolean`, `stripHtml`,
  `host`, `isoDate`, …); Provider und Nutzer registrieren eigene.

Die Definition ist unabhängig von den Domain-Klassen (Prinzip 4): Ziele sind symbolische Namen, die die Engine auf
Builder-Methoden abbildet; die Definition importiert keine Klasse.

### 3. Ebene 2: Custom Mapper in Java
- `ItemMapper` je Sektion: läuft **nach** dem deklarativen Teil auf demselben Element und Builder; ergänzt oder
  überschreibt (z. B. `SerpPosition.area` aus `position = right`, Brave `mixed`-Rangsemantik).
- `FeatureMapper` je **(Sektion, Feature-Typ)**: ersetzt den deklarativen Teil für verschachtelte Strukturen
  (Knowledge Graph mit Fakten, PAA mit Fragen und Antwortquellen, KI-Antwort mit `SourceReference`s).
- `MetadataMapper` für Metadaten, die nicht als Feldpfad ausdrückbar sind (z. B. `cost` + `time` → `ProviderUsage`).
- Registrierung in einer `CustomMapperRegistry`, die **zu genau einer Definition** gehört, nie global je Typ-Code und
  nie zwischen Definitionen geteilt. Ein Mapper für DataForSEOs `knowledge_graph` darf nie an Braves `info_box` laufen,
  auch wenn beide auf `knowledge_panel` abbilden (Erkenntnis aus dem Prototyp). **Verbindliche Implementierungsvorgabe
  (Angelo):** Paket MP enthält einen Regressionstest, in dem zwei Definitionen dieselben Sektions-IDs und Feature-Typ-IDs
  verwenden und ihre Custom Mapper sich nachweislich nicht gegenseitig beeinflussen.
- Custom Mapper erhalten einen `MappingContext` (Namensraum des Providers, Konverter, Warnungen, konsumierte Schlüssel).

### 4. Unbekannte Daten (Prinzip 5, ADR-006)
- Jede Sektion merkt sich je Element die konsumierten Schlüssel; der Rest geht als `StructuredValue` nach
  `Extensions.unmapped(<namespace>)` des Elements, Wurzelreste nach `SearchMetadata`.
- Unbekannte Diskriminatorwerte werden `GenericSerpFeature` mit Original-Typcode und Inhalt, plus `SearchWarning`.
- Fehlende Pflichtfelder (`required`, insbesondere die URL eines Items) überspringen das Element mit Warnung, werfen nie.
- Eine Ausnahme wirft die Engine nur für eine **ungültige Definition** (beim Laden, nicht beim Mapping).

### 5. Tests (Prinzip 6, E-1)
- `serp4j-mapping`: Unit-Tests für Pfade, Konverter, Sektionen, Polymorphie, Unmapped-Erhalt.
- `serp4j-testkit` erhält `MappingDefinitionContractTest`: jede Definition lädt; **jeder Quellpfad trifft in mindestens
  einer Fixture** (tote Zuordnungen fallen auf); Golden-Output je Fixture; „unbekannter Typ überlebt".
- Definitionen werden nur gegen echte Fixtures geschrieben. Für Brave und Bright Data entstehen sie erst nach dem
  Recording-Lauf (E-1); erfundene Beispieldaten sind auch hier ausgeschlossen.

### 6. Öffentliche API für Nutzer
`MappingEngine`, `MappingDefinitions`, `CustomMapperRegistry`, `ValueConverters`, `MappingContext` und die
Mapper-Interfaces sind öffentlich und stabil. Ein Nutzer schreibt für einen eigenen Provider eine Definition, bei
Bedarf wenige Java-Mapper, und ruft die Engine mit dem `StructuredValue` seiner Antwort auf. Die Definitionen der
mitgelieferten Provider liegen als Ressourcen vor und sind lesbar, aber nicht Teil der API (Pakete `…internal…`).

## Grenzen der deklarativen Ebene (bewusst)
Deklarativ abgedeckt: flache und verschachtelte Einzelfelder, Listen von Objekten, alternative Feldnamen,
Konvertierungen, Defaults, Pflichtfelder, Polymorphie über Diskriminator, typspezifische Felder, Rang aus Listenindex
oder aus Feldern, Extensions, Unmapped-Erhalt. **Nicht** deklarativ: Listen → typisierte Unterobjekte (Fakten, Fragen),
Ableitungen aus mehreren Feldern, Bedingungen, Aggregationen, Klassifikation nach Suchmodus (AI Overview ≠ AI Mode).
Diese Grenze wird nicht durch eine Ausdruckssprache in der Konfiguration verschoben, sondern bleibt Java.

## Konsequenzen
- Neues Paket **MP** im Implementierungsplan (nach A, parallel zu E); Schritt 4 „Response-Mapper" in B, C, D, D2 nutzt
  MP. `serp4j-json` wird in zwei Schritten gebaut: J1 (Baum ↔ JSON, mit MP) und J2 (Modellformat, später).
- `serp4j-provider-*`: `api` core, http-api; `implementation` mapping, json. Gson erscheint in keinem Provider-Modul
  direkt.
- `Extensions.unmapped` wird größer als bei handgeschriebenen Mappern, weil jedes nicht konsumierte Feld erhalten
  bleibt; `RawPayloadPolicy` bleibt davon unberührt.
- Der Prototyp unter `spikes/mapping-prototype` bleibt Referenz für die Semantik und wird beim Bau von MP gelöscht
  oder als Dokumentationsbeispiel nach `serp4j-examples` übernommen; er ist kein Produktivcode.
- Eigenes Abnahmekriterium in architecture.md §15.
