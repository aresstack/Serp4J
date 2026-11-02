# ADR-020 – Einheitliche Browserbeschreibung: Der Host wählt den Browser, Serp4J startet ihn

**Status:** Vorgeschlagen (2026-11-02) · **Quellen:** Angelos Anforderung vom 2026-11-02 („Serp4J soll einen Browser
übergeben bekommen“); Live-Befund askai#45 (Edge → `/sorry/index`); E-5/E-6 (gemeinsame Camoufox-Bibliothek, Java 8);
ADR-011 (Browser-Port), ADR-016, ADR-017

## Kontext
Der Browser-Port (`serp4j-browser-api`: `BrowserRuntime`, `BrowserSession`, `ProfileMode`, `ObstaclePolicy`) ist
seit Paket F vorhanden, und `GoogleBrowserSearchProvider` bekommt seine `BrowserRuntime` bereits injiziert (ADR-017).
Was fehlt, ist eine **einheitliche Beschreibung, welcher Browser es sein soll**. Heute muss der Host dafür
`PlaywrightBrowserRuntime.builder()` mit Playwright-Begriffen (`channel("msedge")`, `executablePath`,
`PlaywrightBrowserType`) befüllen. Das hat drei Folgen:

1. AskAI kennt Playwright-Interna, obwohl es nur „Edge“, „Chrome“, „Firefox“ oder „Camoufox unter Pfad X“ sagen will.
2. Es gibt keine Erkennung installierter Browser; jeder Host sucht Pfade selbst.
3. Portable Browser wie Camoufox (Firefox-Engine, eigener Start mit Fingerprint-Konfiguration) passen nicht in das
   Kanal-Modell von Playwright.

Der Live-Test zeigt außerdem: Welcher Browser Google-Treffer liefert, ist eine Betriebsentscheidung des Hosts (Edge →
CAPTCHA, Camoufox in Argos → Treffer). Serp4J darf sie nicht fest verdrahten.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Host übergibt nur eine fertige `BrowserRuntime` (Status quo) | sauber, aber jeder Host baut Playwright-/Camoufox-Start selbst nach; widerspricht „AskAI baut die Browserimplementierung nicht nach“ |
| B. Host übergibt einen **verbundenen Browser** (CDP-/Playwright-WebSocket-Endpunkt) | gut für fremd verwaltete Prozesse; CDP nur Chromium, Camoufox nur über Playwright-Server; Lebenszyklus beim Host |
| C. **Host übergibt eine `BrowserSpec` (Wertobjekt), Serp4J erzeugt daraus über eine Factory-SPI die `BrowserRuntime`** | Host spricht nur Produkt/Pfad/Modus; Backends austauschbar; Erkennung installierter Browser zentral |
| D. Serp4J wählt selbst (Auto-Detect + Heuristik) | widerspricht der Anforderung; Hostentscheidung (CAPTCHA-Erfahrung) ginge verloren |

## Entscheidung
**Option C, mit B als Sonderform derselben Beschreibung.** A bleibt erlaubt (eigene `BrowserRuntime` direkt injizieren).

### 1. Wertobjekte im Port (`serp4j-browser-api`, Java 8, keine Abhängigkeiten)
- `BrowserProduct` – offener Wertetyp (wie `ProviderId`), mit Konstanten `CHROME`, `EDGE`, `CHROMIUM`, `FIREFOX`,
  `CAMOUFOX`, `WEBKIT`; jeweils mit `EngineFamily`. Weitere Produkte (Brave, Opera, …) ohne API-Bruch.
- `BrowserSpec` – unveränderlich, Builder. Genau eine von drei Quellen:
  - `installed(product)` – vorinstallierter Browser; Pfad wird von `BrowserDiscovery` ermittelt oder vom Backend
    (Playwright-Kanal) gefunden;
  - `executable(product, path)` – portabler oder selbst installierter Browser unter festem Pfad (Camoufox,
    portable Firefox/Chromium);
  - `connect(product, endpoint)` – bereits laufender, vom Host verwalteter Browser (Playwright-Server `ws://…`
    oder CDP `http://…`/`ws://…` für Chromium).

  Dazu: `headless` (Default `true`), zusätzliche Startargumente, Umgebungsvariablen für den Browserprozess,
  `profileRoot` (Wurzel für `ProfileMode.persistentExclusive`), `maxConcurrentSessions`, Start-/Schließ-Timeouts,
  freie `option(key, value)` für backend-spezifische Werte (z. B. Camoufox-Fingerprint-Konfiguration).
- `BrowserInstallation` – Ergebnis der Erkennung: Produkt, Pfad, optional Version, Herkunft (`INSTALLED`/`PORTABLE`).
- `BrowserDiscovery` – erkennt installierte Browser **ohne Start**, nur über bekannte Pfade je OS
  (Windows: `%ProgramFiles%`, `%ProgramFiles(x86)%`, `%LOCALAPPDATA%`; macOS: `/Applications`, `~/Applications`;
  Linux: `PATH` + `/usr/bin`, `/opt`, Snap/Flatpak-Exports). Keine Registry-/Prozessaufrufe, deterministisch testbar
  über injizierbares Dateisystem/Env.

### 2. Factory-SPI
- `BrowserRuntimeFactory` (SPI, `ServiceLoader`): `String getBackendId()`, `boolean supports(BrowserSpec)`,
  `BrowserRuntime create(BrowserSpec)`.
- `BrowserRuntimes.create(spec)` wählt die erste unterstützende Factory; mehrdeutig/keine → klare
  `BrowserUnavailableException` mit Liste der vorhandenen Backends. `BrowserRuntimes.create(spec, factory)` für
  explizite Wahl ohne ServiceLoader.
- Zuordnung:
  | Produkt | Backend | Umsetzung |
  |---|---|---|
  | CHROME / EDGE | `playwright` | `installed` → Playwright-Kanal `chrome`/`msedge`; `executable` → `executablePath` |
  | CHROMIUM | `playwright` | Playwright-Chromium oder `executablePath` |
  | FIREFOX | `playwright` | nur Playwrights eigenes Firefox-Build (Juggler); Stock-Firefox ist nicht steuerbar → `supports` = false mit Begründung bei `installed(FIREFOX)` ohne Playwright-Build |
  | WEBKIT | `playwright` | Playwright-WebKit |
  | CAMOUFOX | `camoufox` (eigenes Modul) | Playwright-Firefox mit `executablePath` = Camoufox-Binary, Fingerprint-Konfiguration über `CAMOU_CONFIG_*`-Umgebung |
  | jedes, `connect` | `playwright` | `BrowserType.connect(ws)` bzw. `connectOverCDP` (nur Chromium) |

### 3. Lebenszyklus und Ownership (ADR-017)
- Die per `BrowserRuntimes.create` erzeugte Runtime gehört **dem Aufrufer** (Host). Provider schließen sie nie.
- Bei `connect` beendet `close()` nur die Verbindung und die von Serp4J geöffneten Kontexte, **nie den
  Browserprozess** – der gehört dem Host. Hard-Kill (Paket F) gilt nur für selbst gestartete Prozesse.
- Profile bleiben Sache von `BrowserSessionOptions.profileMode`; `profileRoot` in der Spec legt nur fest, wo
  persistente Profile liegen. Default bleibt `ephemeral` (frisches Profil je Sitzung).

### 4. Obstacle-Erkennung bleibt backend-neutral
Erkennung läuft auf `PageSnapshot`/`NavigationResult` (finale URL, DOM-Marker) im Provider bzw. in
`PageObstacle`/`ObstaclePolicy` des Ports – nicht im Backend. Camoufox, Edge oder ein verbundener Browser liefern
dieselben Snapshots; `GoogleObstacleDetector` bleibt unverändert. ADR-016 gilt unverändert: nichts wird
weggeklickt, kein CAPTCHA gelöst. Die Wahl eines anderen Browsers ist **keine** Umgehung im Sinne von ADR-016,
sondern eine Betriebsentscheidung des Hosts; Serp4J verspricht für keinen Browser Treffer.

### 5. Camoufox (E-5/E-6)
Eigenes Modul `serp4j-browser-camoufox` (Java 8), hängt nur an `serp4j-browser-playwright`. Es **lädt Camoufox
nicht herunter** und installiert nichts: der Host übergibt den Pfad (`executable(CAMOUFOX, path)`) oder
`BrowserDiscovery` findet eine portable Installation an bekannten Orten (`~/.cache/camoufox`,
`%LOCALAPPDATA%\camoufox`). Die Fingerprint-Logik kommt aus der gemeinsamen Camoufox-Bibliothek (E-5), sobald sie
existiert; bis dahin reicht die Factory nur explizit übergebene Konfiguration (`option("camoufox.config", json)`)
durch.

### 6. Enginesneutrale Browser-Suche als eigenes, publiziertes Modul (Angelo, 2026-11-02)
Die Browser-Suche ist nicht auf Google beschränkt. Neues Modul **`serp4j-browser-search`** (Java 8,
`serp4j.published`, eigenes Maven-Central-Artefakt `com.aresstack:serp4j-browser-search`), Abhängigkeiten nur
`serp4j-core` + `serp4j-browser-api`. Wer es nicht einbindet, zieht keinen Browsercode.

- `BrowserSearchProvider implements SearchProvider` – generischer Ablauf, heute in
  `GoogleBrowserSearchProvider` fest verdrahtet: Sitzung öffnen (`ProfileMode`, `ObstaclePolicy.failFast()`),
  navigieren, Snapshot ziehen, Obstacle prüfen, parsen, Sitzung immer schließen, Cancellation/Deadline, Ownership
  nach ADR-017.
- SPI **`BrowserSearchEngine`** (ein Adapter je Suchmaschine): `engine()` (Wertetyp `SearchEngine`),
  `capabilities()`, `NavigationRequest buildNavigation(SearchRequest)`, `Optional<PageObstacle> detectObstacle(
  NavigationResult, PageSnapshot)`, `SearchData parse(PageSnapshot, SearchRequest)`. Gefunden per `ServiceLoader`
  oder explizit übergeben.
- **Google bleibt unpubliziert** (ADR-016, E-3/E-4): `serp4j-engine-google-browser` wird zu einem
  `BrowserSearchEngine`-Adapter auf diesem Kern; Parser/Obstacle-Erkennung bleiben in `serp4j-engine-google-common`.
  Generisches wird publiziert, Google-Spezifisches nicht.
- Weitere Adapter (z. B. Bing, DuckDuckGo, Startpage) sind eigene Module; ob sie publiziert werden, entscheidet
  Angelo je Engine nach Prüfung der Nutzungsbedingungen.
- **PR #30 kann so bleiben:** Er publiziert nur die Google-Module nach mavenLocal und begrenzt die Central-Aggregation
  auf `serp4j.published`. `serp4j-browser-search` trägt `serp4j.published` und landet damit automatisch in Central;
  die Google-Module hängen weiter nur an mavenLocal.

## Folgen
- AskAI übergibt `BrowserSpec` statt Playwright-Begriffe; seine Karte „Google-Suchbrowser“ wird zu
  Produkt + Pfad + sichtbar/headless. Der eigene Playwright-Prozess in askai#45 kann entfallen
  (`BrowserRuntimes.create(spec)`) oder als `connect(...)` übergeben werden.
- `PlaywrightBrowserType`/`channel` bleiben als Detail des Playwright-Backends; die öffentliche Hostschnittstelle
  ist `BrowserSpec`.
- Keine Abwärtskompatibilität nötig; `PlaywrightBrowserRuntime.builder()` bleibt trotzdem für Tests und
  Sonderfälle bestehen.
