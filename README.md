# Serp4J

Provider-unabhängige Java-Bibliothek für Web-Suchergebnisse (SERP): ein gemeinsames Domänenmodell, Ports für
Suchanbieter und – in späteren Arbeitspaketen – Adapter für Cloud-APIs (Brave, Bright Data, DataForSEO) sowie für
die Suche im Browser.

**Status:** `0.1.0-SNAPSHOT`, in Entwicklung. Umgesetzt sind Paket A (Build-Gerüst, Core-Domain, Ports,
HTTP-Port) und Paket F (Browser-Port und Playwright-Adapter). Die verbindliche Architektur steht in [docs/architecture](docs/architecture/architecture.md), die
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
| `serp4j-browser-api` | 8 | Browser-Port `BrowserRuntime`/`BrowserSession`, Navigation, Warten, Skripte, Interaktion, Snapshots, Netzwerkbeobachtung, `UrlPolicy`, Fehlertypen; kennt den Core nicht | keine |
| `serp4j-browser-testkit` | 8 | `FakeBrowserRuntime` und TCK `BrowserRuntimeContractTest` (JUnit 5) für jede Implementierung | `serp4j-browser-api` |
| `serp4j-browser-playwright` | 8 | Adapter über Playwright Java: ein Owner-Thread je Browser, Kapazitätsgrenze, Probe, Remote-Server; ohne JS-Treiber | `serp4j-browser-api`, Playwright Java |
| `serp4j-browser-playwright-driver-node` | 8 | Treiber: Node-Treiber von Playwright (plattformspezifisches Bundle) | Playwright `driver-bundle` |
| `serp4j-browser-playwright-driver-graal` | 21 | Treiber: playwright4j (Playwright-Treiber in GraalJS, ohne Node) | playwright4j |
| `serp4j-bom` | – | Bill of Materials über alle publizierten Module (Lockstep-Version) | – |

Die weiteren Module (`serp4j-orchestration`, `serp4j-http-*`, `serp4j-provider-*`,
`serp4j-testkit`) sind in `settings.gradle.kts` bereits vorgesehen und kommen mit den Paketen
B–H hinzu.

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

Der normale Build startet nie einen Browser. Die Browser-TCK läuft getrennt und braucht ein installiertes
Chromium (Serp4J lädt selbst keine Browser herunter):

```bash
./gradlew :serp4j-browser-playwright-driver-node:installPlaywrightBrowsers   # einmalig: Chromium installieren
./gradlew :serp4j-browser-playwright-driver-node:browserTest \
          :serp4j-browser-playwright-driver-graal:browserTest -Pserp4j.browserTests=true
# vorhandenes Binary statt Download: SERP4J_BROWSER_EXECUTABLE=/pfad/zu/chrome
```

## Verwenden

Die Artefakte erscheinen unter `com.aresstack:serp4j-*` auf Maven Central (siehe [PUBLISHING.md](PUBLISHING.md));
vor dem ersten Release stehen sie nur lokal über `publishToMavenLocal` bereit.

```kotlin
dependencies {
    implementation(platform("com.aresstack:serp4j-bom:0.1.0"))
    implementation("com.aresstack:serp4j-core")
}
```

### Browser

Wer im Browser suchen will, nimmt den Adapter und **genau einen** Treiber dazu; ohne oder mit zwei Treibern meldet
`probe()` `DRIVER_NOT_FOUND` bzw. `MULTIPLE_DRIVERS`, statt beim ersten Aufruf zu scheitern (ADR-011).

```kotlin
dependencies {
    implementation("com.aresstack:serp4j-browser-playwright")
    runtimeOnly("com.aresstack:serp4j-browser-playwright-driver-node")   // Java 8, Node-Treiber
    // oder: runtimeOnly("com.aresstack:serp4j-browser-playwright-driver-graal")  // Java 21, ohne Node
}
```

```java
try (BrowserRuntime runtime = PlaywrightBrowserRuntime.builder().maxConcurrentSessions(2).build();
     BrowserSession session = runtime.openSession(BrowserSessionOptions.builder()
             .urlPolicy(UrlPolicy.allowHosts("example.org", "*.example.org")).build())) {
    NavigationResult result = session.navigate(NavigationRequest.to("https://example.org/"));
}
```

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
