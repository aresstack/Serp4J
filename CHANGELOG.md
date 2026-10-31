# Changelog

Alle nennenswerten Änderungen an Serp4J stehen in dieser Datei. Das Format folgt
[Keep a Changelog](https://keepachangelog.com/de/1.1.0/); die Versionierung folgt
[Semantic Versioning](https://semver.org/lang/de/) im Lockstep aller Module (ADR-015). Vor 1.0 können sich
öffentliche APIs noch ändern; `@Experimental` markiert die Teile, bei denen das besonders wahrscheinlich ist.

## [Unreleased]

### Added

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
- `serp4j-testkit` (Java 8, Test-Scope, Paket E): `MockSearchProvider`, `ScriptedSearchProvider` und
  `ScriptedConversationalProvider` mit Fehlerinjektion je Kategorie, Latenz, Deadline und Abbruch;
  `FakeHttpTransport` mit Stubs, Fixtures, Aufzeichnung, Verzögerung, Timeout und Sequenzen (pending → ready);
  Fixture-Ablage `fixtures/<provider>/` mit Pflicht-Sidecar (`recorded`/`synthetic`, E-1) und Secret-Scan;
  abstrakte Contract-Tests `SearchProviderContractTest`, `ConversationalSearchProviderContractTest`,
  `FixtureContractTest` (ADR-008, ADR-009, ADR-017). Die Contract-Vorlage aus `serp4j-core/src/test` ist dorthin
  umgezogen.
- CI: Build mit JDK 21, Tests auf JDK 21 und JDK 8, Javadoc ohne Fehler, Central-Portal-Bündel; Release-Workflow
  für Tags `vX.Y.Z`, der nur Commits aus `main` veröffentlicht (siehe `PUBLISHING.md`).
- Secret-Schutz: `HttpHeaders.Builder.sensitive(name)` und `HttpRequest.Builder.setSensitiveHeader` maskieren
  Credential-Header beliebigen Namens in `toString()`; `RequestAuthentication` nutzt das für Bearer, Basic und
  benutzerdefinierte Header. `RawPayload.Builder.sensitiveHeader(name)` filtert providerspezifische
  Credential-Header aus Rohantworten.
- `SearchCapabilities.check` prüft per `require()` verlangte Features auch dann, wenn der Request keine zugehörige
  Option setzt.
