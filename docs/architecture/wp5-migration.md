# WP5 – Migration, Tests & Qualität

**Status:** Entwurf zur Integrationsphase · **Stand:** 2026-10-31 · **Quelle:** `aresstack/askai@523699e`, Modul `:web-search-providers`

> Architekturphase. Dieses Dokument beschreibt Bestand und Migrationsweg. Es enthält **keine** Implementierung und
> setzt keine Entscheidung aus WP1–WP4 voraus. Wo WP5 eine Annahme über das Zielmodell trifft, ist sie als
> *Annahme* markiert und an den Integrations-Thread adressiert.

---

## 1. Zusammenfassung

Das AskAI-Modul `:web-search-providers` ist eine kleine, sauber geschnittene Java-8-Bibliothek
(72 Produktionsklassen, ~6 100 LOC, 13 Testklassen mit ~970 LOC) mit drei Cloud-Adaptern (Brave, Bright Data, DataForSEO),
asynchronem HTTP über AsyncHttpClient 2.16, Gson-basierten Config-DTOs und einer AES-GCM-Secret-Verwaltung.
Es kennt AskAI fachlich nicht, ist aber in drei Punkten **AskAI-förmig**:

1. **Das Ergebnismodell ist zu schmal.** `WebSearchHit` kennt nur `rank/title/url/snippet`. Alles andere
   (Knowledge Graph, AI Overview, People Also Ask, News, Paid, Related Searches, Zeitstempel, Domain, absolute
   Ränge, Kosten, Laufzeit) wird verworfen oder steckt nur noch im unstrukturierten `rawResponse`-String.
2. **Konfiguration und Secrets sind verheiratet.** Die Provider-Config-DTOs tragen `EncryptedSecret`-Felder und
   sind veränderliche Gson-POJOs mit AskAI-spezifischem Ablageort (`~/agents/research/providers`).
3. **Transport ist fest verdrahtet.** Jeder Provider erzeugt einen eigenen `AsyncHttpClient` (Netty).
   Es gibt keinen Transport-Port, Tests brauchen einen echten lokalen HTTP-Server.

**Empfehlung:** Die Request-Mapper, Engine-/Parameter-Enums und Validatoren werden nahezu unverändert
übernommen (das ist das eigentliche API-Wissen). Response-Mapper, Ergebnismodell, Registry, Composite,
Transport und Secret-Anbindung werden **refaktoriert bzw. neu entwickelt**, damit Serp4J eine eigenständige
Plattform wird und keine ausgelagerte AskAI-Suche. Persistenz (Gson-Dateien, AES-Schlüsseldatei,
Settings-Draft, Playground) bleibt **in AskAI** bzw. wandert in ein optionales Modul.

Der Migrationsweg ist so geschnitten, dass AskAI zu keinem Zeitpunkt Funktion verliert:
AskAI behält `:web-search-providers`, bis Serp4J-Adapter dieselben Charakterisierungstests bestehen, und
schaltet dann über eine einzige Bridge-Klasse um (Abschnitt 6).

---

## 2. Bestandsaufnahme

### 2.1 Modulgrenzen und Abhängigkeiten

```text
:web-search-providers   (java-library, Java 8)
  api            com.google.code.gson:gson:2.13.1        ← leakt Gson in alle Konsumenten
  implementation org.asynchttpclient:async-http-client:2.16.0  (+ Netty, reactive-streams)
  test           junit:junit:4.13.2, com.sun.net.httpserver (JDK)
```

Konsumenten im AskAI-Repo:

| Konsument | Nutzt | Zweck |
|---|---|---|
| `:research-agent-runtime` (`search/provider/async/*`) | `WebSearchProvidersModule.openResilient`, `WebSearchProvider`, `WebSearchRequest/Result/Hit`, `HttpResponseException`, `WebSearchException`, `ProviderConfigurationPaths` | Produktive Bridge vom asynchronen Modul auf den synchronen Runtime-Port `SearchProvider` inkl. Hot-Reload mit Generationen-Leasing |
| `:research-agent-ui-plugin` (`research/host/*`) | `ProviderConfigurationService`, `GsonProviderConfigurationStore`, `SecretValueService`, `AesGcmSecretCipher`, `FileSecretKeyProvider`, `SearchProviderFactory`, `AsyncHttpClientFactory`, `DataForSeoSettingsDraft`, `DataForSeoPlayground`, DataForSEO-Enums | Swing-Einstellungsdialog, Provider-Karten, DataForSEO-Playground (Live-Probeabfrage) |
| `:agent-plugin-host` (Test) | Klassen im Plugin-Classpath | PF4J-Ladetest |

Wichtig für die Migration: Die **Runtime** hat ein eigenes, breiteres Suchmodell
(`SearchProviderId` mit 13 IDs, `SearchEngine`, `SearchHit` mit `domain/absoluteRank/publishedAt`,
typisierte Exceptions für Auth/RateLimit/Temporary/Response/Configuration). Davon sind nur die drei
migrierten Provider produktiv; zehn weitere IDs (SerpApi, Tavily, Exa, SearXNG, Google CSE, Bing,
Yandex Cloud, Baidu Qianfan, Azure OpenAI, AWS AgentCore) sind leere Platzhalter (je 8 Zeilen).

### 2.2 Komponenteninventar

Bewertung: **Ü** = übernehmen (Code bleibt im Kern gleich, nur Paket/Typen angepasst) ·
**R** = refaktorieren (Logik wird übernommen, Schnitt/Vertrag ändert sich) ·
**N** = neu entwickeln (Konzept wird ersetzt) · **A** = bleibt in AskAI (kein Serp4J-Bestandteil).

#### API / Domain (`search.api`)

| Komponente | Inhalt heute | Befund | Bewertung | Ziel in Serp4J (Annahme, Abgleich mit WP1/WP2) |
|---|---|---|---|---|
| `WebSearchProvider` | `getProviderId()`, `supports(SearchEngine)`, `CompletableFuture<WebSearchResult> search(WebSearchRequest)`, `close()` | Schlanker, guter asynchroner Port. Fehlt: Capabilities, Engine-Auswahl pro Request, Session-Fähigkeit | **R** | `SearchProvider` (Port) + `SearchCapabilities`; `supports(..)` wird aus Capabilities abgeleitet |
| `WebSearchRequest` | `query`, `countryCode`, `languageCode`, `maximumResults` (1–200), `offset`; Builder, unveränderlich | Gutes Muster. Fehlt: gewünschte `SearchEngine`, Gerät, SafeSearch, Zeitraum, Vertikale, providerspezifische Overrides | **R** | `SearchRequest` (Builder bleibt) + optionaler Erweiterungsmechanismus für Provider-Optionen |
| `WebSearchHit` | `providerId`, `searchEngine`, `rank`, `title`, `url`, `snippet` | Zu schmal; `title` ist Pflicht (scheitert bei SERP-Elementen ohne Titel) | **N** | `SearchResultItem` (WP1), u. a. mit `absoluteRank`, `domain`, `displayUrl`, `publishedAt`, `extraSnippets`, Erweiterungsattributen |
| `WebSearchResult` | `providerId`, `searchEngine`, `hits`, `rawResponse` (String) | Rohantwort erhalten ist richtig, aber nur als String und im Composite verworfen | **N** | `SearchResponse` = `SearchData` + `SearchMetadata` (+ optional `SearchAnalysis`); Rohantwort als `RawResponse` in den Metadaten (Abschnitt 4) |
| `SearchProviderId` | Enum `BRAVE, BRIGHT_DATA, DATA_FOR_SEO, COMPOSITE` | Enum ist geschlossen: Drittanbieter-Provider unmöglich, `COMPOSITE` ist kein Provider | **N** | Offener Wert-Typ (`ProviderId` mit `of(String)`) + Konstanten für mitgelieferte Provider |
| `SearchEngine` | Enum mit 10 Werten inkl. `MULTI` | `MULTI` ist ein Artefakt des Composite. Enum ist ebenfalls geschlossen; Google AI Mode fehlt | **R** | Offener Wert-Typ `SearchEngine` (Konstanten `GOOGLE`, `BING`, …); `MULTI` entfällt, Composite liefert Engine je Item |
| `WebSearchException` | Unchecked, eine Klasse | Zu grob; die Runtime musste HTTP-Status selbst in Auth/RateLimit/Temporary übersetzen (`AsyncModuleSearchProvider.translate`) | **N** | Exception-Hierarchie im Core: `SearchAuthenticationException`, `SearchRateLimitException`, `SearchTemporaryException`, `SearchResponseException`, `SearchConfigurationException` – Vorlage ist die AskAI-Runtime, nicht das Modul |

#### Application (`search.application`)

| Komponente | Inhalt heute | Befund | Bewertung | Ziel |
|---|---|---|---|---|
| `WebSearchProviderRegistry` | `EnumMap<SearchProviderId, WebSearchProvider>`, `require(id)`, `getAll()`, schließt alle Provider | Nur eine Instanz je Provider-ID möglich (z. B. kein „Bright Data Google“ + „Bright Data Bing“ parallel). Lifecycle-Eigentum implizit | **R** | `SearchProviderRegistry` (WP2) mit Instanz-Schlüssel statt Provider-Typ; Lookup nach Capability/Engine |
| `CompositeWebSearchProvider` | Parallelausführung, `toleratePartialFailure`, URL-Dedupe (scheme/host/port/path), `owning(..)` | Gute Basis. Verluste: Rohantworten (`""`), Herkunft verworfener Duplikate, Fehlerinfo der ausgefallenen Provider (`exceptionally → null`), Reihenfolge nur nach Provider-Reihenfolge; Query-String wird beim Dedupe ignoriert (kann echte Unterschiede verschlucken) | **R** | Composite in `serp4j-core`/Application: liefert alle Teilantworten in den Metadaten, Fehler pro Provider, mergt Herkunft je Item; Dedupe- und Merge-Strategie als austauschbare Strategy |
| `SearchProviderFactory` | Validiert Config, baut AHC-Client + Auth + Mapper je Provider | Verdrahtung ist AskAI-spezifisch (Gson, `SecretValueService`) | **R** | Je Provider-Modul eine Factory/Builder (`BraveSearchProvider.builder()...`) plus optional `ServiceLoader`-basiertes `SearchProviderFactory`-SPI |
| `WebSearchProvidersModule` | Öffnet Dateien, baut Registry, strikt/resilient, `reloadProviders()` | Komposition aus Datei-Config, Secrets und Providern – das ist Host-Logik | **A** | Bleibt in AskAI (wird zu einer Serp4J-Bootstrap-Klasse in AskAI). Das Resilient-Verhalten wird als Registry-Option dokumentiert |

#### Provider-Adapter

| Komponente | Inhalt heute | Befund | Bewertung | Ziel |
|---|---|---|---|---|
| `BraveSearchProvider` | GET mit Headern, mappt auf `WebSearchResult` | Trivial | **R** | `serp4j-provider-brave`, Transport über Port |
| `BraveSearchRequestMapper` | Vollständiges Parameter-Mapping (q, country, search_lang, ui_lang, count≤20, offset als Seitenindex ≤9, safesearch, freshness, result_filter, goggles, extra_snippets, operators, units, rich callback, fetch metadata) + Standortheader `X-Loc-*`, `Api-Version` | Fachlich wertvoll und getestet | **Ü** | unverändert, Eingabetyp `SearchRequest` |
| `BraveSearchResponseMapper` | Nur `web.results[]` → Hits; `description`, Fallback `extra_snippets` | Ignoriert `news`, `videos`, `discussions`, `faq`, `infobox`, `locations`, `mixed`, `query` (u. a. `altered`/Rechtschreibkorrektur), je Ergebnis `age`, `page_age`, `language`, `profile`, `meta_url`, `thumbnail`, `deep_results` | **N** | Neuer Mapper auf `SearchData` (Items + `SerpFeature`s) |
| Brave-Config (`BraveSearchConfiguration`, `…Freshness…`, `…Location…`, Enums `BraveResultType/SafeSearch/Units/FreshnessMode`) | Mutable Gson-POJO, `EncryptedSecret apiKey`, `@SerializedName` an Enums | Feldumfang vollständig und richtig | **R** | Unveränderliches `BraveSearchOptions` (Builder); Enums **Ü** ohne Gson-Annotation; Credential über Port |
| `BraveSearchConfigurationValidator` | Range-/Pflichtprüfungen | | **Ü** | in Builder/`build()` |
| `BrightDataSearchProvider` | Sync (`/request`) und serverseitig async (`/serp/req` + Polling `/serp/get_result`, eigener `ScheduledExecutorService` je Provider) | Polling-Logik korrekt, aber: ein Thread je Provider-Instanz, „pending“ wird über String-Vergleich erkannt, kein Test für den Async-Pfad | **R** | Polling in einen wiederverwendbaren `PollingExecutor` (geteilter Scheduler, injizierbar); Async-Pfad mit Testkit abdecken |
| `BrightDataRequestMapper` | Sync-Body (zone, url, format, method, country, data_format), Async-Body (q, gl, hl, start, num, Zusatzparameter, `brd_json`) | Übernehmbar. **Prüfpunkt:** Im Sync-Pfad wird `brd_json=1` nicht an die Ziel-URL gehängt; der Response-Mapper erwartet aber geparstes JSON mit `organic[]` auf Wurzelebene. Ohne `brd_json=1` in `additionalSearchParameters` liefert Bright Data sehr wahrscheinlich HTML bzw. ein `{status_code, headers, body}`-Envelope → 0 Treffer. Gegen die aktuelle API-Doku verifizieren | **Ü** (+Fix) | `serp4j-provider-brightdata` |
| `BrightDataTargetUrlFactory` | Ziel-URL je Engine (Google, Bing, DuckDuckGo, Yandex) inkl. SafeSearch-Übersetzung, Paging | Wertvolles Engine-Wissen, getestet (nur Google) | **Ü** | unverändert; Tests für Bing/DDG/Yandex ergänzen |
| `BrightDataSearchResponseMapper` | Nur `organic[]` (`link`/`url`, `title`, `description`/`snippet`, `rank`) | Ignoriert u. a. `general` (Query, Sprache, Ergebniszahl, Suchzeit), `knowledge`, `people_also_ask`, `related`, `top_stories`, `images`, `videos`, `ai_overview`, `pagination`, `global_rank`, `extensions` | **N** | Neuer Mapper auf `SearchData`/`SearchMetadata` |
| Bright-Data-Config (`BrightDataSearchConfiguration`, Enums `ExecutionMode`, `SearchEngine`, `SafeSearch`, `ResponseFormat`, `RequestMethod`, `DataFormat`) | Mutable POJO, `EncryptedSecret apiKey` | `DataFormat.MARKDOWN/SCREENSHOT` liefern kein JSON – mit dem aktuellen Mapper nicht nutzbar | **R** | `BrightDataSearchOptions`; `DataFormat` nur zulassen, wenn ein passender Mapper existiert (Capability) |
| `BrightDataSearchConfigurationValidator` | | | **Ü** | |
| `DataForSeoSearchProvider` | POST Live-Organic, Engine aus Config | | **R** | `serp4j-provider-dataforseo` |
| `DataForSeoRequestMapper` | Vollständige Task-Abbildung (keyword, location_*, language_*, depth, device, os, AI Overview async, tag, stop_crawl_on_match, max_crawl_pages, search_param, remove_from_url, PAA-Klicktiefe, group_organic_results, calculate_rectangles, Bildschirm, url, se_domain, target-Filter), Endpoint `/v3/serp/{engine}/organic/live/{format}` | Umfangreich und getestet. Auffälligkeit: `target_search_mode` wird nur gesetzt, wenn `stopCrawlOnMatch` nicht leer ist (vermutlich falsche Bedingung – gehört zu `target`) | **Ü** (+Prüfung) | unverändert |
| `DataForSeoSearchResponseMapper` | Status-Validierung (20000) auf Envelope und Task, nur `items[type=organic]`, Rang `rank_group` → `rank_absolute` → Fallback | Statusprüfung **Ü**. Ignoriert alle anderen Item-Typen (`featured_snippet`, `knowledge_graph`, `people_also_ask`, `ai_overview`, `local_pack`, `top_stories`, `related_searches`, `video`, `images`, `paid`, …), Item-Felder (`domain`, `breadcrumb`, `timestamp`, `highlighted`, `links`, `rating`, `is_featured_snippet`) und Result-Metadaten (`check_url`, `datetime`, `item_types`, `se_results_count`, `spell`, Task-`cost`/`time`) | **N** (Statuslogik **Ü**) | Neuer Mapper; DataForSEO ist die reichste Quelle für `SerpFeature`s und dient als Referenz für WP1 |
| `DataForSeoPlayground` | Request-Vorschau, Live-Lauf, liest `status/cost/time` aus der Rohantwort | Nur nötig, weil das Ergebnismodell Kosten/Laufzeit nicht trägt. UI-nah | **A** | Entfällt mittelfristig: `SearchMetadata.providerUsage` (Kosten, Laufzeit, Status) + eine generische `RequestPreview`-Fähigkeit im Provider-Modul |
| DataForSEO-Config (`DataForSeoSearchConfiguration`, 9 Enums, `DataForSeoStopCrawlTarget`) | Mutable POJO mit `username` + `EncryptedSecret password` | Vollständig | **R** | `DataForSeoSearchOptions`; Enums **Ü** |
| `DataForSeoSearchConfigurationValidator` | Umfangreiche Prüfungen inkl. Exklusivität Location/Language | | **Ü** | |

#### Transport (`search.http`)

| Komponente | Inhalt heute | Befund | Bewertung | Ziel |
|---|---|---|---|---|
| `AsyncJsonHttpClient` | GET/POST-JSON, Header, Timeout, Status ≠ 2xx → `HttpResponseException`, Auth-Strategy | Saubere Fassade, aber an AHC-`BoundRequestBuilder` gebunden; ein AHC-Client (eigener Netty-Eventloop) **je Provider** | **N** (Logik **Ü**) | Port `HttpTransport` im Core (`CompletableFuture<HttpResponse> execute(HttpRequest)`); Adapter `serp4j-transport-ahc` (heutige Logik) und optional ein JDK-`HttpURLConnection`-Adapter ohne Zusatz-Dependency |
| `AsyncHttpClientFactory` | Baut AHC aus `HttpTransportConfiguration` | | **Ü** | in `serp4j-transport-ahc`; geteilter Client für alle Provider möglich |
| `HttpTransportConfiguration` | Timeouts, Pools, Redirects, KeepAlive, Kompression, User-Agent (`AskAI-Research/1.0`) | User-Agent ist AskAI-Branding | **R** | `HttpTransportOptions`, Default-UA `Serp4J/<version>` |
| `RequestAuthentication` + `NoAuthentication`, `EncryptedHeaderAuthentication`, `EncryptedBearerAuthentication`, `EncryptedBasicAuthentication` | Strategy; entschlüsselt pro Request, löscht `char[]` | Strategy-Muster gut; an AHC und `EncryptedSecret` gebunden | **R** | `RequestAuthentication` arbeitet auf neutralem `HttpRequest.Builder`; Secret kommt über `CredentialProvider`-Port (Abschnitt 2.3) |
| `HttpResponseException` | Status + Body | Body kann Secrets/PII enthalten; AskAI kopiert ihn bewusst nicht | **R** | Body nur gekürzt/optional, Mapping auf Core-Exception-Hierarchie im Transport-Adapter |
| `UrlQueryBuilder` | URL-Encoding, null-sichere `add(..)` | | **Ü** | Core-Utility (intern) |

#### Konfiguration & Secrets (`search.config`, `search.security`)

| Komponente | Inhalt heute | Befund | Bewertung | Ziel |
|---|---|---|---|---|
| `ProviderConfigurationService` | load-or-create je Provider, Validierung, `save…(config, char[])` verschlüsselt und löscht | Host-Persistenz | **A** | Bleibt in AskAI; mappt Datei-DTO → Serp4J-Options |
| `ProviderConfigurationStore` / `GsonProviderConfigurationStore` | Atomares Schreiben, Owner-only-Rechte | | **A** | optional später `serp4j-config-json` (offene Frage F3) |
| `ProviderConfigurationPaths` | `${user.home}/agents/research/providers/*.json` | AskAI-Pfad | **A** | |
| `ProviderConfigurationBundle` | Tupel der drei Configs | | **A** | |
| `ConfigurationValidation` | Statische Helfer | | **Ü** | Core-intern |
| `DataForSeoSettingsDraft` | UI-Entwurf mit Pending-Passwort | Swing-nah | **A** | |
| `SecretCipher`, `AesGcmSecretCipher` (AES/GCM, 128 Bit, 96-Bit-IV), `EncryptedSecret`, `SecretKeyProvider`, `FileSecretKeyProvider`, `SecretValueService`, `SecretArrays` | Datei-basierte Verschlüsselung; Schlüssel liegt neben den Configs | Solide, aber Speicherpolitik des Hosts. Serp4J braucht nur „gib mir das Secret für Provider X“ | **A** (Krypto) / **N** (Port) | Core-Port `CredentialProvider` (liefert `char[]` pro Aufruf); mitgelieferte Implementierungen: `StaticCredentialProvider` (Tests), `EnvironmentCredentialProvider`. AskAI implementiert den Port mit seiner AES-GCM-Datei (später KeePass). `SecretArrays` **Ü** |

#### Tests (13 Klassen, ~970 LOC)

| Test | Abdeckung | Bewertung |
|---|---|---|
| `BraveSearchRequestMapperTest`, `BrightDataTargetUrlFactoryTest`, `DataForSeoRequestMapperTest` | Request-Mapping (je 1 Fall) | **Ü** → als Golden-Tests ausbauen |
| `DataForSeoSearchResponseMapperTest` | Organic-Mapping | **R** → auf neues Modell, mit gespeicherter Echt-Antwort |
| `BraveSearchProviderHttpTest`, `BrightDataSearchProviderHttpTest`, `DataForSeoSearchProviderHttpTest` | Lokaler `com.sun.net.httpserver.HttpServer`: Auth-Header, Query, Fehler-Status, Malformed JSON, kein Secret in `toString()` | **R** → Vertrag bleibt, Ausführung über Fake-`HttpTransport` (schnell, ohne Ports); ein HTTP-Server-Test je Transport-Adapter bleibt |
| `DataForSeoPlaygroundTest`, `DataForSeoSettingsDraftTest`, `ProviderConfigurationServiceTest`, `AesGcmSecretCipherTest`, `WebSearchProvidersModuleSmokeTest`, `WebSearchProvidersModuleResilienceTest` | Host-/Persistenzthemen | **A** (bleiben in AskAI) |
| fehlend | Bright-Data-Async-Polling, Composite (Teilfehler, Dedupe), Registry, Engines außer Google bei Bright Data, Response-Mapper Brave/Bright Data | in Serp4J **neu** |

Zusätzlich in AskAI `research-agent-runtime`: `AsyncModuleSearchProviderTest`, `AsyncProviderMappingTest`,
`ModuleAsyncSearchGenerationFactoryTest` – bleiben in AskAI und werden auf die Serp4J-Bridge umgestellt.

### 2.3 Befunde, die über das Modul hinausgehen

1. **Engine-Information geht in AskAI verloren.** `AsyncProviderMapping.defaultEngine` etikettiert Bright Data
   und DataForSEO immer als `GOOGLE`, unabhängig von der konfigurierten Engine; `AsyncModuleSearchProvider`
   ignoriert `SearchProviderRequest.getSearchEngine()` vollständig. Wer DataForSEO auf Bing stellt, bekommt
   Bing-Ergebnisse mit Google-Etikett. Serp4J muss die Engine pro Request annehmen und pro Item zurückmelden.
2. **Die Rohantwort erreicht AskAI nie.** `AsyncModuleSearchProvider.toRuntimeResult` verwirft `rawResponse`;
   `offset` wird nicht durchgereicht. Das ist der eigentliche Funktionsverlust heute, nicht in Serp4J.
3. **Zwei Fehlermodelle.** Die Runtime übersetzt HTTP-Status in eine gute, typisierte Hierarchie. Diese
   Hierarchie gehört in den Serp4J-Core, damit nicht jeder Konsument sie neu baut.
4. **Lifecycle/Hot-Reload ist Host-Sache, aber anspruchsvoll.** `AsyncSearchProviderRegistry` (Generationen,
   Leasing, Schließen nach letzter Nutzung) ist korrekt und gut getestet. Serp4J muss nur garantieren: Provider
   sind threadsicher, `close()` ist idempotent, laufende Futures werden bei `close()` definiert beendet.
5. **Ein Netty-Eventloop je Provider.** Drei aktive Provider = drei AHC-Instanzen + ein Polling-Thread.
   Für eine Bibliothek sollte ein geteilter Transport der Normalfall sein.
6. **Browser-Suche ist nicht Teil dieses Moduls.** `browser-api` und `browser-search-analysis` (~15 600 LOC,
   mechanische SERP-Analyse eines gerenderten DOM) sind Kandidaten für WP3/Paket F/G, nicht für WP5. Hinweis an
   WP3: `browser-search-analysis` ist bereits Java 8, frameworkfrei und hängt nur an `browser-api`.

---

## 3. Abgleich mit dem Serp4J-Zielbild

| Serp4J-Vorgabe | Heute im Modul | Lücke / Migrationsaufgabe |
|---|---|---|
| Domain kennt keine Provider | `WebSearchHit` trägt `SearchProviderId`-Enum mit festen Providern | Offene `ProviderId` in den Metadaten, nicht im Item-Kern |
| `SearchResponse` = Data + Metadata + Analysis | Nur Hits + Rohstring | Neues Modell (WP1); Mapper neu |
| `SerpFeature` | nicht vorhanden | Neue Mapper je Provider (Abschnitt 4.2) |
| `SearchCapabilities` | `supports(SearchEngine)` | Capabilities je Provider-Instanz: Engines, Vertikalen, Paging, Locale-Modell, SERP-Features, Rohantwort, Async/Polling, Kosteninformation, Conversational |
| Provider ≠ SearchEngine | teilweise (Enums getrennt), aber `BRAVE` doppelt und `MULTI` | sauber trennen; `MULTI` streichen |
| `ConversationalSearchProvider` | nicht vorhanden; keiner der drei Provider ist dialogfähig | Nicht betroffen. Die drei Cloud-Provider melden `conversational=false`. DataForSEO/Bright Data liefern AI Overviews nur als SERP-Feature einer zustandslosen Suche |
| Playwright optional | kein Browser im Modul | nicht betroffen |
| Java 8 | erfüllt (`--release 8`, keine Lambdas im Hauptcode) | beibehalten |
| Nur HTTP/JSON-Abhängigkeiten ohne Browser | Gson (api!) + AHC/Netty | Gson nur noch `implementation` in Provider-Modulen; AHC optional über Transport-Modul |
| Keine AskAI-Abhängigkeit | Paket `com.aresstack.askai…`, UA `AskAI-Research`, Pfad `~/agents/research` | Paket `com.aresstack.serp4j…`, neutraler UA, kein Default-Pfad |

---

## 4. Erhalt der Roh-JSON-Antworten und nicht normalisierter SERP-Informationen

Ziel: **kein Informationsverlust gegenüber der Provider-Antwort**, auch wenn das normalisierte Modell ein
Feld (noch) nicht kennt. Drei Ebenen:

### 4.1 Rohantwort

- Jede `SearchResponse` trägt in `SearchMetadata` optional eine `RawResponse`:
  `contentType`, `body` (String, unverändert), `httpStatus`, ausgewählte Antwort-Header, `providerRequestId`
  (Bright Data `response_id`, DataForSEO Task-`id`).
- Steuerbar über `SearchRequest.rawResponsePolicy`: `NONE`, `ON_ERROR`, `ALWAYS` (Default für Migration:
  `ALWAYS`, damit AskAI-Verhalten gleich bleibt; Bibliotheks-Default offen, siehe F5).
- Composite-Antworten enthalten die Teilantworten **aller** Provider (`List<SearchResponse> partialResponses`
  bzw. Metadaten je Provider), nicht `""` wie heute.
- Bright-Data-Async: Rohantwort des Ergebnisabrufs, nicht der Job-Bestätigung.

### 4.2 Strukturiert, aber nicht normalisiert

Jedes `SearchResultItem` und jedes `SerpFeature` bekommt ein unveränderliches
`ProviderAttributes`/`extensions`-Bag (z. B. `Map<String, JsonValue>` mit einem eigenen, Gson-freien
JSON-Wertmodell im Core). Der Response-Mapper legt dort **alle Felder ab, die er nicht in typisierte Felder
übersetzt**. Damit wird aus „verworfen“ ein „verfügbar, aber providerspezifisch“.

Erstbefüllung, die die Migration mindestens leisten muss:

| Provider | in typisierte Felder | in `SerpFeature` | in Extensions |
|---|---|---|---|
| Brave | `title`, `url`, `description`, `extra_snippets`, `age`/`page_age` → `publishedAt`, `meta_url.hostname` → `domain`, `language` | `infobox`, `faq`, `discussions`, `news`, `videos`, `locations`, Query-Korrektur (`query.altered`) | `profile`, `thumbnail`, `deep_results`, `family_friendly`, Rich-Callback-Hinweis |
| Bright Data | `link`, `title`, `description`, `rank`, `global_rank` → `absoluteRank`, `display_link` | `knowledge`, `people_also_ask`, `related`, `top_stories`, `images`, `videos`, `ai_overview` | `extensions`, `image`, `pagination`, `general.*` → Metadaten |
| DataForSEO | `url`, `title`, `description`, `rank_group`, `rank_absolute`, `domain`, `breadcrumb`, `timestamp` | jeder `items[].type ≠ organic` (featured_snippet, knowledge_graph, people_also_ask, ai_overview, local_pack, top_stories, related_searches, video, images, paid, …) | `highlighted`, `links`, `rating`, `rectangle`, `about_this_result` |
| alle | – | – | Metadaten: Ergebnisanzahl, Rechtschreibvorschlag, Check-URL, Kosten (`cost`), Laufzeit (`time`), Status-Code/-Message → `ProviderUsage` |

Regel (aus Angelos Vorgabe): Knowledge Graph und AI Overview sind **Suchdaten** (`SerpFeature`), keine
`SearchAnalysis`. Der Response-Mapper befüllt `SearchAnalysis` nie.

### 4.3 Unbekannte SERP-Elemente

Ein Element-Typ, den der Mapper nicht kennt (z. B. neuer DataForSEO-Typ), wird als
`SerpFeature` mit Typ `UNKNOWN` und vollständigen Extensions übernommen statt verworfen. Ein Test pro
Provider sichert das ab („unbekannter Typ überlebt das Mapping“).

---

## 5. Migrationsplan

Leitlinie: **Charakterisieren vor Verschieben.** Erst wird das heutige Verhalten mit Golden-Files
festgeschrieben, dann wird portiert, und dieselben Golden-Files müssen in Serp4J gegen das neue Modell
dasselbe (oder mehr) liefern. AskAI schaltet erst um, wenn Parität nachgewiesen ist.

Die Schritte passen auf die Implementierungspakete A–H aus der Projektplanung.

### Schritt 0 – Charakterisierung in AskAI (vor Paket A, rein additiv)

- Fixtures anlegen: je Provider mindestens eine **echte, anonymisierte** Antwort (Brave Web, Bright Data
  Google parsed JSON sync + async, DataForSEO advanced mit mehreren Item-Typen) sowie Fehlerantworten
  (401, 429, 5xx, DataForSEO-Status ≠ 20000, Bright Data „Request is pending“).
  Quelle: vorhandene Rohantworten aus AskAI-Läufen bzw. Playground; **keine neuen kostenpflichtigen Aufrufe
  nötig**, wenn Angelo vorhandene Antworten bereitstellen kann (F1).
- Golden-Tests für die Request-Mapper: Konfiguration + Request → exakte URL/Body/Header.
- Diese Fixtures werden später 1:1 nach `serp4j-testkit` kopiert.
- Keine Änderung am Produktionscode von AskAI.

### Schritt 1 – Core & Ports (Paket A)

Aus dem Bestand in den Core:

- `UrlQueryBuilder`, `ConfigurationValidation`, `SecretArrays` (intern).
- Exception-Hierarchie nach Vorbild der AskAI-Runtime.
- Ports: `SearchProvider`, `HttpTransport`, `RequestAuthentication`, `CredentialProvider`.
- Composite und Registry (refaktoriert), Dedupe-Strategie aus `CompositeWebSearchProvider.canonicalize`
  als Default-Implementierung übernehmen.

### Schritt 2 – Testkit (Paket E, parallel zu B–D)

- `FakeHttpTransport`: antwortet aus Fixtures, zeichnet Requests auf (URL, Header, Body), kann Status,
  Verzögerung, Timeout und „pending → ready“-Sequenzen simulieren.
- `MockSearchProvider` / `ScriptedSearchProvider`: liefert vorgegebene `SearchResponse`s oder Fehler,
  für Konsumenten-Tests (AskAI-Runtime, Composite).
- Wiederverwendbare **Vertragstests** (`SearchProviderContract`): jeder Provider muss bestehen:
  Auth wird gesetzt, Secret erscheint nie in `toString()`/Exception-Message, HTTP-Fehler werden typisiert,
  Malformed JSON scheitert kontrolliert, `close()` ist idempotent, Rohantwort bleibt erhalten,
  unbekannte SERP-Elemente überleben.

### Schritt 3 – Provider portieren (Pakete B, C, D, parallel)

Je Provider dieselbe Reihenfolge:

1. Enums und Validator **übernehmen** (Paket umbenennen, Gson-Annotationen entfernen).
2. Config-POJO → unveränderliches `…SearchOptions` mit Builder; Credential über `CredentialProvider`.
3. Request-Mapper **übernehmen**; Golden-Tests aus Schritt 0 müssen grün sein.
4. Response-Mapper **neu** auf `SearchData`/`SearchMetadata` inkl. Extensions und `SerpFeature`s;
   Organic-Ergebnis muss mindestens die heutigen Hits (Titel, URL, Snippet, Rang) identisch liefern.
5. Provider-Klasse auf `HttpTransport`.
6. Vertragstests aus dem Testkit.

Provider-spezifisch:

- **Brave:** Offset-Semantik (Seitenindex 0–9, `count ≤ 20`) als Capability `maxPageSize=20`, `maxPages=10`
  ausweisen.
- **Bright Data:** `brd_json`-Prüfpunkt klären und testen; Polling in geteilten Scheduler; Async-Pfad testen;
  Async-Modus heute nur für Google – als Capability ausweisen statt still falsch zu mappen;
  `DataFormat.MARKDOWN/SCREENSHOT` nur, wenn ein passender Mapper existiert.
- **DataForSEO:** `target_search_mode`-Bedingung prüfen; Location bleibt Provider-Option (ISO-Land →
  `location_code` ist nicht eindeutig), aber eine optionale Lookup-Tabelle für die häufigsten Länder
  wäre ein Gewinn (F6); `cost`/`time` nach `ProviderUsage`; Engine pro Request statt nur aus Config.

### Schritt 4 – Paritätsnachweis

Ein Paritätstest in AskAI (temporär, Test-Scope auf beide Bibliotheken): gleiche Fixture → altes Modul und
Serp4J → Vergleich der Organic-Hits (Titel, URL, Snippet, Rang, Engine). Abweichungen nur erlaubt, wenn sie
dokumentierte Fixes sind (Engine-Etikett, `brd_json`).

### Schritt 5 – AskAI auf Serp4J umstellen

Siehe Abschnitt 6. Danach `:web-search-providers` in AskAI löschen; die Host-Teile (Persistenz, Krypto,
Settings-Draft, Playground) wandern in ein AskAI-Modul, z. B. `:research-search-config`.

### Schritt 6 – Nach der Migration (nicht blockierend)

- Bright-Data-Async für Bing/DDG/Yandex.
- DataForSEO Standard-Queue (task_post/task_get) statt nur Live – günstiger, gleiche Ports.
- Weitere Provider aus dem AskAI-Katalog (SerpApi, Tavily, Exa, SearXNG …) direkt als Serp4J-Module.

### Funktionsparität – Checkliste

| Heutige Funktion | Nach Migration | Nachweis |
|---|---|---|
| Brave: alle Query-Parameter und `X-Loc-*`-Header | gleich | Golden-Test Request-Mapper |
| Brave: Treffer aus `web.results` inkl. Extra-Snippet-Fallback | gleich + Features | Fixture-Test |
| Bright Data sync für Google/Bing/DDG/Yandex mit SafeSearch und Paging | gleich (+ `brd_json`-Fix) | Golden-Tests je Engine |
| Bright Data async mit Polling, Abbruch nach `maximumPollAttempts` | gleich, getestet | FakeTransport mit Sequenz |
| DataForSEO Live-Organic, alle Task-Parameter, alle Engines | gleich | Golden-Test |
| DataForSEO Statusprüfung Envelope + Task | gleich | Fehler-Fixtures |
| Rohantwort je Provider | gleich, zusätzlich im Composite | Vertragstest |
| Composite parallel, Teilfehler-Toleranz, URL-Dedupe, `owning` | gleich + Fehlerdetails + Herkunft | Composite-Tests |
| Registry `require/getAll/close` mit Fehlersammlung | gleich | Registry-Tests |
| Resilientes Öffnen (ein kaputter Provider legt die anderen nicht lahm) | in AskAI-Bootstrap | bestehender AskAI-Test |
| Verschlüsselte Secrets, nie Klartext in Datei/`toString()` | AskAI-`CredentialProvider` + Serp4J-Vertragstest | beide Testseiten |
| Hot-Reload ohne Use-after-close | AskAI `AsyncSearchProviderRegistry` unverändert | bestehende AskAI-Tests |
| DataForSEO-Playground (Preview, Kosten, Laufzeit) | AskAI, gespeist aus `ProviderUsage` + `RequestPreview` | UI-Test in AskAI |

---

## 6. Wie AskAI Serp4J konsumiert

```text
AskAI
├── :research-agent-runtime
│     SearchProvider (Runtime-Port, synchron)  ←  Serp4jSearchProviderBridge  ←  serp4j SearchProvider (async)
│     AsyncSearchProviderRegistry (Generationen/Leasing) bleibt
├── :research-search-config   (neu, aus :web-search-providers herausgelöst)
│     Gson-Dateien, ProviderConfigurationService, AES-GCM, SettingsDraft, Playground
│     AskAiCredentialProvider implements serp4j CredentialProvider
│     ProviderOptionsMapper: brave.json → BraveSearchOptions, …
└── :research-agent-ui-plugin  → nutzt :research-search-config

Abhängigkeiten
  implementation 'com.aresstack:serp4j-core:<v>'
  implementation 'com.aresstack:serp4j-provider-brave:<v>'
  implementation 'com.aresstack:serp4j-provider-brightdata:<v>'
  implementation 'com.aresstack:serp4j-provider-dataforseo:<v>'
  implementation 'com.aresstack:serp4j-transport-ahc:<v>'      // solange AHC gewünscht
  testImplementation 'com.aresstack:serp4j-testkit:<v>'
```

Konkrete Umstellung:

1. `AsyncModuleSearchProvider` → `Serp4jSearchProviderBridge`: gleiche Timeout-/Interrupt-Logik, aber
   - die angeforderte `SearchEngine` wird in den Serp4J-Request übernommen,
   - das Engine-Etikett kommt aus dem Ergebnis statt aus `AsyncProviderMapping.defaultEngine`,
   - `translate(..)` schrumpft auf ein 1:1-Mapping der Serp4J-Exceptions,
   - `absoluteRank`, `domain`, `publishedAt` werden aus dem reichen Modell befüllt (heute `rank`, Host, `null`).
2. `ModuleAsyncSearchGenerationFactory` baut die Generation aus `:research-search-config` + Serp4J-Factories
   statt `WebSearchProvidersModule.openResilient`. Das Resilient-Verhalten bleibt dort.
3. Die Konfigurationsdateien unter `~/agents/research/providers` bleiben **formatkompatibel**; Secrets müssen
   nicht neu eingegeben werden. Der `ProviderOptionsMapper` ist die einzige Stelle, die Datei-DTO und
   Serp4J-Options kennt.
4. Übergangsweise kann AskAI eine Composite-Build-Einbindung nutzen (`includeBuild '../Serp4J'`), bis das erste
   Serp4J-Release veröffentlicht ist (Abgleich mit WP4).
5. Wenn AskAI die Rohantwort oder SERP-Features nutzen will (z. B. für Quellen-Diagnose), erweitert AskAI
   seinen eigenen `SearchProviderResult` – Serp4J liefert die Daten bereits.

---

## 7. Teststrategie

| Ebene | Ort | Werkzeug | Kosten |
|---|---|---|---|
| Request-Mapping (Golden) | Provider-Modul | JUnit, erwartete URL/Body/Header als Datei | keine |
| Response-Mapping | Provider-Modul | Fixtures (echte, anonymisierte Antworten) | keine |
| Provider-Vertrag | Provider-Modul | `serp4j-testkit` `SearchProviderContract` + `FakeHttpTransport` | keine |
| Transport-Adapter | `serp4j-transport-*` | lokaler JDK-`HttpServer` (wie heute) | keine |
| Composite/Registry | Core | `ScriptedSearchProvider` | keine |
| Konsument (AskAI) | AskAI | `serp4j-testkit` Mock-Provider | keine |
| Live-Smoke | eigenes Gradle-Source-Set `liveTest` | echte Keys aus Umgebungsvariablen; **nie** im Default-Build und nie in CI ohne explizites Opt-in (`-Pserp4j.live=true`) | kostenpflichtig, manuell |

Regeln:

- Kein Test im Default-Build darf das Netzwerk verlassen. Ein JUnit-Guard (oder ein
  `SecurityManager`-freier Ansatz: `FakeHttpTransport` als einziger erlaubter Transport im `test`-Scope der
  Provider-Module) sichert das ab.
- Fixtures werden vor dem Einchecken anonymisiert (keine Keys, keine Account-IDs, keine Zone-Namen);
  ein Test scannt Fixtures auf bekannte Secret-Muster.
- Live-Smoke-Läufe dürfen ihre Antworten als neue Fixtures speichern (`-Pserp4j.recordFixtures=true`), damit
  API-Änderungen der Anbieter mit einem einzigen bezahlten Aufruf nachgezogen werden können.
- Java-8-Kompatibilität: Tests laufen mit Toolchain 8 (Abgleich WP4).

---

## 8. Risiken

| Risiko | Wirkung | Gegenmaßnahme |
|---|---|---|
| Keine echten Antwort-Fixtures verfügbar | Response-Mapper werden gegen Annahmen gebaut | F1 früh klären; notfalls je Provider ein bezahlter Aufruf im Live-Smoke mit Recording |
| `brd_json`-Verhalten falsch eingeschätzt | Bright Data sync liefert 0 Treffer, unbemerkt | Fixture + Test, Verifikation gegen API-Doku vor Paket C |
| Neues Modell zu groß für Java-8-Builder-Stil | Boilerplate, Pflegeaufwand | Extensions-Bag statt typisierter Felder für Seltenes (WP1) |
| Gson weiter als `api`-Abhängigkeit | Konsumenten erben Gson-Version | Core ohne JSON-Bibliothek; Gson nur `implementation` in Provider-Modulen |
| AHC/Netty-Gewicht im Default | widerspricht „leichtgewichtig“ | Transport-Port + JDK-Adapter; AHC optional |
| Hot-Reload-Semantik in AskAI bricht | laufende Suchen scheitern bei Reload | AskAI-Registry bleibt unverändert; Serp4J garantiert idempotentes `close()` und Thread-Sicherheit (Vertragstest) |
| Paketumbenennung bricht PF4J-Plugin-Classpath | Plugin lädt nicht | `AgentPluginLoadIntegrationTest` in AskAI als Gate der Umstellung |

---

## 9. Offene Fragen

An Angelo:

- **F1 – Fixtures:** Gibt es gespeicherte echte Antworten der drei Anbieter (z. B. aus dem DataForSEO-Playground
  oder AskAI-Logs), die anonymisiert als Testdaten dienen dürfen? Andernfalls: ist je Provider ein bezahlter
  Recording-Aufruf in Ordnung?
- **F2 – Bright Data sync:** Läuft Bright Data in AskAI produktiv mit Treffern? Falls ja, steht vermutlich
  `brd_json=1` in `additionalSearchParameters` der lokalen `brightdata.json` – dann sollte das der Default werden.

An den Integrations-Thread (Konflikte mit WP1/WP2/WP4):

- **F3 – Datei-Konfiguration:** Bleibt JSON-Datei-Persistenz komplett in AskAI, oder bietet Serp4J ein
  optionales `serp4j-config-json` an (Formatkompatibilität mit den heutigen `brave.json`/… wäre dann Pflicht)?
- **F4 – Transport-Default:** Wird AHC das Standard-Transportmodul, oder bekommt der Core einen
  dependency-freien JDK-Transport als Default? (WP4: Modulgraph; WP2: Port-Vertrag)
- **F5 – Rohantwort-Default:** `ALWAYS` (verlustfrei, mehr Speicher) oder `ON_ERROR` als Bibliotheks-Default?
- **F6 – Locale-Modell:** Wie werden neutrale Locale-Angaben (ISO-Land/Sprache) auf providerspezifische
  Codes abgebildet (DataForSEO `location_code`)? Im Provider-Modul mit Lookup-Tabelle oder nur als
  Provider-Option?
- **F7 – Provider-Instanzen:** Erlaubt die Registry mehrere Instanzen desselben Providers (z. B. Bright Data
  Google und Bright Data Bing parallel)? WP5 empfiehlt ja.
- **F8 – Exception-Hierarchie:** Übernahme der AskAI-Runtime-Hierarchie (Auth/RateLimit/Temporary/Response/
  Configuration/NotImplemented/UnsupportedEngine) als Core-Vertrag?
- **F9 – JSON-Wertmodell im Core:** Eigenes minimales, Gson-freies Wertmodell für Extensions oder
  `Map<String, Object>` mit dokumentierten Typen?

---

## Anhang A – Dateiliste des Quellmoduls

`com.aresstack.askai.research.search` (Produktionscode, 72 Klassen):

- `api` (7): `SearchEngine`, `SearchProviderId`, `WebSearchException`, `WebSearchHit`, `WebSearchProvider`,
  `WebSearchRequest`, `WebSearchResult`
- `application` (4): `CompositeWebSearchProvider`, `SearchProviderFactory`, `WebSearchProviderRegistry`,
  `WebSearchProvidersModule`
- `brave` (11): `BraveFreshnessConfiguration`, `BraveFreshnessMode`, `BraveLocationConfiguration`,
  `BraveResultType`, `BraveSafeSearch`, `BraveSearchConfiguration`, `BraveSearchConfigurationValidator`,
  `BraveSearchProvider`, `BraveSearchRequestMapper`, `BraveSearchResponseMapper`, `BraveUnits`
- `brightdata` (12): `BrightDataDataFormat`, `BrightDataExecutionMode`, `BrightDataRequestMapper`,
  `BrightDataRequestMethod`, `BrightDataResponseFormat`, `BrightDataSafeSearch`,
  `BrightDataSearchConfiguration`, `BrightDataSearchConfigurationValidator`, `BrightDataSearchEngine`,
  `BrightDataSearchProvider`, `BrightDataSearchResponseMapper`, `BrightDataTargetUrlFactory`
- `dataforseo` (14): `DataForSeoDevice`, `DataForSeoOperatingSystem`, `DataForSeoPlayground`,
  `DataForSeoRequestMapper`, `DataForSeoResultFormat`, `DataForSeoSearchConfiguration`,
  `DataForSeoSearchConfigurationValidator`, `DataForSeoSearchEngine`, `DataForSeoSearchProvider`,
  `DataForSeoSearchResponseMapper`, `DataForSeoSerpElementType`, `DataForSeoStopCrawlTarget`,
  `DataForSeoTargetMatchType`, `DataForSeoTargetSearchMode`
- `http` (9): `AsyncHttpClientFactory`, `AsyncJsonHttpClient`, `EncryptedBasicAuthentication`,
  `EncryptedBearerAuthentication`, `EncryptedHeaderAuthentication`, `HttpResponseException`,
  `NoAuthentication`, `RequestAuthentication`, `UrlQueryBuilder`
- `config` (8): `ConfigurationValidation`, `DataForSeoSettingsDraft`, `GsonProviderConfigurationStore`,
  `HttpTransportConfiguration`, `ProviderConfigurationBundle`, `ProviderConfigurationPaths`,
  `ProviderConfigurationService`, `ProviderConfigurationStore`
- `security` (7): `AesGcmSecretCipher`, `EncryptedSecret`, `FileSecretKeyProvider`, `SecretArrays`,
  `SecretCipher`, `SecretKeyProvider`, `SecretValueService`

Weitere Dateien: `README.md`, `INTEGRATION.md`, `examples/IntegrationExample.java`,
`examples/providers/{brave,brightdata,dataforseo}.json`.
