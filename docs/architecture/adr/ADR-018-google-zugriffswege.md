# ADR-018 – Zwei technische Zugriffswege auf Google: HTTP und Browser

**Status:** Angenommen (Angelo, 2026-10-31, Review-Punkt A) · **Quellen:** Serp4J #6 (Quick Web Search), #7 (AI Web
Search), Angelos Review; WP3 §7; WP4 §3.2 (`serp4j-engine-google-http` als spätere Option)

## Kontext
Issue #6 legt fest, zunächst den **direkten HTTP-Aufruf** `GET https://www.google.com/search?q=…` (ohne `udm=50`) zu
untersuchen; der Praxistest vom 2026-10-31 lieferte HTTP 200 mit normalen organischen Treffern, allerdings in einem
Browserkontext. Die ursprüngliche Integration sah nur ein Browser-Modul `serp4j-engine-google` vor und hatte diesen Weg
nicht als eigenen Adapter.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Nur Browser | ignoriert #6; schwergewichtig für die klassische Suche |
| B. Ein Modul mit beiden Wegen | zieht `browser-api` auch für HTTP-Nutzer; Modulgrenze erzwingt die Trennung nicht |
| C. Ein Provider, der intern von HTTP auf Browser umschaltet | stiller Fallback, widerspricht E7 (kein stiller Providerwechsel) |
| D. **Zwei Provider in getrennten Modulen** mit gemeinsamem Parser-Modul | beide bedienen dieselbe Engine/Vertical; der Aufrufer wählt per `SearchPlan` (z. B. `fallback(google-http, google-browser)`); keine versteckte Entscheidung |

## Entscheidung
Option D:
- `serp4j-engine-google-common` (core, jsoup): `GoogleUrlBuilder`, `GoogleObstacleDetector` (Consent, `/sorry/`, Login),
  `GoogleSerpHtmlParser` für die klassische SERP. Jsoup ist Java 8 und abhängigkeitsfrei; das Modul wird nicht
  publiziert, deshalb bleibt die Regel „keine Drittanbieter-Typen in publizierten Signaturen" gewahrt.
- `serp4j-engine-google-http` (core, http-api, common): `GoogleHttpSearchProvider` als `SearchProvider` für
  `google/WEB`. Direkter `GET /search` mit URL-kodierter Query, `hl`, `gl`, `start`, `num`; **nie** `udm=50`.
  Nachgeladene Responses (`/async/folsrch`) nur, wenn durch Fixtures belegt.
- `serp4j-engine-google-browser` (core, browser-api, common): `GoogleBrowserSearchProvider` (`SearchProvider`,
  `google/WEB`) und `GoogleAiModeProvider` (`ConversationalSearchProvider`, `google/AI_MODE`).
- Beide Suchprovider liefern identische `SearchData`-Strukturen; Unterschiede stehen in `SearchMetadata.provider`
  (`google-http` bzw. `google-browser`) und in den Capabilities.
- Der Orchestrator entscheidet nicht über die Implementierung; er führt den vom Aufrufer gewählten Plan aus.
- Die Grenzen aus ADR-016 gelten für beide Wege; keines der drei Module wird in 0.x veröffentlicht (E-3/E-4).

## Offene technische Punkte (Spikes, nicht Angelo)
- **S0** (vor G1): Funktioniert `GET /search` ohne Browserprofil und Cookies reproduzierbar (Consent-Redirect, Blockierung,
  Antwortvarianten)? Ergebnis bestimmt die Capabilities und Warnungen des HTTP-Providers.
- **S3** (vor G2): initialer AI-Mode-Transport (HTML vs. dynamische Responses), `/async/folif` für Folgefragen.

## Konsequenzen
- Das ursprüngliche Paket G wird in G1 (HTTP, nach A, T und Spike S0) und G2 (Browser, nach F, Spikes S1–S4 und G1)
  geteilt; G1 kann früher starten.
- AskAIs „Quick Web Search" nutzt später `google-http` mit `fallback` auf einen anderen Provider nach eigener Wahl; die
  Auswahl bleibt AskAI-Logik.
