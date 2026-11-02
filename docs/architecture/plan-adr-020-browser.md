# Umsetzungsplan ADR-020 – Browserübergabe und enginesneutrale Browser-Suche

| Schritt | Inhalt | Modul | Stand |
|---|---|---|---|
| B1 | `BrowserProduct`, `BrowserSpec` (installed/executable/connect), `BrowserInstallation`, `BrowserDiscovery` (Windows/macOS/Linux, ohne Prozessaufrufe), SPI `BrowserRuntimeFactory`, `BrowserRuntimes.create(spec)`, Status `NO_BACKEND` | `serp4j-browser-api` | dieser PR |
| B2 | `PlaywrightBrowserRuntimeFactory`: Chrome/Edge → Kanal `chrome`/`msedge`, Chromium → System-Chromium oder Playwright-Build, Firefox/WebKit nur Playwright-Builds, `connect` über Playwright-Server (`ws://`); Browser-Umgebungsvariablen (`browserEnvironment`) getrennt von der Treiber-Umgebung | `serp4j-browser-playwright` | dieser PR |
| B3 | `CamoufoxBrowserRuntimeFactory`: portable Camoufox per Pfad oder Erkennung, Playwright-Firefox mit `executablePath`, Konfiguration als `CAMOU_CONFIG_1..n`; kein Download | `serp4j-browser-camoufox` (neu, publiziert) | dieser PR |
| B4 | Live-Test auf Angelos Rechner (Pyloros, Temp-Klon): `BrowserDiscovery.system().findAll()`, Edge und Camoufox über `BrowserRuntimes.create`, Browser-TCK mit Camoufox | – | nach PR-Erstellung |
| S1 | `serp4j-browser-search` (neu, publiziert): `BrowserSearchProvider` + SPI `BrowserSearchEngine` (Navigation, Obstacle-Erkennung, Parser), generischer Ablauf aus `GoogleBrowserSearchProvider` herausgelöst | `serp4j-browser-search` | nach Merge #28 |
| S2 | Google als `BrowserSearchEngine`-Adapter; bleibt unpubliziert (ADR-016) | `serp4j-engine-google-browser` | nach S1 |
| S3 | CDP-Verbindung (`connectOverCDP`) für vom Host gestartete Chromium-Browser | `serp4j-browser-playwright` | bei Bedarf AskAI |
| S4 | Gemeinsame Camoufox-Bibliothek (E-5) für Fingerprint-Erzeugung anbinden | `serp4j-browser-camoufox` | wenn Bibliothek existiert |

## Auswirkungen auf AskAI (askai#45)
- Statt `PlaywrightBrowserRuntime.builder().channel("msedge")…` baut AskAI eine `BrowserSpec` aus der Karte
  „Google-Suchbrowser“ (Produkt, optional Pfad, sichtbar/headless) und ruft `BrowserRuntimes.create(spec)`.
  Abhängigkeiten: `serp4j-browser-playwright` + Treiber, optional `serp4j-browser-camoufox`.
- Die Liste der wählbaren Browser kommt aus `BrowserDiscovery.system().findAll()`.
- Der eigene Playwright-Suchprozess kann entfallen, oder AskAI übergibt ihn als `BrowserSpec.connect(product, ws)`.
  Die Runtime gehört AskAI und wird von AskAI geschlossen (ADR-017).
- Nach S1/S2 nimmt AskAI `BrowserSearchProvider` mit Google-Adapter statt `GoogleBrowserSearchProvider`.
