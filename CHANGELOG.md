# Changelog

Alle nennenswerten Änderungen an Serp4J stehen in dieser Datei. Das Format folgt
[Keep a Changelog](https://keepachangelog.com/de/1.1.0/); die Versionierung folgt
[Semantic Versioning](https://semver.org/lang/de/) im Lockstep aller Module (ADR-015). Vor 1.0 können sich
öffentliche APIs noch ändern; `@Experimental` markiert die Teile, bei denen das besonders wahrscheinlich ist.

## [Unreleased]

### Added

- Google-`/goto`-Auflösung in verschachtelten URLs: `ResultUrlEnricher` löst neben der Treffer-URL auch Sitelinks
  (`SearchResultItem.siteLinks`), Quellen von Answer Box, Featured Snippet, Generated Answer (AI Overview) und
  People also ask sowie Website, Beschreibungsquelle, Profile und Fakten-Links des Knowledge Panels auf – im selben
  Lauf, Grenzen (4 parallel, 3 s, Deadline, Cancellation) für alle URLs einer Suche gemeinsam, jede URL einmal.
  Nachvollziehbarkeit über die neue Extension `serp4j:nested_url_resolutions` am besitzenden Treffer bzw. Feature:
  Array `{field, original_url, url_resolution, url?}`. Unaufgelöste verschachtelte URLs bleiben unverändert und zählen
  in die eine Warnung `url_resolution_failed`. `resolveGoogleGotoUrls(false)` schaltet alles ab. Generische Features
  (`GenericSerpFeature`) und Bilder/Thumbnails/Related-Query-URLs werden nicht angefasst.
- Mapping: neues Item-Ziel `siteLinks` (Objekte mit `link`/`url`, `title`, `description`/`snippet`; sammelt über alle
  angegebenen Quellen). DataForSEO mappt `links[*]`, SerpApi `sitelinks.inline/expanded/list[*]`. Die echten Fixtures
  enthalten verschachtelte `/goto`-URLs nur in Sitelinks (SerpApi `google-web-de.json`: 2, DataForSEO
  `google-organic-live-advanced-de.json`: 1); Golden-Files entsprechend aktualisiert.
- Core: `SiteLink.withUrl`, `SourceReference.withUrl`, `clear…()` an den Buildern von `SearchResultItem`,
  `GeneratedAnswerFeature`, `QuestionAnswerFeature` und `KnowledgePanelFeature`.
- SerpApi: Google-`/goto`-URLs werden nach dem JSON-Mapping wie bei DataForSEO aufgelöst – mit demselben
  `GoogleGotoUrlResolver` und demselben providerneutralen `ResultUrlEnricher` (keine zweite Implementierung).
  `SerpApiResponseMapper` bleibt netzwerkfrei. Option `SerpApiSearchOptions.resolveGoogleGotoUrls` (Standard `true`)
  und `urlResolveTimeout` (≤ 3 s), Factory-Properties `resolve-google-goto-urls` und `url-resolve-timeout`. Nur
  Google; Bing bleibt unverändert. Regressionstests auf der echten Fixture `google-web-de.json` (7 von 10 `/goto`).
- `serp4j-http-api`: `ResultUrlEnricher` – providerneutraler Postprozessor nach dem Mapping (höchstens 4 parallel,
  Timeout je Request und Such-Deadline, Cancellation, eine Warnung `url_resolution_failed` je Suche,
  `completeWith(target, mapped, context)` als komplette Provider-Anbindung). Neue Extension `serp4j:url_resolution`
  auf jedem versuchten Treffer: `resolved` oder der Fehlergrund (`not_a_redirect`, `invalid_location`, `timeout`,
  `network`, `unsupported_url`, `cancelled`). Treffer mit Fehlerstatus tragen weiter die unaufgelöste `/goto`-URL und
  dürfen nicht als verifizierte Quelle gelten (AskAI #41).
- DataForSEO: Google-Weiterleitungs-URLs (`https://www.google.com/goto?url=…`, auch ohne `www.`) werden nach dem
  Mapping automatisch aufgelöst. `DataForSeoResponseMapper` bleibt netzwerkfrei; `ResultUrlEnricher`
  löst jede verschiedene URL einer Antwort einmal per `GET` ohne Redirect-Folgen auf (höchstens 4 parallel,
  ≤ 3 s je Request und nie über die Such-Deadline hinaus) und übernimmt bei `301/302/303/307/308` mit absolutem
  `http(s)`-`Location` die Ziel-URL in `url` und deren Host in `domain`; die ursprüngliche URL steht in der Extension
  `serp4j:original_url`. Fehlschläge (403/429/5xx, Timeout, fehlendes/relatives `Location` …) lassen das Ergebnis
  unverändert und erzeugen genau eine Warnung `url_resolution_failed` mit Zählung je Grund. Die Zielseite wird nie
  abgerufen, an Google gehen keine Zugangsdaten, Cookies oder Referrer. Nur Google-Suchen; Bing bleibt unverändert.
  Option `resolveGoogleGotoUrls` (Standard `true`) im Builder und in der Factory, `urlResolveTimeout` im Builder.
  Folgeaufgabe: `/goto`-URLs innerhalb von SERP-Features (verschachtelte Treffer) werden noch nicht aufgelöst, nur
  `SearchData.getResults()`.
- `serp4j-http-api` hängt jetzt von `serp4j-core` ab (für `ResultUrlEnricher`).
- `serp4j-http-api`: Paket `com.aresstack.serp4j.http.resolve` mit `UrlResolver`, `UrlResolutionException` und
  `GoogleGotoUrlResolver` – providerunabhängig wiederverwendbar.
- `HttpRequest.Builder.followRedirects(boolean)` / `HttpRequest.isFollowRedirects()`: Redirects pro Request
  abschaltbar (Standard `true`); `AhcHttpTransport` liefert dann die erste `3xx`-Antwort unverändert aus,
  das Verhalten aller anderen Requests bleibt gleich. `FakeHttpTransport` folgt ohnehin nie.
- `SearchResultItem.withUrl(String)` und `SearchResultItem.Builder.url(String)`: Kopie mit neuer URL, alle übrigen
  Felder bleiben erhalten.

- `serp4j-provider-argos` (Java 8, eigenes Gradle-Subprojekt, keine Abhängigkeit auf AskAI): die Browser-Websuche von
  AskAI/Argos als SERP-Provider (Typ `argos`, `ProviderKind.BROWSER`) nach Ports & Adapters. Port `ArgosWebSearch`;
  Adapter `McpArgosWebSearch` ruft das Tool `web_search` des AskAI-Browser-Sidecars (`browser-mcp-server-java21`) per
  MCP Streamable HTTP über den `HttpTransport`-Port auf (JSON- und SSE-Antworten, Session-Neuaufbau nach Sidecar-Neustart,
  Token im Pfad wird in Meldungen geschwärzt); `ArgosSearchOutputParser` liest das Textformat des Tools;
  `ArgosSearchProvider` bildet Treffer, Engine (aus dem Host des liefernden Versuchs), Engine-Versuche und manuelle
  Challenges (`BLOCKED`, nie umgangen) auf das Domain-Modell ab; Plugin-Registrierung über `ServiceLoader`
  (`ArgosSearchProviderFactory`, Properties `endpoint` oder `port` + Credential `token`). Den Sidecar startet weiter der
  Host. Cancellation, `close()` und Deadline (auch beim Warten auf die gemeinsame MCP-Initialisierung) brechen den
  laufenden HTTP-Aufruf ab (ADR-017); `SearchProviderContractTest` läuft. Testdaten sind ausschließlich synthetisch und als solche gekennzeichnet (E-1).
- `serp4j-provider-brave` (Paket B, Java 8): `BraveSearchProvider` für `GET /res/v1/web/search`, Ziel `brave/web`,
  gegen die Brave-API-Referenz vom 2026-11-01. Request: `q` (≤ 600 Zeichen/75 Wörter), `count` ≤ 20, `offset` als
  Seitenindex 0–9 (Request-Offset muss ein Vielfaches des Limits sein), `country`/`search_lang`/`ui_lang` mit
  Instanz-Defaults, `safesearch`, `freshness` (`pd`/`pw`/`pm`/`py` oder Datumsbereich), `X-Loc-*` aus
  `GeoLocation`, Brave-spezifisches über `BraveRequestOptions` (`spellcheck`, `extra_snippets`, `result_filter`,
  `goggles`, `units`, `summary` …). Token nur im sensiblen Header `X-Subscription-Token`, je Suche aus dem
  `CredentialProvider` (`brave.api-key`). Antwort über die Mapping-Definition `brave-web-v1` (ADR-019): `web`,
  `news`, `videos`, `discussions` als Items, `infobox` als Knowledge Panel, `faq` und `locations` als Features;
  `query.altered` → `SpellCorrection`, `query.more_results_available` → `PageInfo.hasMore`, Rest in
  `Extensions.getUnmapped("brave")`. Fehler: `QUOTA_LIMITED` → `QUOTA_EXHAUSTED`, `RATE_LIMITED`/429 →
  `RATE_LIMITED` (mit `Retry-After`), 422 → `INVALID_REQUEST`, 401/403 → `AUTHENTICATION`, 5xx → `UNAVAILABLE`.
  `BraveSearchProviderFactory` per `ServiceLoader`. Tests laufen gegen **synthetische** Fixtures (als solche
  gekennzeichnet); `liveTest` zeichnet mit `-Pserp4j.recordFixtures=true` eine echte Antwort auf (E-1).
- `serp4j-provider-dataforseo` (Java 8, Paket D, gegen die DataForSEO-API-Referenz vom 2026-11-01):
  `DataForSeoSearchProvider` implementiert `SearchProvider` für Google und Bing organic über
  `POST /v3/serp/{engine}/organic/live/{regular|advanced}` (ein Task je Request, `depth` = offset + limit, max. 200).
  Zugangsdaten über `CredentialProvider` (`<id>.login`, `<id>.password`), nur als Basic-`Authorization`-Header.
  Statuscodes im Body werden ausgewertet: 20000 Erfolg, 40102 leeres Ergebnis, 40106 Teilergebnis
  (`complete=false`, Warnung `partial_results`), 40100 `AUTHENTICATION`, 40202/40209 `RATE_LIMITED`, 402xx
  `QUOTA_EXHAUSTED`, 5xxxx `UNAVAILABLE`/`TIMEOUT` (retryable). Mapping über die Definition
  `dataforseo-serp-organic` plus Custom Mapper (Knowledge Graph, People also ask, SERP-Bereich); `cost` →
  `ProviderUsage.cost` in USD, Provenienz je Ergebnis, `check_url`/`datetime`/`time` in `ExecutionInfo`, Rohantwort
  mit `RawPayloadPolicy.ALWAYS`, bei Teil- oder unvollständigen Ergebnissen auch mit `ON_ERROR`. `DataForSeoOptions` (location_code/-name, se_domain, os, Funktion, tag) und
  `DataForSeoSearchProviderFactory` (ServiceLoader). Fixtures sind **synthetisch** und als solche gekennzeichnet,
  bis eine aufgezeichnete Antwort vorliegt (E-1). Noch nicht enthalten: Standard-Task-Ablauf, HTML, Locations/Languages.
- `serp4j-provider-serpapi` (Java 8, fünfter Provider, gegen die SerpApi-Dokumentation vom 2026-11-02):
  `SerpApiSearchProvider` für `GET https://serpapi.com/search.json` mit `engine=google` (Standard) und
  `engine=bing`. Google: `q`, `gl`, `hl`, `location`, `google_domain`, `start`, `safe`, `tbs` (kein `num` – Google wertet ihn seit 09/2025 nicht mehr aus; andere Limits als 10 ergeben eine Warnung bzw. `UNSUPPORTED`)
  (`qdr:*` bzw. `cdr:1,cd_min,cd_max`); Bing: `cc`, `mkt` (Sprache ohne Land → Warnung bzw. `UNSUPPORTED`), `count` ≤ 50 (sonst gekappt mit Warnung bzw.
  `UNSUPPORTED`), `first` = offset + 1, `safeSearch`, `filters` (`ez1`–`ez3`, sonst `ez5_<von>_<bis>`). Der Key
  kommt aus dem `CredentialProvider` (`<id>.api-key`) und geht – weil SerpApi ihn nur so annimmt – als URL-Parameter
  `api_key` über `RequestAuthentication.queryParameter`: maskiert in `getRedactedUrl()`/`toString()`/
  `ExecutionInfo.sourceUrl`, in Fehlermeldungen per Muster `api_key=***`, Transportfehler ohne angehängte Ursache;
  `SecretValue` wird geschlossen, der Key nur als `char[]` gehalten und danach gelöscht. `SerpApiRequestOptions`
  (`no_cache`, `device`, `location`, `google_domain`, weitere Parameter ohne die reservierten Namen); falscher
  Optionstyp → `INVALID_REQUEST` vor Credential-Abruf und HTTP-Aufruf. Mapping-Definitionen `serpapi-google` und
  `serpapi-bing` v1 (organic, ads, top_stories, knowledge_graph, answer_box, related_questions, related_searches;
  `search_metadata.id` → `providerRequestId`, `total_results` → `PageInfo.totalResultsEstimate`, `serpapi_pagination.next` → `hasMore`, Anzeigen-URL mit Fallback `tracking_link`). Fehler: 401/403/„Invalid API
  key“ → `AUTHENTICATION`, „run out of searches“ → `QUOTA_EXHAUSTED`, sonst 429 → `RATE_LIMITED`, 400 →
  `INVALID_REQUEST`, 5xx und `status=Error` → `UNAVAILABLE`; „hasn't returned any results“ ist eine leere
  Erfolgsantwort. SerpApi-Cache bleibt standardmäßig an (gecachte Antworten kosten kein Kontingent).
  `SerpApiSearchProviderFactory` per `ServiceLoader`. Keine SerpApi-Client-Bibliothek als Abhängigkeit
  (`serpapi-java` braucht Java 21, `serpapi4j` Java 16). Fixtures sind **synthetisch** und gekennzeichnet; `liveTest`
  (`SERP4J_SERPAPI_API_KEY`) zeichnet Google- und Bing-Antworten mit `-Pserp4j.recordFixtures=true` auf (E-1).
- `serp4j-provider-brightdata` (Java 8, Paket C): `BrightDataSearchProvider` für die Bright Data SERP API,
  synchroner Weg `POST https://api.brightdata.com/request` mit `{zone, url, format: "json", data_format: "parsed"}`
  (von Angelo real verifiziert). Die Google-URL entsteht aus dem neutralen `SearchRequest` (`q`, `start`, `hl`, `gl`,
  `uule`, `safe`, `tbs`, `brd_mobile`; kein `num`); Bright-Data-Spezifisches über `BrightDataOptions` (Zone,
  Proxy-Land `country` ≠ `gl`, `uule`, `brd_mobile`, `brd_browser`, `brd_json`, weitere URL-Parameter). Zweistufiges
  Parsen (Wrapper `status_code`/`headers`/`body`, `body` ist ein JSON-String); Ziel-Status ≠ 2xx wird `BLOCKED`
  bzw. `UNAVAILABLE`. Mapping-Definition `brightdata-google-web` v1 (organic, videos, short_videos,
  knowledge, overview, people_also_ask, related): `rank` → `rankInGroup`, `global_rank` → `rankAbsolute`; Rest
  (u. a. `navigation`, Data-URIs) bleibt in `unmapped`. API-Key nur als sensibler `Authorization`-Header.
  `BrightDataSearchProviderFactory` per `ServiceLoader` (Properties `zone`, `apiUrl`, `googleSearchUrl`,
  `requestTimeout`). Erste echte Fixture `fixtures/brightdata/google-web-pizza.json` (recorded 2026-07-28) mit
  Golden-File. Async-API (`/serp/req`) folgt separat.

- `serp4j-testkit`: `MappingDefinitionContractTest` (ADR-019) – Definition lädt, jeder Pfad trifft in einer Fixture,
  „unbekannter Typ überlebt“, Golden-Output je Fixture (`SERP4J_UPDATE_GOLDEN=1`); nur aufgezeichnete Fixtures (E-1).
  `serp4j-mapping` ist `compileOnly`.
- `serp4j-mapping` (Java 8, nur `serp4j-core`): Response-Mapping in zwei Ebenen (ADR-019) – deklarative, versionierte
  Mapping-Definitionen mit strikter Validierung (`MappingDefinitions`), kleine Pfadsprache (`TreePath`), Sektionen
  `items`/`features`/`elements` mit Diskriminator, `fieldsByType`, Positionsmodi, Pflichtfeldern, Defaults und
  Konvertern (`ValueConverters`); Custom Mapper (`ItemMapper`, `FeatureMapper`, `MetadataMapper`) in einer
  `CustomMapperRegistry`, die an genau eine Definition gebunden ist; `MappingEngine` erhält nicht konsumierte Daten
  blattgenau in `Extensions.unmapped`, unbekannte Typen als `GenericSerpFeature`, und wirft nie wegen einer Antwort;
  Prüfbausteine `MappingDefinitionVerifier` und `MappingSnapshot` für Fixture-Tests. Formatreferenz:
  `docs/mapping/mapping-definition-format.md`.
- `serp4j-json` (Teil J1): `JsonTrees` (JSON-Text ↔ `StructuredValue`, exakte Zahlen, strikt), einzige Stelle mit Gson.
- `serp4j-json` (Teil J2, ADR-013): `Serp4jJson` liest und schreibt `SearchResponse`, `SearchConversation` und
  `SessionHandle` als versioniertes JSON (`formatVersion` 1, `type`-Diskriminator). Fehlende, nicht ganzzahlige oder
  unbekannte Versionen werden mit `UnsupportedFormatVersionException` abgelehnt; unbekannte Felder werden toleriert;
  `RawPayload` wird nur mit `includeRawPayload(true)` geschrieben; ungültige Dokumente melden
  `Serp4jJsonException` mit JSON-Pfad.
- Paket F, Browser (ADR-010, ADR-011, ADR-017, WP3):
  - `serp4j-browser-api` (Java 8, keine Abhängigkeiten, kennt den Core nicht): `BrowserRuntime` mit `getInfo()`,
    `probe()` und `openSession(...)`; `BrowserSession` mit `navigate`, `await` (`WaitCondition`), `evaluate`
    (`PageScript`, Ergebnis als `BrowserValue`), `click`/`type`/`press`, `snapshot` und `network()`
    (`NetworkCapture`, ohne Cookie- und Authorization-Header); `UrlPolicy` als Host-Allowlist vor und nach jeder
    Navigation; Fehlertypen unter `BrowserException`.
  - `serp4j-browser-testkit`: `FakeBrowserRuntime` für Provider-Tests ohne Browser und die TCK
    `BrowserRuntimeContractTest` (32 Fälle: Isolation, Kapazität, Timeouts, Absturz, Hard-Kill, Prozessaufräumen, Policy, Profile, Hindernisse).
  - `serp4j-browser-playwright` (Java 8): `PlaywrightBrowserRuntime` mit einem Owner-Thread je Browser,
    Kontext je Session, harter Kapazitätsgrenze, Lebensdauer-/Leerlauf-/Navigationsbudget, `RemotePlaywrightRuntime`
    für einen Playwright-Server und der Prüfung „genau ein Treiber“ über `PlaywrightDriverModule`.
  - `ProfileMode` (`EPHEMERAL` als Default, `PERSISTENT_EXCLUSIVE(profileKey)` mit exklusiver Dateisperre),
    `ObstaclePolicy` (`FAIL_FAST`, `WAIT_FOR_HUMAN(timeout)` nur im sichtbaren Modus) mit
    `BrowserSession.resolveObstacle(...)` und `PageObstacleException`; Hard-Kill der Browser- und Treiberprozesse
    nach überschrittenem Close-Timeout (WP3 §6.3, R12).
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
