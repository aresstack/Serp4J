# Serp4J

Provider-unabhängige Java-Bibliothek für Web-Suchergebnisse (SERP): ein gemeinsames Domänenmodell, Ports für
Suchanbieter und – in späteren Arbeitspaketen – Adapter für Cloud-APIs (Brave, Bright Data, DataForSEO, SerpApi) sowie für
die Suche im Browser.

**Status:** `0.1.0-SNAPSHOT`, in Entwicklung. Umgesetzt sind Paket A (Build-Gerüst, Core-Domain, Ports,
HTTP-Port), Paket E (Testkit) und Paket F (Browser-Port und Playwright-Adapter). Die verbindliche Architektur steht in [docs/architecture](docs/architecture/architecture.md), die
Entscheidungen in den [ADRs](docs/architecture/adr), die Reihenfolge der Arbeitspakete im
[Implementierungsplan](docs/architecture/implementation-plan.md).

## Module

| Modul | Java | Inhalt | Abhängigkeiten |
|---|---|---|---|
| `serp4j-core` | 8 | Domänenmodell (`SearchRequest`, `SearchResponse` = `SearchData` + `SearchMetadata` + `SearchAnalysis`, `SerpFeature`-Hierarchie mit Visitor, `Extensions`, `RawPayload`), Ports (`SearchProvider`, `ConversationalSearchProvider`, `SearchAnalyzer`, `CredentialProvider`, `SearchProviderFactory`), Capabilities, Fehlermodell | keine |
| `serp4j-http-api` | 8 | `HttpTransport`-Port, `HttpRequest`/`HttpResponse`/`HttpHeaders`, `RequestAuthentication`, `UrlQueryBuilder` | keine |
| `serp4j-http-ahc` | 8 | `AhcHttpTransport`: `HttpTransport` auf async-http-client 2.x, geteilter Client, Redirects ohne Credential-Weitergabe an fremde Hosts, Warteschlange statt Ablehnung bei Verbindungslimit | `serp4j-http-api`, async-http-client 2.16 (Netty) |
| `serp4j-mapping` | 8 | Response-Mapping: deklarative Mapping-Definitionen, `MappingEngine`, Custom Mapper je Definition, Prüfbausteine für Fixture-Tests ([Format](docs/mapping/mapping-definition-format.md)) | `serp4j-core` |
| `serp4j-json` | 8 | `JsonTrees`: JSON-Text ↔ `StructuredValue` (einzige Stelle mit Gson) | `serp4j-core`, Gson |
| `serp4j-provider-dataforseo` | 8 | Cloud-Adapter DataForSEO SERP API v3: `DataForSeoSearchProvider` für Google/Bing organic (live/regular, live/advanced), Basic-Auth nur im Header, Statuscodes im Body, Mapping-Definition `dataforseo-serp-organic` ([README](serp4j-provider-dataforseo/README.md)) | core, http-api; mapping, json (`implementation`) |
| `serp4j-testkit` | 8 | Test-Scope: `MockSearchProvider`, `ScriptedSearchProvider`, `ScriptedConversationalProvider`, `FakeHttpTransport`, Fixtures mit Herkunftskennzeichnung und Secret-Scan, abstrakte Contract-Tests ([README](serp4j-testkit/README.md)) | core, http-api; JUnit 5 `compileOnly` |
| `serp4j-playwright` | 21 | Playwright-Integration für lokale browsergestützte Provider (ADR-020): `PlaywrightSearchProvider` (`search(request, context, browser)`), `PlaywrightSearchSupport` (Vorprüfung, Timeouts, Fehlerübersetzung, Metadaten), `PlaywrightSearchFailures`; der Host startet und übergibt den Browser, Serp4J verwaltet keinen | `serp4j-core`, Playwright Java (ohne Treiber) |
| `serp4j-bom` | – | Bill of Materials über alle publizierten Module (Lockstep-Version) | – |

Die weiteren Module (`serp4j-orchestration`, `serp4j-http-*`, `serp4j-provider-*`) sind in
`settings.gradle.kts` bereits vorgesehen und kommen mit den Paketen B–H hinzu.

## Bauen

Voraussetzungen: JDK 21 als Compiler-Toolchain (`--release 8` für die Java-8-Module) und ein JDK 8 für den
zusätzlichen Testlauf. Fehlt eines, lädt Gradle es über die Foojay-Toolchain-Auflösung nach. Gradle selbst muss mit
JDK 17 oder neuer gestartet werden (z. B. JDK 21), weil die Build-Plugins (Foojay-Resolver, nmcp) Java-17-Bytecode
sind; JDK 8 wird nur als Test-Laufzeit gebraucht.

```bash
./gradlew build                            # Kompilieren, Tests auf JDK 21 und JDK 8, Javadoc, ArchUnit
./gradlew build -Pserp4j.java8Tests=false  # ohne den JDK-8-Testlauf (z. B. offline)
./gradlew publishToMavenLocal              # Artefakte lokal in ~/.m2 ablegen
```

Der normale Build startet nie einen Browser. Die Browser-Tests von `serp4j-playwright` laufen getrennt und brauchen
ein installiertes Chromium (Serp4J lädt selbst keine Browser herunter):

```bash
./gradlew :serp4j-playwright:installPlaywrightBrowsers      # einmalig: Chromium installieren
./gradlew :serp4j-playwright:browserTest -Pserp4j.browserTests=true
# vorhandenes Binary statt Download: SERP4J_BROWSER_EXECUTABLE=/pfad/zu/chrome
```

### Echte Provider-Antworten aufzeichnen (E-1)

Live-Tests laufen nur mit `-Pserp4j.live=true` und den Zugangsdaten als Umgebungsvariablen; Zugangsdaten gehen nur
als HTTP-Header raus (Ausnahme SerpApi: dort verlangt die API den Key als maskierten URL-Parameter `api_key`). Mit `-Pserp4j.recordFixtures=true` landet die Rohantwort unter
`<modul>/build/recorded-fixtures/` samt Sidecar `anonymized=false`. Vor dem Übernehmen nach
`src/test/resources/fixtures/` von Hand prüfen und anonymisieren, dann im Sidecar `anonymized=true` setzen.

```bash
SERP4J_BRAVE_API_KEY=… ./gradlew :serp4j-provider-brave:liveTest -Pserp4j.live=true -Pserp4j.recordFixtures=true
SERP4J_DATAFORSEO_LOGIN=… SERP4J_DATAFORSEO_PASSWORD=… \
  ./gradlew :serp4j-provider-dataforseo:liveTest -Pserp4j.live=true -Pserp4j.recordFixtures=true
# SerpApi verlangt den Key als URL-Parameter api_key; er wird in URLs/Logs maskiert. Eine Suche je Test (Free: 250/Monat).
SERP4J_SERPAPI_API_KEY=… ./gradlew :serp4j-provider-serpapi:liveTest -Pserp4j.live=true -Pserp4j.recordFixtures=true
```

Liegen die DataForSEO-Zugangsdaten als ein einzelner Base64-Wert `login:password` vor (so wie im
`Authorization: Basic`-Header), zerlegt man ihn nur im laufenden Prozess. Nichts davon ausgeben, loggen oder speichern:

```bash
# Bash; DATA_FOR_SEO = Base64("login:password")
pair="$(printf '%s' "$DATA_FOR_SEO" | base64 -d)"
SERP4J_DATAFORSEO_LOGIN="${pair%%:*}" SERP4J_DATAFORSEO_PASSWORD="${pair#*:}" \
  ./gradlew :serp4j-provider-dataforseo:liveTest -Pserp4j.live=true -Pserp4j.recordFixtures=true
unset pair
```

```powershell
# PowerShell
$pair = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($env:DATA_FOR_SEO))
$i = $pair.IndexOf(':'); if ($i -le 0) { throw 'DATA_FOR_SEO ist kein Base64(login:password)' }
$env:SERP4J_DATAFORSEO_LOGIN = $pair.Substring(0, $i); $env:SERP4J_DATAFORSEO_PASSWORD = $pair.Substring($i + 1)
try { .\gradlew.bat :serp4j-provider-dataforseo:liveTest -Pserp4j.live=true -Pserp4j.recordFixtures=true --no-daemon }
finally { Remove-Item Env:SERP4J_DATAFORSEO_LOGIN, Env:SERP4J_DATAFORSEO_PASSWORD -ErrorAction SilentlyContinue; $pair = $null }
```

Ein Live-Lauf kostet etwas DataForSEO-Guthaben.

## Verwenden

Die Artefakte erscheinen unter `com.aresstack:serp4j-*` auf Maven Central (siehe [PUBLISHING.md](PUBLISHING.md));
vor dem ersten Release stehen sie nur lokal über `publishToMavenLocal` bereit.

```kotlin
dependencies {
    implementation(platform("com.aresstack:serp4j-bom:0.1.0"))
    implementation("com.aresstack:serp4j-core")
}
```

### Browser (ADR-020)

Serp4J startet keinen Browser. Wer im Browser suchen will, initialisiert Playwright selbst, startet den Browser
(z. B. Camoufox) und übergibt ihn bei jeder Suche einem eigenen Provider, der `PlaywrightSearchProvider`
implementiert. `serp4j-playwright` bringt die Playwright-Java-API ohne Treiber mit; den Treiber (offizielles
`driver-bundle` oder `playwright4j`) wählt der Host.

```kotlin
dependencies {
    implementation("com.aresstack:serp4j-playwright")           // Java 21; zieht serp4j-core
    runtimeOnly("com.microsoft.playwright:driver-bundle:1.62.0") // oder com.aresstack:playwright4j
}
```

```java
try (Playwright playwright = Playwright.create();
     Browser browser = playwright.chromium().launch()) {                 // gehört dem Host
    SearchResponse response = myProvider.search(SearchRequest.builder("serp4j").build(), browser);
}
```

Ein Provider nutzt `PlaywrightSearchSupport` (Vorprüfung, Timeout aus dem `SearchContext`, Fehlerübersetzung,
Metadaten) und meldet Consent-Dialoge oder Challenges als `BLOCKED` (`PlaywrightSearchFailures.obstacle`), statt sie
wegzuklicken (ADR-016). Ein vollständiges Beispiel ist `LocalPageSearchProvider` im `browserTest`-Source-Set.

Persistente Profile (`ProfileMode.persistentExclusive("google-de")`, z. B. für Consent-Cookies) gibt es nur mit
`PlaywrightBrowserRuntime.builder().profileDirectory(pfad)`; ein Profil wird per Dateisperre exklusiv geleast.
Hindernisse wie Consent- oder Challenge-Seiten erkennt der Engine-Adapter und übergibt sie an
`session.resolveObstacle(...)`: mit `ObstaclePolicy.failFast()` (Default) kommt sofort eine `PageObstacleException`,
mit `ObstaclePolicy.waitForHuman(timeout)` wartet die Session im sichtbaren Fenster (nur `headless(false)`), bis
ein Mensch das Hindernis gelöst hat. Serp4J löst oder umgeht keine CAPTCHAs.

Die Runtime gehört dem, der sie baut (ADR-017), und startet den Browser erst beim ersten Bedarf. Mit dem
Graal-Treiber kein Fat-JAR bauen: playwright4j und GraalJS müssen als eigene JARs auf dem Klassenpfad bleiben.

## Grundsätze

- Clean Architecture: `serp4j-core` kennt keine Anbieter und keine Infrastruktur, Adapter hängen vom Core ab, nie
  umgekehrt (ADR-001). ArchUnit-Regeln sichern das im Build.
- Java 8 für Core, HTTP-Port und Provider-Adapter (ADR-002); unveränderliche Wertobjekte mit Buildern und
  `toBuilder()`, offene Werttypen statt Enums (ADR-004).
- Daten, Herkunft und Auswertung getrennt: `SearchResponse` = `SearchData` + `SearchMetadata` + optionale
  `SearchAnalysis` (ADR-003, ADR-008).
- Ein Fehlertyp `SearchProviderException` mit Kategorie, `retryable` und `retryAfter` (ADR-007).
- Injizierte Infrastruktur gehört dem Aufrufer; ein Provider schließt nur, was er selbst erzeugt hat (ADR-017).
- Konversationelle Suche (`ConversationalSearchProvider`, `SearchSession`) ist mit `@Experimental` markiert und kann
  sich in 0.x noch ändern.

## Lizenz

[MIT](LICENSE)
