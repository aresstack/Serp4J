# ADR-001 – Modulschnitt und Optionalität nur über Modulgrenzen

**Status:** Angenommen (Angelo, 2026-10-31) · **Quellen:** WP4 §2–4, WP2 §2.3, WP3 §4

## Kontext
Wer nur Cloud-Provider nutzt, soll weder Playwright noch Camoufox noch Netty erhalten. Gleichzeitig sollen Maven- und
Gradle-Konsumenten dieselbe einfache Erfahrung haben. Clean Architecture verlangt, dass Use Cases nur Ports kennen und
Adapter sich nicht gegenseitig kennen.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Wenige große Module mit Maven-`<optional>` | Maven wertet `optional` nicht transitiv aus; Nutzer müssen Koordinaten selbst kennen. Abgelehnt. |
| B. Gradle Feature Variants | Im POM nur als `optional` sichtbar, gleiche Nachteile für Maven. Abgelehnt. |
| C. Ein Modul je Fähigkeit, alles darin ist Pflicht | Gleich in Maven und Gradle; Abhängigkeitsrichtung vom Build erzwungen. Mehr, aber kleine JARs. |

## Entscheidung
Option C. Modulbaum wie [architecture.md §1](../architecture.md#1-modulstruktur). Insbesondere:
`serp4j-core` (Domain + Ports, 0 Deps), `serp4j-orchestration` (Use Cases, Registry), eigene Port-Module
`serp4j-http-api` und `serp4j-browser-api`, ein Modul je Adapter, zwei Testkits, BOM, kein `serp4j-all`.

## Konsequenzen
- Verstöße gegen die Schichtung sind Compile-Fehler, nicht nur Review-Befunde.
- Konsumenten wählen Module über die BOM ohne Versionsangaben.
- Mehr Module bedeuten mehr Build-Konfiguration; Convention-Plugins halten jedes Modul-Skript auf wenige Zeilen.
- Neue Fähigkeiten (z. B. `serp4j-http-jdk`) kommen als neues Modul, nie als optionale Abhängigkeit.
