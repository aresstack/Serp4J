# ADR-010 – Browser-Port in eigenem Modul `serp4j-browser-api`

**Status:** Angenommen (Angelo, 2026-10-31) · **Quellen:** Angelo Punkt 3; WP2 E12, §13, F2; WP3 B-1, B-2, Q1; WP4 §3.2

## Kontext
Angelos Skizze zeigt `BrowserSession (Port)` unter `serp4j-core`. WP2, WP3 und WP4 empfehlen unabhängig voneinander ein
eigenes Modul. Angelos eigentliches Ziel ist erfüllt, solange Playwright nur ein Adapter ist und Google nur den Port kennt.

## Alternativen
| Option | Vorteile | Nachteile |
|---|---|---|
| A. Paket `…core.browser` im Core | ein JAR weniger, wörtlich wie die Skizze | Cloud-Nutzer sehen Browserbegriffe; jede Browser-Port-Änderung erzwingt eine Core-Version; der Browser ist kein Port der Use Cases, sondern der Engine-Adapter |
| B. Eigenes Modul `serp4j-browser-api` (0 Deps, kennt Core nicht) | Core bleibt reine Suchdomäne; Port entwickelt sich eigenständig; auch für andere Konsumenten (später AskAI) nutzbar | ein zusätzliches kleines JAR |

## Entscheidung
Option B. Die Abhängigkeitsrichtung aus Angelos Skizze bleibt erhalten:
`engine-google-browser → browser-api ← browser-playwright → (Chromium | Camoufox)`, und `engine-google-browser → core`.

**Angelo hat diese Abweichung im Review vom 2026-10-31 ausdrücklich bestätigt** („Verbesserung gegenüber meiner
ursprünglichen Skizze").
`ProviderEnvironment` im Core kennt den Browser nicht; Browser-Runtimes werden per Builder oder per
`getService(BrowserRuntime.class)` injiziert.

## Konsequenzen
- Bewusste Abweichung von der Skizze, Angelo bestätigt sie mit der Architekturabnahme.
- Browser-Typen haben ein eigenes Wertmodell (`BrowserValue`) statt `StructuredValue`.
