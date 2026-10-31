# ADR-016 – Grenzen browserbasierter Google-Adapter

**Status:** Vorgeschlagen; Veröffentlichung offen (E-3, E-4) · **Quellen:** WP3 B-9, R1, Q7, §9, §13

## Kontext
Automatisierte Abfragen der Google-Oberfläche widersprechen Googles Nutzungsbedingungen. Anti-Fingerprinting (Camoufox)
verschärft die Frage. Das ist eine Produkt- und Haftungsentscheidung, keine technische.

## Entscheidung (technisch verbindlich)
- Keine automatische CAPTCHA-Lösung, keine Umgehung von Schutzmaßnahmen, keine Google-Anmeldung.
- Hindernisse werden typisiert gemeldet (`BLOCKED`, `UNSUPPORTED`); `WAIT_FOR_HUMAN` nur im sichtbaren Modus.
- URL-Allowlist je Sitzung, geprüft vor und nach Redirects; keine Downloads, Popups werden geschlossen.
- Konservative Parallelität und Backoff je Engine im Adapter.
- Serp4J lädt nie implizit Browser herunter (`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`).

## Offene Entscheidung (Angelo)
E-3/E-4: Wird `serp4j-engine-google` angeboten und veröffentlicht? Empfehlung: für 0.x im Repository, `@Experimental`,
nicht publiziert; Entscheidung über Veröffentlichung vor 1.0. Für produktive Google-Ergebnisse sind Bright Data und
DataForSEO der bevorzugte Weg.

## Konsequenzen
- Paket G kann technisch umgesetzt werden, die Veröffentlichung bleibt getrennt steuerbar (Publish-Flag je Modul).
