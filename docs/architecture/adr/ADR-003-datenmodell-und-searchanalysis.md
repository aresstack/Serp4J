# ADR-003 – Dreiteiliges Datenmodell, SearchAnalysis-Typen im Core

**Status:** Angenommen (Angelo, 2026-10-31) · **Quellen:** WP1 D1, D8, D10, Q1; WP2 §12; WP4 §3.2; Angelo Regel 4

## Kontext
Vorgabe: `SearchResponse = SearchData + SearchMetadata + SearchAnalysis`, Analysis strikt getrennt von Suchdaten.
Offen war, ob `SearchAnalysis` in den Core oder in ein Erweiterungsmodul gehört.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Typen und Analyzer im Core | Bläht den Core mit Logik auf, ggf. später mit NLP-Abhängigkeiten. Abgelehnt. |
| B. `SearchResponse` ohne Analysis, Wrapper `AnalyzedSearchResponse` in einem Analysis-Modul | Formal am saubersten, bricht aber das vorgegebene dreiteilige Modell und erzeugt zwei Antworttypen. |
| C. Wertetypen von `SearchAnalysis` im Core, Erzeugung nur über den Port `SearchAnalyzer` außerhalb der Provider | Modell bleibt dreiteilig, Core bleibt logik- und abhängigkeitsfrei, Trennung ist auf Port-Ebene erzwungen. |

## Entscheidung
Option C.
- `SearchData` ist Pflicht (darf leer sein), Metadata und Analysis sind optional.
- Provider befüllen nur Data und Metadata. Alles, was eine Quelle liefert (Knowledge Graph, AI Overview, AI-Mode-Antwort,
  PAA, Rechtschreibkorrektur), ist Data bzw. Metadata.
- `SearchAnalysis` trägt immer `AnalysisProvenance` und referenziert Suchdaten per `ElementId`, statt sie zu kopieren.
- Mehrere Analyzer werden per `merge()` kombiniert; Provenance bleibt je Teilergebnis erhalten.
- KI-Antworten: `GeneratedAnswerFeature` mit `SourceReference`-Zitaten (nicht als `SearchResultItem`).
- Analyzer-Implementierungen leben in späteren Modulen `serp4j-analysis-*` oder beim Konsumenten (AskAI).

## Konsequenzen
- Ein Contract-Test prüft, dass kein Provider `SearchAnalysis` setzt.
- Jede Modellerweiterung beantwortet die Prüffrage „geliefert oder berechnet?".
