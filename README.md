# Serp4J

Provider-unabhängige Java-Bibliothek für Web-Suchergebnisse (SERP): ein gemeinsames Domänenmodell, Ports für
Suchanbieter und – in späteren Arbeitspaketen – Adapter für Cloud-APIs (Brave, Bright Data, DataForSEO) sowie für
die Suche im Browser.

**Status:** `0.1.0-SNAPSHOT`, in Entwicklung. Umgesetzt ist Paket A (Build-Gerüst, Core-Domain, Ports,
HTTP-Port). Die verbindliche Architektur steht in [docs/architecture](docs/architecture/architecture.md), die
Entscheidungen in den [ADRs](docs/architecture/adr), die Reihenfolge der Arbeitspakete im
[Implementierungsplan](docs/architecture/implementation-plan.md).

## Module

| Modul | Java | Inhalt | Abhängigkeiten |
|---|---|---|---|
| `serp4j-core` | 8 | Domänenmodell (`SearchRequest`, `SearchResponse` = `SearchData` + `SearchMetadata` + `SearchAnalysis`, `SerpFeature`-Hierarchie mit Visitor, `Extensions`, `RawPayload`), Ports (`SearchProvider`, `ConversationalSearchProvider`, `SearchAnalyzer`, `CredentialProvider`, `SearchProviderFactory`), Capabilities, Fehlermodell | keine |
| `serp4j-http-api` | 8 | `HttpTransport`-Port, `HttpRequest`/`HttpResponse`/`HttpHeaders`, `RequestAuthentication`, `UrlQueryBuilder` | keine |
| `serp4j-mapping` | 8 | Response-Mapping: deklarative Mapping-Definitionen, `MappingEngine`, Custom Mapper je Definition, Prüfbausteine für Fixture-Tests ([Format](docs/mapping/mapping-definition-format.md)) | `serp4j-core` |
| `serp4j-json` | 8 | `JsonTrees`: JSON-Text ↔ `StructuredValue` (einzige Stelle mit Gson) | `serp4j-core`, Gson |
| `serp4j-testkit` | 8 | Test-Scope: `MockSearchProvider`, `ScriptedSearchProvider`, `ScriptedConversationalProvider`, `FakeHttpTransport`, Fixtures mit Herkunftskennzeichnung und Secret-Scan, abstrakte Contract-Tests ([README](serp4j-testkit/README.md)) | core, http-api; JUnit 5 `compileOnly` |
| `serp4j-bom` | – | Bill of Materials über alle publizierten Module (Lockstep-Version) | – |

Die weiteren Module (`serp4j-orchestration`, `serp4j-http-*`, `serp4j-provider-*`,
`serp4j-browser-*`) sind in `settings.gradle.kts` bereits vorgesehen und kommen mit den Paketen
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

## Verwenden

Die Artefakte erscheinen unter `com.aresstack:serp4j-*` auf Maven Central (siehe [PUBLISHING.md](PUBLISHING.md));
vor dem ersten Release stehen sie nur lokal über `publishToMavenLocal` bereit.

```kotlin
dependencies {
    implementation(platform("com.aresstack:serp4j-bom:0.1.0"))
    implementation("com.aresstack:serp4j-core")
}
```

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
