# Mapping-Prototyp (Spike)

**Prototyp, kein Produktivcode.** Dieses Verzeichnis ist nicht Teil des Gradle-Builds und wird nicht veröffentlicht.
Es prüft die Frage aus Angelos Mapping-Vorschlag: *Ist der deklarative Teil flexibel genug, um unterschiedlich
strukturierte Provider-Antworten ohne Code zu erfassen, und wo genau beginnt die Java-Ebene?* Ergebnis und
Entscheidung stehen in [ADR-019](../../docs/architecture/adr/ADR-019-response-mapping.md).

## Ausführen

```bash
./run.sh        # javac --release 8, dann java Demo
```

Erwartete Ausgabe: [`expected-output.txt`](expected-output.txt). Keine Abhängigkeiten, kein Gradle.

## Was drin ist

| Datei | Rolle im Zielsystem |
|---|---|
| `src/Json.java` | Stellvertreter für `serp4j-json` (`JsonTrees`): JSON-Text → neutraler Baum (Map/List/String/Long/BigDecimal/Boolean/null). Im Zielsystem ist der Baum `StructuredValue` aus `serp4j-core`. |
| `src/TreePaths.java` | Die eigene, minimale Pfadsprache: `$.a.b[0].c[*]`, Namen in `'…'` zitierbar. Keine Filter, keine Ausdrücke. |
| `src/Mapping.java` | Stellvertreter für `serp4j-mapping`: `Definition`/`Section`/`FieldMapping` (aus JSON gelesen), `Converters`, `CustomMappers` (`ItemMapper` je Sektion, `FeatureMapper` je Sektion und Typ), `Context`, `Engine`. |
| `src/Model.java` | Stark vereinfachte Stellvertreter für `SearchResultItem`, `SerpFeature`/`GenericSerpFeature`, `SearchMetadata`. |
| `src/Demo.java` | Registriert einen Konverter und drei Custom Mapper, wendet drei Definitionen auf drei Antworten an. |
| `inputs/provider-{a,b,c}.json` | Drei Antwortstrukturen (siehe unten). **Handgeschriebene Beispiele**, nur zur Strukturprüfung; echte Fixtures (E-1) kommen erst in den Paketen B–D. |
| `definitions/provider-{a,b,c}.json` | Die deklarativen Definitionen dazu. |

## Die drei Strukturen

- **A** – Angelos Beispiel 1: `organic_results[]` mit `title`/`link`/`snippet`/`position`, dazu `related_questions[]`
  und `pagination`. Rein deklarativ.
- **B** – Angelos Beispiel 2: dieselbe Information als `results[]` mit `name`/`url`/`description`/`rank` (Rang als
  String, ein Element ohne URL, Zusatztexte `extra[]`, Thumbnail), `info_box`, ein unbekannter Block, Metadaten als
  String. Rein deklarativ.
- **C** – DataForSEO-artig: `tasks[0].result[0].items[]` als flache, polymorphe Liste mit `type`-Diskriminator
  (`organic`, `paid`, `featured_snippet`, `knowledge_graph`, `people_also_ask`, ein unbekannter Typ),
  `rank_group`/`rank_absolute`, `position: left|right`, `cost`. Deklarativ **plus** drei Custom Mapper.

## Befunde

**Deklarativ reicht für:** alternative Feldnamen (`["link","url"]`), Konverter (`int` aus String, `stripHtml`,
`host`), Defaults und Pflichtfelder (Element ohne URL wird mit Warnung übersprungen), Rang aus Listenindex (B) oder aus
Feldern (C), Polymorphie über Diskriminator mit `discriminatorMap`, typspezifische Felder (`fieldsByType`),
Extensions mit Namensraum (`b:thumbnail`), Unmapped-Erhalt je Element und für die Wurzel, unbekannte Typen als
generisches Feature mit Warnung. A und B kommen ohne eine Zeile Java aus.

**Java (Custom Mapper) nötig für:** Listen → typisierte Unterobjekte (Knowledge-Graph-Fakten, PAA-Fragen mit
Antwortquelle), Ableitungen (`area` aus `position`), Flags → Extensions mit Bedeutung (`is_featured_snippet`).

**Drei Korrekturen aus dem ersten Lauf, die in ADR-019 eingeflossen sind:**

1. Custom Feature Mapper **je Sektion und Typ** registrieren, nie global je Typ-Code. Global registriert lief der
   DataForSEO-Knowledge-Graph-Mapper auch auf Braves `info_box` und lieferte leere Felder.
2. Eine polymorphe Liste braucht **eine** Sektion (`kind: elements`, Diskriminator entscheidet `item:`/`feature:`).
   Zwei Sektionen auf demselben Pfad erzeugten Dubletten als generische Features.
3. Gemeinsame `fields` plus `fieldsByType`, sonst landen Item-Felder in Features und umgekehrt in `unmapped`.
4. (Angelos Review) Die Demo nutzte zunächst **eine** `CustomMappers`-Instanz für alle drei Definitionen. ADR-019
   verlangt eine Registry je Definition; die Demo hält deshalb jetzt je Definition eine eigene Instanz, und Paket MP
   bekommt einen Regressionstest für zwei Definitionen mit gleichen Sektions- und Typ-IDs.

**Bewusst nicht gelöst:** Bedingungen oder Ausdrücke in der Definition. Die Grenze bleibt: Was ein Pfad plus Konverter
nicht ausdrückt, ist Java.
