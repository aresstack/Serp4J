# ADR-016 – Grenzen direkter Google-Zugriffe (Browser und HTTP)

**Status:** Angenommen (Angelo, 2026-10-31; E-3 = Option B, E-4 = nicht veröffentlichen) · **Quellen:** WP3 B-9, R1, Q7, §9, §13; Angelos Review

## Kontext
Automatisierte Abfragen der Google-Oberfläche widersprechen Googles Nutzungsbedingungen. Das gilt **nicht nur für
Browserautomation**: Google beschreibt maschinell erzeugte Suchanfragen ohne ausdrückliche Erlaubnis als unzulässigen
automatisierten Zugriff, also auch einen direkten `GET /search?q=…` über HTTP (Angelos Klarstellung im Review).
Anti-Fingerprinting (Camoufox) verschärft die Frage. Das ist eine Produkt- und Haftungsentscheidung, keine technische.

## Entscheidung (technisch verbindlich, für `-http` und `-browser` gleichermaßen)
- Keine automatische CAPTCHA-Lösung, keine Umgehung von Schutzmaßnahmen, keine Google-Anmeldung.
- Hindernisse werden typisiert gemeldet (`BLOCKED`, `UNSUPPORTED`); `WAIT_FOR_HUMAN` nur im sichtbaren Modus.
- URL-Allowlist je Sitzung, geprüft vor und nach Redirects; keine Downloads, Popups werden geschlossen.
- Konservative Parallelität und Backoff je Engine im Adapter.
- Serp4J lädt nie implizit Browser herunter (`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`).

## Entscheidung zur Veröffentlichung (Angelo, E-3/E-4)
Alle direkten Google-Zugriffe (`serp4j-engine-google-common`, `-http`, `-browser`) bleiben im Repository, sind
`@Experimental` und werden in 0.x **nicht** als Maven-Artefakt veröffentlicht. Weder ein HTTP-Scraper noch ein
Browser-Scraper wird ungeprüft öffentliches Produktivmodul. Vor 1.0 wird neu entschieden. Für den regulären
Produktivbetrieb sind die SERP-APIs (DataForSEO, Bright Data) der vorgesehene Weg; AI Mode zuerst über DataForSEO (E-8).

## Konsequenzen
- Pakete G1/G2 können technisch umgesetzt werden; das Publish-Flag der Google-Module ist aus.
- Die Modulaufteilung und der HTTP-Weg stehen in [ADR-018](ADR-018-google-zugriffswege.md).
