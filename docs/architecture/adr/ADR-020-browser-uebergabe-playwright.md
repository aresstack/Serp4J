# ADR-020 – Der Host übergibt den Playwright-Browser; `serp4j-playwright` als optionale Erweiterung

**Status:** Angenommen (Angelo, 2026-11-02) · **Quellen:** Angelos Zielarchitektur vom 2026-11-02 (09:24, 09:51,
09:54); Live-Befund askai#45 (Edge → `/sorry/index`); ADR-016, ADR-017; E-3/E-4

## Kontext
`GoogleBrowserSearchProvider` (PR #28) und der Browser-Port aus Paket F (`BrowserRuntime`, `ProfileMode`,
`ObstaclePolicy`, Hard-Kill, Playwright-Adapter mit Treibermodulen) ließen Serp4J Browser starten, Profile verwalten
und Prozesse beenden. AskAI hat aber bereits eine funktionierende Google-Browsersuche und eine eigene
Browserverwaltung (Camoufox, Sidecar, Java 21). Zwei Browserverwaltungen nebeneinander sind unnötig; welcher
Browser Google-Treffer liefert (Edge → CAPTCHA, Camoufox → Treffer), ist eine Betriebsentscheidung des Hosts.

Ein erster Entwurf dieses ADRs (`BrowserSpec`, `BrowserDiscovery`, `BrowserRuntimeFactory`, Camoufox-Backend) ist
verworfen: Er hätte Serp4J wieder zur Browserverwaltung gemacht.

## Alternativen
| Option | Bewertung |
|---|---|
| A. Serp4J wählt und startet den Browser (`BrowserSpec` + Factory, erster Entwurf) | Browserverwaltung in zwei Systemen; Erkennung installierter Browser, Profile, Hard-Kill in Serp4J – verworfen |
| B. Serp4J bekommt eine eigene `BrowserRuntime`-Abstraktion injiziert (Paket F) | Abstraktion über Playwright, die kein Host braucht; Google-Adapter müsste trotzdem in Serp4J liegen – verworfen |
| C. **Host übergibt das Playwright-`Browser`-Objekt bei jeder Suche; Serp4J nutzt es nur** | keine Browserverwaltung in Serp4J, keine Abstraktion über Playwright, Engines bleiben beim Host |

## Entscheidung
Option C.

### 1. Module und Artefakte
| Gradle-Modul | Maven-Artefakt | Java | Aufgabe |
|---|---|---|---|
| `serp4j-core` | `com.aresstack:serp4j-core` | 8 | Allgemeine Such-API, Provider-Schnittstellen, Datenmodell, API-basierte Provider |
| `serp4j-playwright` | `com.aresstack:serp4j-playwright` | 21 | Playwright-Integration, damit Hosts eigene lokale browsergestützte Provider implementieren |
| `askai-playwright-google` (in AskAI) | – (nicht publiziert) | 21 | bestehende Google-Browsersuche aus AskAI, auf `serp4j-core` + `serp4j-playwright` umgestellt; baut auf AskAIs `askai-playwright` (Playwright-/Browserverwaltung des Hosts) auf |

Beide Serp4J-Module gehen regulär nach Maven Central. `serp4j-playwright` hängt an `serp4j-core` und der
Playwright-Java-API ohne Treiber; den Treiber (driver-bundle oder playwright4j) bringt der Host.

### 2. Browserübergabe und Verantwortlichkeiten
Der Host initialisiert Playwright, startet den Browser, hält ihn am Leben, startet ihn nach einem Absturz neu und
übergibt die aktuell gültige `Browser`-Instanz **bei jeder Suchanfrage**:

```java
SearchResponse response = googleProvider.search(request, context, browser);
```

`serp4j-playwright` stellt dafür bereit:
- `PlaywrightSearchProvider` – Schnittstelle für lokale Provider: `getDescriptor()`,
  `search(SearchRequest, SearchContext, Browser)` (synchron, auf dem Thread des Aufrufers).
- `PlaywrightSearchSupport` – gemeinsame Funktionen: Vorprüfung (Browser verbunden, Capabilities, Cancellation,
  Deadline), Timeout aus dem `SearchContext`, Übersetzung von Playwright-Fehlern in `SearchProviderException`
  (`TIMEOUT`, `CANCELLED`, `UNAVAILABLE` bei geschlossenem Browser, sonst `INTERNAL`), Metadaten nach ADR-008
  inklusive Raw-Payload nach `RawPayloadPolicy`.
- `PlaywrightSearchFailures` – `obstacle(...)` (`BLOCKED`, ADR-016) und `unrecognized(...)` (`RESPONSE_INVALID`).

Serp4J startet, beendet oder ersetzt keine Browserprozesse, verwaltet keine Profile, Kontexte oder Sitzungen und
hält zwischen zwei Aufrufen keine Referenz auf den Browser. Wie ein Provider den übergebenen Browser nutzt (Seiten,
Waits), ist Sache des Providers.

**Nicht Teil von Serp4J:** eine generische Browser-Sucharchitektur (`BrowserSearchProvider`, `BrowserSearchEngine`,
SPI), ein `Supplier<Browser>`, ein `BrowserContext` je Suche, Browser-Erkennung, Backend-Auswahl, Camoufox-Start.

### 3. AskAI
AskAI verwaltet zwei unabhängige Camoufox-Instanzen: den **Suchbrowser** (headless, dauerhaft, wird an Serp4J
übergeben, bei Absturz von AskAI neu gestartet) und den **Research-Browser** (sichtbar, öffnet die gefundenen
Seiten). Die eigentliche Recherche bleibt in AskAI. `askai-playwright-google` registriert Google als Serp4J-Provider;
AskAI behält keine parallele HTTP-/HTML-Suche.

### 4. Folgen für vorhandene Module
- Paket F (`serp4j-browser-api`, `serp4j-browser-playwright`, Treibermodule, `serp4j-browser-testkit`) und die
  Google-Module (`serp4j-engine-google-*`, PR #28/#30) sind überholt und werden in einem Folge-PR entfernt; der
  Google-HTML-Parser aus `google-common` wandert mit dem Adapter nach AskAI.
- ADR-011 (Browser-Port), ADR-016 (Grenzen, gilt für Provider unverändert) und ADR-018 (Google-Zugriffswege) werden
  entsprechend angepasst; ADR-017 gilt unverändert: Der Browser gehört dem Host.
- Die Playwright-Version von `serp4j-playwright` und des Hosts müssen übereinstimmen (das `Browser`-Objekt geht über
  die Modulgrenze); die Version steht im BOM.
