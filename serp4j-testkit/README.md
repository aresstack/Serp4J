# serp4j-testkit

Test-Scope-Modul (Paket E, architecture.md §10): Provider, Orchestrierung und Hosts ohne bezahlte API-Aufrufe und
ohne Netzwerk testen. Java 8, hängt nur von `serp4j-core` und `serp4j-http-api` ab. JUnit Jupiter ist `compileOnly`:
wer die Contract-Tests nutzt, bringt JUnit 5 selbst mit; alles andere funktioniert ohne JUnit.

```kotlin
dependencies {
    testImplementation(project(":serp4j-testkit"))   // extern: com.aresstack:serp4j-testkit
}
```

## Bausteine

| Paket | Inhalt |
|---|---|
| `testkit.provider` | `MockSearchProvider` (eine Antwortregel, zur Laufzeit austauschbar), `ScriptedSearchProvider` (feste Folge von `Outcome`s), `ScriptedConversationalProvider` + `ScriptedSearchSession` (vollständiger Sitzungsvertrag aus ADR-009), `Outcome` (Ergebnisse, Fehler je `SearchErrorCategory`, unveränderte Antwort, `hang()`, Latenz), `SyntheticResults` |
| `testkit.http` | `FakeHttpTransport` mit `RequestMatcher` und `FakeHttpStep`: Antworten jedes Status, Fixtures, Transportfehler, Verzögerung, Request-Timeout, `hang()`, Sequenzen; Aufzeichnung aller Requests; nicht passende Requests schlagen laut fehl |
| `testkit.fixture` | `Fixtures`, `Fixture`, `FixtureCatalog`, `FixtureSecretScanner` |
| `testkit.contract` | abstrakte JUnit-5-Contract-Tests `SearchProviderContractTest`, `ConversationalSearchProviderContractTest`, `FixtureContractTest` |
| `testkit.assertion` | `Serp4jAssertions` (`assertFailsWith`, `assertSingleProviderResponse`, `assertNoSecret`, …) ohne Drittbibliothek |
| `testkit` | `SharedInfrastructure`, `FakeSharedResource` – beobachtbare Infrastruktur für den Ownership-Vertrag (ADR-017) |

Alle Mocks bestehen ihre eigenen Contract-Tests (`src/test/.../contract`).

## Fixtures (E-1)

Ablage im Classpath unter `fixtures/<provider>/<name>`, daneben **verpflichtend** `<name>.fixture.properties`:

```properties
provenance=recorded          # recorded | synthetic
source=DataForSEO playground, POST /v3/serp/google/organic/live/advanced
recordedAt=2026-10-12
anonymized=true              # Pflicht für recorded
status=200                   # optional, Default 200
```

- Response-Mapper werden nur gegen **aufgezeichnete** Antworten getestet: `Fixtures.loadRecorded(...)` lehnt
  synthetische Daten ab. Synthetische Fixtures brauchen eine `description`, warum sie nicht aufgezeichnet sind.
- Mock-Ergebnisse zeigen ausschließlich auf `https://example.invalid/...` und heißen „Synthetic result …“.
- Jedes Modul mit Fixtures erbt einmal `FixtureContractTest`: Layout, Sidecar und Secret-Scan aller Fixtures.

Das Testkit enthält derzeit **keine** Provider-Fixtures. Die echten DataForSEO-Antworten (E-1) und die aus Schritt 0
(AskAI-Charakterisierung) kommen nach `src/main/resources/fixtures/<provider>/`, sobald sie anonymisiert vorliegen.

## Beispiel: Adapter-Contract

```java
class DataForSeoContractTest extends SearchProviderContractTest<FakeHttpTransport> {
    protected FakeHttpTransport createInfrastructure() {
        return FakeHttpTransport.create()
                .on(RequestMatcher.post("https://api.dataforseo.com/v3/serp/google/organic/live/advanced"))
                .reply(FakeHttpStep.fixture(Fixtures.loadRecorded("dataforseo", "organic-advanced.json")));
    }
    protected SearchProvider createProvider(ProviderId id, FakeHttpTransport http) {
        return DataForSeoSearchProvider.builder(id).transport(http).credentials(credentials).build();
    }
    protected SearchRequest supportedRequest() {
        return SearchRequest.builder("serp4j").engine(SearchEngineId.GOOGLE).build();
    }
    protected Optional<String> secret() {
        return Optional.of("contract-password");
    }
}
```

## Noch nicht enthalten

- `MappingDefinitionContractTest` (ADR-019): folgt als kleiner PR auf der JUnit-freien Prüf-API aus
  `serp4j-mapping` (Paket MP), sobald beide Pakete gemergt sind.
- `FakeBrowserRuntime`/`BrowserRuntimeContractTest`: gehören in `serp4j-browser-testkit` (Paket F).
