# Spike S0 – Google-Websuche per HTTP ohne Browserkontext (Serp4J #6)

**Datum:** 2026-11-02 · **Ort:** Angelos Rechner (Windows, `curl.exe`, keine Cookies, kein Profil) und Argos-Browser
· **Abfrage:** `GET https://www.google.com/search?q=elasticsearch+java+client&hl=de&gl=de` (ohne `udm`)

## Ergebnis

| Zugriff | Antwort | Treffer |
|---|---|---|
| HTTP, Chrome-130-User-Agent, `Accept-Language: de-DE` | **200**, `text/html`, 94 538 Bytes; `noscript` mit Meta-Refresh auf `/httpservice/retry/enablejs?sei=…`, sonst nur Skripte | **keine** |
| HTTP, Nicht-Browser-User-Agent | **302** auf `https://consent.google.com/ml?continue=…&gl=DE&m=0&pc=srp&uxe=bip&cm=2&hl=de&src=1` | **keine** |
| Browser (Argos, normaler Kontext) | **200**; Consent-Overlay liegt über der Seite, die Treffer sind trotzdem gerendert | 9 organisch (davon 1 Video), Sitelinks, 8 „Wird auch oft gesucht“, nächste Seite |

Alle Ziel-URLs der gerenderten Seite sind `/goto?url=…&gsessionid=…` (keine direkten URLs, kein klassisches `/url?q=`).

## Schlussfolgerung

- Der cookiefreie HTTP-Weg liefert derzeit **keine Suchergebnisse**. Google verlangt entweder die Consent-Seite oder
  JavaScript. Beides ist eine Zugriffskontrolle, die Serp4J nach ADR-016 nicht umgeht: kein Consent-Cookie, kein
  Klick, kein Ausführen der Bot-Prüfskripte, kein Wechsel des User-Agents, um eine einfachere Seite zu erzwingen.
- `google-http` bleibt trotzdem als Adapter bestehen: Er meldet diese Fälle typisiert als `BLOCKED` mit Detail
  `google_obstacle` (`CONSENT`, `JAVASCRIPT_REQUIRED`, `CHALLENGE`, `LOGIN`) und funktioniert sofort, falls Google
  wieder eine HTML-Ergebnisseite ausliefert (gleicher Parser wie der Browserweg).
- Für Quick Web Search ist **`google-browser` über den optionalen Browser-Port** der funktionierende Weg. Der
  Aufrufer kombiniert per `SearchPlan`, z. B. `fallback(google-http, google-browser)` oder direkt `google-browser`
  (ADR-018: kein stiller Wechsel im Provider).
- `/async/folsrch`: nicht verwendet. Die erste Ergebnisseite ist im DOM vollständig; nachgeladene Antworten sind nicht
  durch Fixtures belegt.
- `/goto`-Treffer werden mit dem bestehenden `GoogleGotoUrlResolver` aufgelöst (cookiefreier 302, in #24 live belegt).
  Ob die browsergebundenen `gsessionid`-Links ohne Browser-Cookies ebenfalls 302 liefern, ist noch live zu prüfen.

## Fixtures

- `serp4j-engine-google-common/src/test/resources/fixtures/google/browser-web-de.html` – gerendertes DOM (`#search`,
  `#botstuff`), reduziert und anonymisiert (siehe Sidecar).
- `…/http-enablejs-de.html` – die cookiefreie HTTP-Antwort, auf Kopf und `noscript` gekürzt.
- Der Consent-302 ist als aufgezeichneter `Location`-Header in `GoogleHttpSearchProviderTest` hinterlegt.
