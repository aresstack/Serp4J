# ADR-011 – Playwright-Adapter in Java 8, Treiber als Laufzeitmodule

**Status:** Angenommen (Angelo, 2026-10-31) · **Quellen:** WP3 B-3, Q2; WP4 §5, Frage 9

## Kontext
Playwright Java braucht einen JS-Treiber: offiziell Node (`driver` + `driver-bundle`, Java 8+) oder `playwright4j`
(GraalJS, Java 21). Es darf genau **eine** `com.microsoft.playwright.impl.driver.Driver`-Klasse auf dem Classpath liegen.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Zwei vollständige Adapter | Code-Duplizierung. Abgelehnt. |
| B. Ein Adapter + Exclusion-Rezept in der Doku | Maven-Nutzer müssen Exclusions selbst pflegen. Abgelehnt. |
| C. Ein Adapter ohne Treiber + zwei dünne Treibermodule | gleich einfach in Maven und Gradle, keine Exclusions beim Konsumenten. |

## Entscheidung
Option C: `serp4j-browser-playwright` (Java 8, API ohne Treiber), `serp4j-browser-playwright-driver-node` (Java 8) und
`serp4j-browser-playwright-driver-graal` (Java 21). Der Adapter prüft beim Start über einen `ServiceLoader`-Marker, dass
genau ein Treibermodul vorhanden ist, und bricht sonst mit verständlicher Meldung ab. Die getestete Kombination
Playwright ↔ playwright4j steht im Version Catalog und in der BOM.

## Konsequenzen
- `playwright4j` nur als Thin-Jar/Maven-Auflösung, nicht im Fat-Jar (Truffle-Problem); dokumentiert.
- Spike S1 misst Startzeit, Speicher und Artefaktgröße beider Treiber.
