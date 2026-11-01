# serp4j-testkit

Test-Scope-Modul (Paket E, architecture.md §10): Provider, Orchestrierung und Hosts ohne bezahlte API-Aufrufe und
ohne Netzwerk testen. Java 8, hängt nur von `serp4j-core` und `serp4j-http-api` ab. JUnit Jupiter ist `compileOnly`:
wer die Contract-Tests nutzt, bringt JUnit 5 selbst mit; alles andere funktioniert ohne JUnit. Ebenso ist
`serp4j-mapping` nur `compileOnly`: es wird nur für `MappingDefinitionContractTest` gebraucht, und Module mit
Mapping-Definitionen hängen ohnehin davon ab.

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
| `testkit.contract` | abstrakte JUnit-5-Contract-Tests `SearchProviderContractTest`, `ConversationalSearchProviderContractTest`, `FixtureContractTest`, `MappingDefinitionContractTest` |
| `testkit.assertion` | `Serp4jAssertions` (`assertFailsWith`, `assertSingleProviderResponse`, `assertNoSecret`, …) ohne Drittbibliothek |
| `testkit` | `SharedInfrastructure`, `FakeSharedResource` – beobachtbare Infrastruktur für den Ownership-Vertrag (ADR-017) |

Alle Mocks bestehen ihre eigenen Contract-Tests (`src/test/.../contract`).

## Fixtures (E-1)

Ablage im Classpath unter `fixtures/<provider>/<name>`, daneben **verpflichtend** `<name>.fixture.properties` (Java-Properties: Kommentare nur auf eigenen `#`-Zeilen, nie hinter einem Wert):

```properties
# recorded | synthetic
provenance=recorded
source=DataForSEO playground, POST /v3/serp/google/organic/live/advanced
recordedAt=2026-10-12
# Pflicht für recorded
anonymized=true
# optional, Default 200
status=200
```

- Response-Mapper werden nur gegen **aufgezeichnete** Antworten getestet: `Fixtures.loadRecorded(...)` lehnt
  synthetische Daten ab. Synthetische Fixtures brauchen eine `description`, warum sie nicht aufgezeichnet sind.
- Mock-Ergebnisse zeigen ausschließlich auf `https://example.invalid/...` und heißen „Synthetic result …“.
- Jedes Modul mit Fixtures erbt einmal `FixtureContractTest`: Layout, Sidecar und Secret-Scan aller Fixtures.

Das Testkit enthält derzeit **keine** Provider-Fixtures. Die echten DataForSEO-Antworten (E-1) und die aus Schritt 0
(AskAI-Charakterisierung) kommen nach `src/main/resources/fixtures/<provider>/`, sobald sie anonymisiert vorliegen.

## Beispiel: Mapping-Definition (ADR-019)

Eine Klasse je Definition. Geprüft wird: Definition lädt und Engine baut; jeder Sektionspfad, Quellpfad und
Diskriminatorwert trifft in mindestens einer Fixture; ein Element unbekannten Typs überlebt als `GenericSerpFeature`;
die Ausgabe jeder Fixture (`MappingSnapshot`) entspricht ihrem Golden-File. Synthetische Fixtures werden abgelehnt (E-1).

```java
class DataForSeoWebMappingContractTest extends MappingDefinitionContractTest {
    protected MappingEngine engine() { return DataForSeoMapping.webEngine(); }   // mit Konvertern und Custom Mappern
    protected List<Fixture> fixtures() { return Fixtures.list("dataforseo"); }
    protected StructuredValue tree(Fixture fixture) { return JsonTrees.read(fixture.getText()); }
}
```

Golden-Files liegen unter `golden/<definition-id>/<provider>/<name>.snapshot.json` im Test-Classpath. Mit
`SERP4J_UPDATE_GOLDEN=1` werden fehlende oder abweichende Golden-Files nach `src/test/resources` geschrieben statt
zu scheitern; das Ergebnis wird wie Code reviewt.

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

- `FakeBrowserRuntime`/`BrowserRuntimeContractTest`: gehören in `serp4j-browser-testkit` (Paket F).
