# Changelog

Alle nennenswerten Änderungen an Serp4J stehen in dieser Datei. Das Format folgt
[Keep a Changelog](https://keepachangelog.com/de/1.1.0/); die Versionierung folgt
[Semantic Versioning](https://semver.org/lang/de/) im Lockstep aller Module (ADR-015). Vor 1.0 können sich
öffentliche APIs noch ändern; `@Experimental` markiert die Teile, bei denen das besonders wahrscheinlich ist.

## [Unreleased]

### Added

- `serp4j-mapping` (Java 8, nur `serp4j-core`): Response-Mapping in zwei Ebenen (ADR-019) – deklarative, versionierte
  Mapping-Definitionen mit strikter Validierung (`MappingDefinitions`), kleine Pfadsprache (`TreePath`), Sektionen
  `items`/`features`/`elements` mit Diskriminator, `fieldsByType`, Positionsmodi, Pflichtfeldern, Defaults und
  Konvertern (`ValueConverters`); Custom Mapper (`ItemMapper`, `FeatureMapper`, `MetadataMapper`) in einer
  `CustomMapperRegistry`, die an genau eine Definition gebunden ist; `MappingEngine` erhält nicht konsumierte Daten
  blattgenau in `Extensions.unmapped`, unbekannte Typen als `GenericSerpFeature`, und wirft nie wegen einer Antwort;
  Prüfbausteine `MappingDefinitionVerifier` und `MappingSnapshot` für Fixture-Tests. Formatreferenz:
  `docs/mapping/mapping-definition-format.md`.
- `serp4j-json` (Teil J1): `JsonTrees` (JSON-Text ↔ `StructuredValue`, exakte Zahlen, strikt), einzige Stelle mit Gson.
- Paket F, Browser (ADR-010, ADR-011, ADR-017, WP3):
  - `serp4j-browser-api` (Java 8, keine Abhängigkeiten, kennt den Core nicht): `BrowserRuntime` mit `getInfo()`,
    `probe()` und `openSession(...)`; `BrowserSession` mit `navigate`, `await` (`WaitCondition`), `evaluate`
    (`PageScript`, Ergebnis als `BrowserValue`), `click`/`type`/`press`, `snapshot` und `network()`
    (`NetworkCapture`, ohne Cookie- und Authorization-Header); `UrlPolicy` als Host-Allowlist vor und nach jeder
    Navigation; Fehlertypen unter `BrowserException`.
  - `serp4j-browser-testkit`: `FakeBrowserRuntime` für Provider-Tests ohne Browser und die TCK
    `BrowserRuntimeContractTest` (27 Fälle: Isolation, Kapazität, Timeouts, Absturz, Prozessaufräumen, Policy).
  - `serp4j-browser-playwright` (Java 8): `PlaywrightBrowserRuntime` mit einem Owner-Thread je Browser,
    Kontext je Session, harter Kapazitätsgrenze, Lebensdauer-/Leerlauf-/Navigationsbudget, `RemotePlaywrightRuntime`
    für einen Playwright-Server und der Prüfung „genau ein Treiber“ über `PlaywrightDriverModule`.
  - Treiber `serp4j-browser-playwright-driver-node` (Java 8) und `-driver-graal` (Java 21, playwright4j).
  - Build: Plugin `serp4j.browser-tests` (Source-Set `browserTest`, nur mit `-Pserp4j.browserTests=true`);
    CI-Job `browser-tck` mit Chromium und beiden Treibern.
- Gradle-Multi-Project: Wrapper 8.14.3, Convention-Plugins in `build-logic` (`serp4j.java-library`, `serp4j.java8`,
  `serp4j.java21`, `serp4j.published`, `serp4j.provider`), Version Catalog, `serp4j-bom`, Publishing über
  `com.gradleup.nmcp` mit signierten Sources- und Javadoc-JARs (ADR-015).
- `serp4j-core` (Java 8, keine Abhängigkeiten): Domänenmodell `SearchRequest`, `SearchResponse`, `SearchData`,
  `SearchResultItem`, `SerpFeature`-Hierarchie mit Visitor und `GenericSerpFeature`, `SearchMetadata` mit
  `QueryInfo`/`PageInfo`/`ProviderUsage`/`SearchWarning`, `SearchAnalysis`, `Extensions`, `StructuredValue`,
  `RawPayload`; Ports `SearchProvider`, `ConversationalSearchProvider`/`SearchSession` (`@Experimental`),
  `SearchAnalyzer`, `CredentialProvider`, `SearchProviderFactory`/`ProviderEnvironment`; `SearchCapabilities` mit
  `check(SearchRequest)`; Fehlermodell `SearchProviderException`/`SearchSessionException` mit `SearchErrorCategory`
  (ADR-003, ADR-004, ADR-007, ADR-008, ADR-017).
- `serp4j-http-api` (Java 8, keine Abhängigkeiten): `HttpTransport`, `HttpRequest`, `HttpResponse`, `HttpHeaders`,
  `HttpTransportException`, `RequestAuthentication` (Bearer, Basic, Header, Query-Parameter), `UrlQueryBuilder`.
- Tests: Unit-Tests für Validierung, Unveränderlichkeit, `equals`/`hashCode`, Builder und `toBuilder()`;
  ArchUnit-Regeln für die Modulgrenzen; Contract-Tests für `SearchMetadata` (Review-Punkt B) und die
  Ownership-Regel (Review-Punkt C) mit der Vorlage `SearchProviderContract`.
- CI: Build mit JDK 21, Tests auf JDK 21 und JDK 8, Javadoc ohne Fehler, Central-Portal-Bündel; Release-Workflow
  für Tags `vX.Y.Z`, der nur Commits aus `main` veröffentlicht (siehe `PUBLISHING.md`).
- Secret-Schutz: `HttpHeaders.Builder.sensitive(name)` und `HttpRequest.Builder.setSensitiveHeader` maskieren
  Credential-Header beliebigen Namens in `toString()`; `RequestAuthentication` nutzt das für Bearer, Basic und
  benutzerdefinierte Header. `RawPayload.Builder.sensitiveHeader(name)` filtert providerspezifische
  Credential-Header aus Rohantworten.
- `SearchCapabilities.check` prüft per `require()` verlangte Features auch dann, wenn der Request keine zugehörige
  Option setzt.
- Nachtrag A.1 (Review-Nachlese zu Paket A): `SearchMetadata.getLocale()`/`getDevice()` (effektiv verwendetes Locale
  und Gerät, architecture.md §4, WP1 §4.8); `ExecutionInfo.getProviderReportedDuration()`/`getSourceUrl()`/
  `getProviderTimestamp()` (WP1 §4.8); `ConversationContext` mit 0-basiertem `getTurnIndex()` und
  `getAnswerElement()` (ADR-009, WP1 §4.11; `getTurnNumber()` entfällt); `toBuilder()` für `RawPayload`,
  `ExecutionInfo`, `ProviderUsage`, `RateLimitInfo`, `SearchWarning`, `ResultProvenance`, `AnalysisProvenance`,
  `EntityMention`, `Topic`, `Fact`, `ConversationalCapabilities`; `UrlQueryBuilder.paramIfPresent(String, String)`
  ersetzt die `Optional`-Überladung; `HttpHeaders` weist Namen, die kein RFC-7230-Token sind, und Werte mit CR/LF/NUL
  zurück; Thread-Sicherheit von `close()` in den Port-Javadocs (ADR-017 Regel 8); Tests für alle bisher ungetesteten
  Wertklassen. Angleichung an WP1: `ProviderUsage.getCredits()` (`Optional<BigDecimal>`, ersetzt `getCreditsUsed()`),
  `ResultProvenance.builder(provider, engine)` mit Pflicht-Engine, `ExecutionInfo` ohne `cached`.
- `serp4j-http-ahc` (Java 8, Paket T): `AhcHttpTransport` und `AhcHttpTransportOptions` auf async-http-client 2.16.
  `create(...)` besitzt seinen Client, `wrap(client, ...)` leiht ihn nur (ADR-017). Jede Serverantwort ist eine
  `HttpResponse`; Transportfehler werden nach `HttpTransportException.Kind` klassifiziert, ohne Bodies, Header-Werte
  oder unmaskierte URLs in Meldungen und Ursachen. Default-User-Agent `Serp4J/<version>`. Redirects übernimmt der
  Transport selbst und entfernt beim Wechsel des Origins alle sensitiven Header und Credential-Query-Parameter.
  Requests über dem Verbindungslimit warten, statt wie bei AHC mit `TooManyConnectionsException` zu scheitern.

### Fixed

- `RawPayload`: `hashCode` passt zu `equals` bei unterschiedlich geschriebenen Header-Namen; Header-Namen mit
  umgebendem Leerraum umgehen den Sensitiv-Filter nicht mehr.
- `CredentialProvider.chain(...)` kopiert das übergebene Array; `StaticCredentialProvider` ist threadsicher
  (`close()` gegen `resolve()`/`getKeys()`, `getKeys()` liefert einen Schnappschuss);
  `CancellationSource.cancel()` führt alle Callbacks aus, auch wenn eines einen `Error` wirft.
- CI: der Release-Workflow sichert das Central-Bündel vor dem Portal-Upload als Workflow-Artefakt; der Snapshot-Job
  läuft nur mit vollständigen Secrets inklusive Signing-Key, ein leerer Signing-Key gilt als nicht gesetzt.
