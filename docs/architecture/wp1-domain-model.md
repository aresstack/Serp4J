# WP1 – Domain Model & Unified Search Format

> **Status:** Architekturvorschlag (Entwurf zur Integration) · **Stand:** 2026-10-31
> **Scope:** Nur Domain-Modell und gemeinsames Suchformat. Ports, Sessions-Lifecycle (WP2), Browser (WP3), Module/Build (WP4) und Migration (WP5) werden nur dort berührt, wo das Datenmodell sie bedingt.
> **Keine produktive Implementierung.** Alle Java-Fragmente sind Dokumentation (Signaturen), kein Code zum Übernehmen ohne Freigabe.

---

## 0. Zusammenfassung der Entscheidungen

| # | Entscheidung | Kurzbegründung |
|---|---|---|
| D1 | `SearchResponse = SearchData (Pflicht) + SearchMetadata (optional) + SearchAnalysis (optional)` – wie vorgegeben. | Trennung von Daten, Herkunft und Auswertung. |
| D2 | Provider, Engine, Vertical, Feature-Typen sind **offene Wertetypen** (`final class` mit String-Code + bekannten Konstanten), **keine Enums**. | Drittanbieter-Provider/-Engines müssen ohne Core-Änderung möglich sein. Enums sind in Java nicht erweiterbar. |
| D3 | `SearchResultItem` = ein einzelnes, verlinktes Dokument mit Position (organisch, Anzeige, News, Video, Bild …), unterschieden über `ResultType`. | Ein Typ für alles, was „ein Treffer“ ist; Konsumenten filtern nach Typ. |
| D4 | `SerpFeature` = jeder SERP-Block, der **kein einzelner Treffer** ist (Knowledge Panel, AI Overview, Featured Snippet, People-also-ask, Related Searches, Local Pack, Top Stories …). Typisierte Unterklassen für einen Kernsatz, `GenericSerpFeature` für alles Unbekannte. | Strukturierte Kernfeatures für Konsumenten, verlustfreies Durchreichen unbekannter Blöcke. |
| D5 | Erweiterungsmechanismus: **`Extensions`** mit typisierten `ExtensionKey<T>` (provider-namespaced) plus JSON-freiem Baum **`StructuredValue`** für ungemappte Felder; optionaler **`RawPayload`** in den Metadaten. | Providerspezifisches geht nicht verloren, Core bleibt frei von Gson/Jackson. |
| D6 | Rang wird als **`SerpPosition`** modelliert (`rankInGroup`, `rankAbsolute`, `page`, `area`), nicht als einzelnes `int`. | Die drei Provider liefern heute drei verschiedene Rang-Semantiken (siehe §2.4). |
| D7 | Nullability-Konvention: Pflichtfelder nie `null`; optionale Einzelwerte als `Optional<T>`/`OptionalInt` im **Getter** (Feld intern nullable); Collections nie `null`, immer unveränderlich. | Java-8-kompatibel, eindeutig, keine `null`-Checks bei Konsumenten. |
| D8 | `SearchAnalysis` enthält **nur abgeleitete** Erkenntnisse mit Herkunftsangabe (`AnalysisProvenance`) und referenziert Suchdaten über `ElementId`, kopiert sie nicht. Alles, was ein Provider liefert (auch KI-Antworten, Knowledge Graph, Brave `is_navigational`), ist **Data bzw. Metadata**. | Angelos Regel 4: Provider-Daten und Analyse nicht vermischen. |
| D9 | Jedes Element in `SearchData` (Item und Feature) hat eine response-lokal eindeutige **`ElementId`**. | Referenzen aus Analysis, Deduplizierung im Composite, Zitate in KI-Antworten. |
| D10 | KI-Antworten (Google AI Overview auf der SERP, Google AI Mode als Dialog) werden als **`GeneratedAnswer`-Feature** in `SearchData` modelliert; Dialogzustand nur als `ConversationContext` in den Metadaten. Jeder Dialogschritt liefert eine normale `SearchResponse`. | Ein Datenmodell für zustandslose und zustandsbehaftete Suche; WP2 definiert die Session-Ports darauf. |
| D11 | Core-Domain hat **keine** JSON-, HTTP- oder Browser-Abhängigkeit, auch keine Annotationen. (De-)Serialisierung in eigenem Modul (Vorschlag `serp4j-json`, WP4). | Clean Architecture, schlanker Core. |

---

## 1. Bestandsaufnahme AskAI `web-search-providers` (lesend)

Untersucht: `askai/web-search-providers` (Paket `com.aresstack.askai.research.search`, ~7 100 LOC), insbesondere `api/*`, die drei `*SearchResponseMapper`, `*RequestMapper`, Enum-Typen, Tests und `examples/providers/*.json`; außerdem der Konsument `research-agent-runtime` (`AsyncModuleSearchProvider`, `SearchHit`).

### 1.1 Heutiges Modell

```
WebSearchRequest  { query, countryCode?, languageCode?, maximumResults(1..200), offset }
WebSearchResult   { providerId, searchEngine, List<WebSearchHit> hits, String rawResponse }
WebSearchHit      { providerId, searchEngine, rank>0, title!, url!, snippet("") }
SearchProviderId  enum { BRAVE, BRIGHT_DATA, DATA_FOR_SEO, COMPOSITE }
SearchEngine      enum { MULTI, BRAVE, GOOGLE, BING, DUCKDUCKGO, YAHOO, YANDEX, BAIDU, NAVER, SEZNAM }
WebSearchProvider { getProviderId(), supports(SearchEngine), CompletableFuture<WebSearchResult> search(req), close() }
```

Der AskAI-Konsument (`research-agent-runtime/.../SearchHit`) reichert die Hits bereits um `query`, `domain`, `absoluteRank`, `publishedAt` an – ein Hinweis, dass genau diese Felder im Zielmodell fehlen.

### 1.2 Befunde / Lücken

| Befund | Auswirkung | Antwort im Zielmodell |
|---|---|---|
| Nur organische Treffer werden gemappt; DataForSEO filtert `type != "organic"` weg, Brave liest nur `web.results`, Bright Data nur `organic`. | Knowledge Graph, PAA, Featured Snippet, AI Overview, News usw. gehen verloren. | `SerpFeature` + `ResultType` (D3/D4). |
| `rank` hat je Provider andere Semantik: Brave = laufender Zähler nach Filterung, Bright Data = `rank` (sonst Zähler), DataForSEO = `rank_group` (sonst `rank_absolute`). | Ränge sind nicht vergleichbar. | `SerpPosition` (D6). |
| `title` ist Pflicht → Treffer ohne Titel werden verworfen. | Bild-/Video-/Local-Treffer ohne Titel fallen raus. | Nur `url` Pflicht; `title` optional (Mapper dürfen weiterhin filtern). |
| Snippet-Fallback verschmilzt Brave `extra_snippets` zu einem String; DataForSEO `extended_snippet` ersetzt `description`. | Struktur geht verloren. | `snippet` + `List<String> additionalSnippets`. |
| `rawResponse` wird immer als String mitgeführt (genutzt in `DataForSeoSettingsPanel` als Playground-Ansicht). | Speicher, keine Steuerung. | `RawPayload` optional, per Request abschaltbar (`RawPayloadPolicy`). |
| Keine Metadaten: Kosten (DataForSEO `cost`), Rate-Limits (Brave-Header), Request-IDs (DataForSEO task id, Bright Data `response_id`), korrigierte Query, Gesamttrefferzahl. | Nicht auswertbar, nicht abrechenbar. | `SearchMetadata` (§4). |
| `SearchProviderId` / `SearchEngine` sind geschlossene Enums; `COMPOSITE` ist kein echter Provider, `MULTI` keine echte Engine; `BRAVE` ist zugleich Provider und Engine. | Keine Erweiterbarkeit; vermischt Provider/Engine. | `ProviderId`/`SearchEngineId` als Wertetypen; Composite wird über `ResultProvenance` sichtbar, nicht über eine Pseudo-ID (D2). |
| Provider-Herkunft steckt redundant in jedem Hit. | OK für Merge, aber starr. | Herkunft auf Response-Ebene (Metadata); pro Element nur optional `ResultProvenance` (für Composite). |
| Locale nur `countryCode`/`languageCode` im Request, kein Echo in der Response. | Man weiß nicht, mit welcher Locale tatsächlich gesucht wurde. | `SearchLocale` in Request **und** Metadata (angefragt vs. effektiv). |

---

## 2. Vergleich der JSON-Formate

Quellen: AskAI-Mapper/-Tests (verifiziert, siehe Klassen oben) und öffentliche API-Dokumentation der Anbieter (Stand Wissensbasis; **vor Implementierung mit echten Responses als Fixtures zu verifizieren**, siehe offene Frage Q9). Felder, die AskAI heute liest, sind **fett**.

### 2.1 Brave Web Search API (`GET /res/v1/web/search`)

```
{ "type": "search",
  "query":  { "original", "altered", "spellcheck_off", "is_navigational", "is_news_breaking",
              "country", "city", "postal_code", "more_results_available", "bad_results" },
  "mixed":  { "main": [ {"type":"web","index":0,"all":false}, ... ], "top": [...], "side": [...] },
  "web":    { "results": [ { **"title"**, **"url"**, **"description"**, **"extra_snippets"**[],
                             "page_age", "age", "language", "family_friendly",
                             "profile":{name,url,img}, "meta_url":{scheme,netloc,hostname,favicon,path},
                             "thumbnail":{src,original}, "deep_results", "faq", "article",
                             "product", "review", "video", "recipe", "location", "schemas" } ] },
  "news":   { "results": [ {title,url,description,age,page_age,meta_url,thumbnail,source,breaking} ] },
  "videos": { "results": [ {title,url,description,video:{duration,views,creator},thumbnail,age} ] },
  "discussions": { "results": [ {data:{forum_name,num_answers,score,question,top_comment}, url, title} ] },
  "faq":    { "results": [ {question,answer,title,url,meta_url} ] },
  "infobox":{ "results": [ {label,category,long_desc,attributes,profiles,website_url,images,...} ] },
  "locations": { "results": [ ... ] },
  "summarizer": { "key" }   // nur mit Summarizer-Plan
}
Header: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset, X-RateLimit-Policy
```

Besonderheiten: Brave ist **Provider und Engine** zugleich. Die Reihenfolge der Blöcke auf der Seite steht nicht in den Blöcken selbst, sondern in `mixed.main/top/side` (Index-Verweise) → wird für `SerpPosition.area` und `rankAbsolute` gebraucht. `text_decorations=true` liefert `<strong>`-Markup in Snippets. Zeitangaben: `page_age` (ISO) und `age` (Anzeigetext, z. B. „2 days ago“).

### 2.2 Bright Data SERP API (`brd_json=1`, sync oder async über `response_id`)

```
{ "general":   { "search_engine", "query", "language", "location", "mobile", "search_type",
                 "page_title", "results_cnt", "search_time", "timestamp" },
  "input":     { "original_url", "user_agent", "request_id" },
  **"organic"**: [ { **"link"** (Fallback **"url"**), "display_link", **"title"**,
                   **"description"** (Fallback **"snippet"**), **"rank"**, "global_rank",
                   "extensions":[{type,link,text}], "image", "date" } ],
  "top_ads"/"bottom_ads": [ {link,display_link,title,description,rank,global_rank,referral_link} ],
  "knowledge": { "name","subtitle","description","description_source","description_link",
                 "facts":[{key,value:[{text,link}]}], "images", ... },
  "people_also_ask": [ {question, answers:[...], answer_source, answer_link, rank, global_rank} ],
  "related":   [ {text, link, rank, global_rank} ],
  "top_stories", "images", "videos", "snack_pack" (Local), "twitter", "recipes",
  "ai_overview" (Google, sofern vorhanden),
  "pagination": { "current_page", "next_page", "next_page_link", "pages":[...] },
  "navigation": [ {title, href} ]
}
Async: POST → { **"response_id"** } → GET get_result?response_id=…
```

Besonderheiten: Bright Data ist ein **Provider, der fremde Engines** (Google, Bing, DuckDuckGo, Yandex) scrapt. `rank` = Rang in der Gruppe, `global_rank` = Position auf der ganzen Seite → passt direkt auf `SerpPosition`. Format je Engine leicht unterschiedlich. Mit `data_format=markdown|screenshot` kommt gar kein JSON → `RawPayload` mit anderem Content-Type.

### 2.3 DataForSEO SERP API (`/v3/serp/{engine}/organic/live/advanced`)

```
{ "status_code": 20000, "status_message", "time", **"cost"**, "tasks_count", "tasks_error",
  **"tasks"**: [ { "id", **"status_code"**, **"status_message"**, "time", "cost",
                 "data": { "se", "keyword", "location_code", "language_code", "device", "os" },
                 **"result"**: [ { "keyword", "se_domain", "check_url", "datetime",
                                 "spell": {keyword, type}, "item_types": [...],
                                 "se_results_count", "items_count",
                                 **"items"**: [ { **"type"**, **"rank_group"**, **"rank_absolute"**,
                                                "page", "position" (left/right), "xpath",
                                                "domain", **"title"**, **"url"**, "breadcrumb",
                                                **"description"**, **"extended_snippet"**,
                                                "pre_snippet", "website_name", "timestamp",
                                                "rating": {value, votes_count, rating_max},
                                                "highlighted": [...], "links": [...],
                                                "is_featured_snippet", "is_video", ... } ] } ] } ] }
Item-Typen u. a.: organic, paid, featured_snippet, knowledge_graph, people_also_ask,
  related_searches, local_pack, map, top_stories, images, video, answer_box, carousel,
  shopping, twitter, ai_overview { markdown, items[ {title,text,markdown,references,links} ], references[ {source,domain,url,title,text} ] }
```

Besonderheiten: Envelope mit Task-Liste und Status-Codes auf zwei Ebenen (AskAI validiert beide). **Alle** SERP-Elemente stehen in einer flachen `items`-Liste mit `type` und gemeinsamem Rangsystem (`rank_group` innerhalb des Typs, `rank_absolute` über alles). Kosten pro Task und gesamt. DataForSEO bietet zusätzlich einen separaten Google-AI-Mode-Endpunkt; ob der in Serp4J zunächst angebunden wird, ist Q7.

### 2.4 Konsolidierte Gegenüberstellung

| Aspekt | Brave | Bright Data | DataForSEO | Zielmodell |
|---|---|---|---|---|
| Provider ≠ Engine | gleich (brave/brave) | Engine wählbar | Engine wählbar | `ProviderId` + `SearchEngineId` getrennt |
| Organische Liste | `web.results[]` | `organic[]` | `items[type=organic]` | `ResultType.ORGANIC` |
| URL | `url` | `link`/`url` | `url` | `url` (Pflicht) |
| Anzeige-URL/Domain | `meta_url.hostname` | `display_link` | `domain`, `breadcrumb` | `displayUrl`, `domain` |
| Rang in Gruppe | (implizit, Index) | `rank` | `rank_group` | `SerpPosition.rankInGroup` |
| Rang absolut | über `mixed` ableitbar | `global_rank` | `rank_absolute` | `SerpPosition.rankAbsolute` |
| Snippet | `description` + `extra_snippets[]` | `description`/`snippet` | `description` + `extended_snippet`, `pre_snippet` | `snippet` + `additionalSnippets` |
| Datum | `page_age` (ISO), `age` (Text) | `date` (Text) | `timestamp` | `PublishedDate { instant?, displayText? }` |
| Knowledge Panel | `infobox` | `knowledge` | `knowledge_graph` | `KnowledgePanelFeature` |
| PAA / FAQ | `faq` | `people_also_ask` | `people_also_ask` | `QuestionAnswerFeature` |
| Verwandte Suchen | (`query`-Varianten, kein Block) | `related` | `related_searches` | `RelatedQueriesFeature` |
| KI-Antwort | `summarizer` (Key, separater Call) | `ai_overview` | `ai_overview` | `GeneratedAnswerFeature` |
| Gesamttreffer | – | `general.results_cnt` | `se_results_count` | `PageInfo.totalResultsEstimate` |
| Korrigierte Query | `query.altered` | – | `spell.keyword` | `QueryInfo.effectiveQuery`, `spellCorrection` |
| Kosten | – (Plan) | – (Plan) | `cost` (USD) | `ProviderUsage.cost` |
| Rate-Limit | Header | – | – | `ProviderUsage.rateLimit` |
| Request-ID | – | `response_id`, `input.request_id` | `tasks[].id` | `ExecutionInfo.providerRequestId` |

---

## 3. Gesamtstruktur

```
SearchResponse                                  (immutable, Builder, withAnalysis(..))
├── SearchData                       [Pflicht]
│   ├── List<SearchResultItem> results          (alle Treffer, ResultType-typisiert, Seitenreihenfolge)
│   └── List<SerpFeature>      features         (KnowledgePanel, GeneratedAnswer, PAA, …, Generic)
├── Optional<SearchMetadata>         [optional]
│   ├── ProviderId provider, SearchEngineId engine, SearchVertical vertical
│   ├── QueryInfo        (original, effective, spellCorrection)
│   ├── SearchLocale     (effektiv verwendete Locale) + DeviceType
│   ├── PageInfo         (offset, requestedCount, page, hasMore, totalResultsEstimate)
│   ├── ExecutionInfo    (startedAt, duration, providerRequestId, attempts)
│   ├── ProviderUsage    (cost, credits, rateLimit)
│   ├── ConversationContext? (nur bei Dialogsuche)
│   ├── List<SearchWarning>
│   ├── Optional<RawPayload>
│   └── Extensions
└── Optional<SearchAnalysis>         [optional, nur abgeleitet]
    ├── AnalysisProvenance (producer, version, createdAt)
    ├── Optional<SearchIntent>
    ├── List<EntityMention>  → referenziert ElementIds
    ├── List<Topic>          → referenziert ElementIds
    └── SearchMetrics
```

### 3.1 Package-Vorschlag (mit WP2/WP4 abzustimmen)

```
com.aresstack.serp4j.model              SearchRequest, SearchResponse
com.aresstack.serp4j.model.id           ProviderId, SearchEngineId, SearchVertical, ElementId
com.aresstack.serp4j.model.data         SearchData, SearchResultItem, ResultType, SerpPosition, …
com.aresstack.serp4j.model.data.feature SerpFeature, SerpFeatureType, *Feature, SerpFeatureVisitor
com.aresstack.serp4j.model.metadata     SearchMetadata, QueryInfo, SearchLocale, PageInfo, …
com.aresstack.serp4j.model.analysis     SearchAnalysis, SearchIntent, EntityMention, Topic, …
com.aresstack.serp4j.model.ext          Extensions, ExtensionKey, StructuredValue, RawPayload
```

---

## 4. Klassenvorschläge (Java 8, Dokumentation)

### 4.1 Allgemeine Konventionen

- Alle Domänenklassen `public final`, alle Felder `private final`, keine Setter.
- Konstruktion über statische `builder(...)`-Methode mit **Pflichtparametern im `builder(...)`-Aufruf** und optionalen Feldern als Builder-Methoden; Validierung in `build()` (`IllegalArgumentException` / `NullPointerException` mit Feldname).
- `toBuilder()` auf allen größeren Objekten, damit Mapper/Composite/Analyzer abgeleitete Kopien erzeugen können.
- Listen werden im Konstruktor defensiv kopiert und `Collections.unmodifiableList` verpackt; Getter liefern nie `null`.
- Optionale Einzelwerte: Feld nullable, Getter `Optional<T>` / `OptionalInt` / `OptionalLong` / `OptionalDouble`. Kein `Optional` als Feld oder Parameter.
- `equals`/`hashCode`/`toString` mit Wertesemantik.
- Zeit: `java.time.Instant`, `java.time.Duration` (Java 8 vorhanden). Geld: `BigDecimal` + ISO-4217-Code.
- URLs als `String` (nicht `java.net.URI`), weil SERPs auch Redirect-/Tracking-URLs mit ungültigen Zeichen liefern; Validierung „nicht leer“, Normalisierung ist Mapper-Aufgabe.
- **Keine** Annotationen aus Fremdbibliotheken, kein `Serializable` (siehe Q5).

### 4.2 Offene Identifikatoren (D2)

```java
public final class ProviderId {                 // analog: SearchEngineId, SearchVertical, ResultType, SerpFeatureType
    public static final ProviderId BRAVE       = ProviderId.of("brave");
    public static final ProviderId BRIGHT_DATA = ProviderId.of("bright-data");
    public static final ProviderId DATA_FOR_SEO= ProviderId.of("dataforseo");
    public static final ProviderId GOOGLE_BROWSER = ProviderId.of("google-browser"); // WP3, Name offen

    public static ProviderId of(String code);   // [a-z0-9][a-z0-9._-]*, lower-case normalisiert
    public String getCode();
    // equals/hashCode über code
}

public final class SearchEngineId {
    GOOGLE("google"), GOOGLE_AI_MODE("google-ai-mode")?, BING, BRAVE, DUCKDUCKGO, YAHOO, YANDEX, BAIDU, NAVER, SEZNAM
}
```

- `MULTI` und `COMPOSITE` aus AskAI entfallen als IDs. Ein Composite-Ergebnis trägt in den Metadaten die ID des Composite (z. B. `ProviderId.of("composite")` als normaler, nicht reservierter Wert) und pro Element eine `ResultProvenance`.
- **Google AI Mode als Engine oder als Vertical?** Vorschlag: `SearchEngineId.GOOGLE` + `SearchVertical.AI_MODE`, weil es dieselbe Engine mit anderem Modus ist (analog zu News/Images). Siehe Q3.

```java
public final class SearchVertical { WEB, NEWS, IMAGES, VIDEOS, SHOPPING, LOCAL, AI_MODE }
```

### 4.3 SearchRequest (Datenanteil; Port-Vertrag in WP2)

```java
public final class SearchRequest {
    String getQuery();                                  // Pflicht, getrimmt, nicht leer
    SearchVertical getVertical();                       // Default WEB
    Optional<SearchEngineId> getPreferredEngine();      // leer = Provider-Default
    Optional<SearchLocale> getLocale();
    Optional<DeviceType> getDevice();                   // DESKTOP, MOBILE, TABLET
    Paging getPaging();                                 // offset (>=0), limit (>=1), Default 0/10
    Optional<SafeSearch> getSafeSearch();               // OFF, MODERATE, STRICT
    Optional<TimeRange> getTimeRange();                 // relativ (DAY/WEEK/MONTH/YEAR) oder from/to
    RawPayloadPolicy getRawPayloadPolicy();             // NONE (Default), ON_ERROR, ALWAYS
    Extensions getProviderOptions();                    // z. B. BraveOptions.GOGGLES, DataForSeoOptions.DEPTH
    static Builder builder(String query);
}

public final class SearchLocale {
    Optional<String> getCountryCode();     // ISO 3166-1 alpha-2, upper-case
    Optional<String> getLanguageCode();    // ISO 639-1 / BCP-47 Sprachteil, lower-case
    Optional<String> getUiLanguage();      // BCP-47, z. B. "de-DE" (Brave ui_lang)
    Optional<GeoLocation> getLocation();   // Freitext ("Berlin,Germany") und/oder lat/lon, Provider-Codes über Extensions
}
```

Heutige Provider-Konfiguration (Brave `goggles`, `extraSnippets`, DataForSEO `depth`, Bright Data `data_format` …) bleibt **Provider-Konfiguration** (WP2/WP5). Nur was pro Suche variieren soll, gehört über `getProviderOptions()` in den Request.

### 4.4 SearchResponse

```java
public final class SearchResponse {
    SearchData getData();                          // Pflicht, darf leer sein
    Optional<SearchMetadata> getMetadata();
    Optional<SearchAnalysis> getAnalysis();
    SearchResponse withAnalysis(SearchAnalysis a); // neue Instanz, Data/Metadata unverändert
    SearchResponse withoutRawPayload();            // für Logging/Persistenz
    static Builder builder(SearchData data);
}
```

**Regel:** Metadata ist im Typ optional (Testdaten, Deserialisierung, Teilergebnisse), aber der Provider-Vertrag (WP2) soll vorschreiben, dass jeder Provider mindestens `provider`, `engine` und `query` setzt. Analysis wird nie von Providern befüllt (D8).

### 4.5 SearchData

```java
public final class SearchData {
    List<SearchResultItem> getResults();                    // in Seitenreihenfolge
    List<SearchResultItem> getResults(ResultType type);
    List<SearchResultItem> getOrganicResults();             // Komfort, == getResults(ORGANIC)
    List<SerpFeature> getFeatures();
    <F extends SerpFeature> List<F> getFeatures(Class<F> type);
    Optional<SearchElement> findElement(ElementId id);
    boolean isEmpty();
    static SearchData empty();
    static Builder builder();
}

public interface SearchElement {        // gemeinsame Basis für Item und Feature
    ElementId getId();
    Optional<SerpPosition> getPosition();
    Optional<ResultProvenance> getProvenance();
    Extensions getExtensions();
}
```

### 4.6 SearchResultItem

```java
public final class SearchResultItem implements SearchElement {
    ElementId getId();
    ResultType getType();                         // ORGANIC, PAID, NEWS, VIDEO, IMAGE, DISCUSSION, LOCAL, SHOPPING, …
    String getUrl();                              // Pflicht
    Optional<String> getTitle();
    Optional<String> getSnippet();                // Klartext, ohne Markup
    List<String> getAdditionalSnippets();         // Brave extra_snippets, DataForSEO pre/extended_snippet
    List<TextRange> getHighlights();              // optionale Hervorhebungen im Snippet (statt <strong>)
    Optional<String> getDisplayUrl();             // Bright Data display_link, DataForSEO breadcrumb
    Optional<String> getDomain();                 // vom Mapper aus URL abgeleitet, falls nicht geliefert
    Optional<String> getSiteName();               // Brave profile.name, DataForSEO website_name
    Optional<PublishedDate> getPublishedDate();
    Optional<String> getLanguage();
    Optional<MediaInfo> getThumbnail();           // url, width?, height?
    Optional<Rating> getRating();                 // value, max?, votes?
    List<SiteLink> getSiteLinks();                // Brave deep_results, DataForSEO links, Bright Data extensions
    Optional<SerpPosition> getPosition();
    Optional<ResultProvenance> getProvenance();
    Extensions getExtensions();
    static Builder builder(ElementId id, ResultType type, String url);
}

public final class SerpPosition {
    OptionalInt getRankInGroup();      // 1-basiert, innerhalb des Typs/Blocks (Brave Index+1, Bright Data rank, DataForSEO rank_group)
    OptionalInt getRankAbsolute();     // 1-basiert, über die ganze SERP (Bright Data global_rank, DataForSEO rank_absolute)
    OptionalInt getPage();             // 1-basiert
    SerpArea getArea();                // MAIN (Default), TOP, SIDEBAR, BOTTOM
}

public final class PublishedDate {
    Optional<Instant> getInstant();    // nur wenn sicher parsebar (ISO, Unix-Timestamp)
    Optional<String> getDisplayText(); // "vor 2 Tagen", "Mar 3, 2025" – nie raten
}

public final class ResultProvenance {   // nur gesetzt, wenn Elemente mehrerer Quellen gemischt werden
    ProviderId getProvider();
    SearchEngineId getEngine();
    Optional<ElementId> getOriginalId();
    Optional<SerpPosition> getOriginalPosition();
}
```

### 4.7 SerpFeature (D4)

```java
public interface SerpFeature extends SearchElement {
    SerpFeatureType getType();
    <R> R accept(SerpFeatureVisitor<R> visitor);
}

public interface SerpFeatureVisitor<R> {     // Java-8-Defaults: neue Feature-Klassen brechen bestehende Visitoren nicht
    default R visitKnowledgePanel(KnowledgePanelFeature f)   { return visitOther(f); }
    default R visitGeneratedAnswer(GeneratedAnswerFeature f) { return visitOther(f); }
    default R visitFeaturedSnippet(FeaturedSnippetFeature f) { return visitOther(f); }
    default R visitQuestionAnswer(QuestionAnswerFeature f)   { return visitOther(f); }
    default R visitRelatedQueries(RelatedQueriesFeature f)   { return visitOther(f); }
    default R visitResultGroup(ResultGroupFeature f)         { return visitOther(f); }
    default R visitAnswerBox(AnswerBoxFeature f)             { return visitOther(f); }
    R visitOther(SerpFeature f);              // inkl. GenericSerpFeature
}
```

| Klasse | Typ-Code(s) | Inhalt | Quellen |
|---|---|---|---|
| `KnowledgePanelFeature` | `knowledge_panel` | `title`, `subtitle?`, `description?`, `descriptionSource?` (`SourceReference`), `List<Fact>` (key, values mit optionalem Link), `List<MediaInfo> images`, `websiteUrl?`, `List<SiteLink> profiles` | Brave `infobox`, Bright Data `knowledge`, DataForSEO `knowledge_graph` |
| `GeneratedAnswerFeature` | `ai_overview`, `ai_mode_answer` | `text` (Klartext), `markdown?`, `List<AnswerSegment>` (Text + Zitat-Indizes), `List<SourceReference> references`, `List<String> followUpSuggestions`, `complete` (bool, für asynchron nachgeladene AI Overviews) | Bright Data/DataForSEO `ai_overview`, Google AI Mode (WP3), Brave Summarizer |
| `FeaturedSnippetFeature` | `featured_snippet` | `text`, `SourceReference source` | DataForSEO `featured_snippet`, Google-Browser |
| `QuestionAnswerFeature` | `people_also_ask`, `faq` | `List<QuestionAnswer>` (question, answer?, source?) | Brave `faq`, Bright Data/DataForSEO `people_also_ask` |
| `RelatedQueriesFeature` | `related_searches` | `List<RelatedQuery>` (text, url?) | Bright Data `related`, DataForSEO `related_searches` |
| `ResultGroupFeature` | `top_stories`, `local_pack`, `video_carousel`, `images`, `discussions` … | `title?`, `List<ElementId> items` → Verweise auf `SearchResultItem`s in `results` | Brave `news/videos/discussions/locations`, Bright Data `top_stories/snack_pack/videos`, DataForSEO `top_stories/local_pack/video/images` |
| `AnswerBoxFeature` | `answer_box`, `calculator`, `weather`, … | `text`, `source?` | DataForSEO `answer_box`, Google |
| `GenericSerpFeature` | beliebig | `title?`, `StructuredValue content` | alles nicht Gemappte (z. B. `shopping`, `twitter`, `recipes`, `events`) |

```java
public final class SourceReference {   // Zitat/Quelle innerhalb eines Features; kein eigenständiger Treffer
    String getUrl(); Optional<String> getTitle(); Optional<String> getSnippet();
    Optional<String> getDomain(); Optional<String> getSourceName();
    Optional<ElementId> getMatchingResult();   // falls dieselbe URL auch als Item in results steht
}
```

**Designregel Gruppen:** Treffer innerhalb einer Gruppe (Top Stories, Local Pack, Video-Karussell) werden als normale `SearchResultItem` mit passendem `ResultType` in `results` abgelegt; das Feature verweist nur per `ElementId` darauf. So bleibt `getResults(NEWS)` vollständig und es gibt keine doppelte Datenhaltung. Zitate in KI-Antworten sind dagegen `SourceReference`s, weil sie keine eigenständigen SERP-Treffer sind.

### 4.8 SearchMetadata

```java
public final class SearchMetadata {
    ProviderId getProvider();                         // Pflicht
    SearchEngineId getEngine();                       // Pflicht
    SearchVertical getVertical();                     // Default WEB
    QueryInfo getQuery();                             // Pflicht
    Optional<SearchLocale> getLocale();               // effektiv verwendet (Echo vom Provider, sonst aus Request)
    Optional<DeviceType> getDevice();
    Optional<PageInfo> getPage();
    Optional<ExecutionInfo> getExecution();
    Optional<ProviderUsage> getUsage();
    Optional<ConversationContext> getConversation();  // nur bei Dialogsuche (D10)
    List<SearchWarning> getWarnings();                // z. B. "partial result", "ai overview not loaded", Composite-Teilausfälle
    Optional<RawPayload> getRawPayload();
    Extensions getExtensions();
}

public final class QueryInfo {
    String getOriginalQuery();
    Optional<String> getEffectiveQuery();         // Brave query.altered, DataForSEO spell.keyword
    Optional<SpellCorrection> getSpellCorrection(); // type: SHOWING_RESULTS_FOR, DID_YOU_MEAN, …
}

public final class PageInfo {
    int getOffset(); int getRequestedCount();
    OptionalInt getPageNumber(); Optional<Boolean> getHasMore();   // Brave more_results_available, Bright Data pagination
    OptionalLong getTotalResultsEstimate();                        // Bright Data results_cnt, DataForSEO se_results_count
}

public final class ExecutionInfo {
    Optional<Instant> getStartedAt(); Optional<Duration> getDuration();
    Optional<Duration> getProviderReportedDuration();   // DataForSEO time, Bright Data search_time
    Optional<String> getProviderRequestId();            // DataForSEO task id, Bright Data response_id
    Optional<String> getSourceUrl();                    // DataForSEO check_url, Bright Data input.original_url
    OptionalInt getAttempts();
    Optional<Instant> getProviderTimestamp();           // DataForSEO datetime, Bright Data general.timestamp
}

public final class ProviderUsage {
    Optional<MonetaryAmount> getCost();     // BigDecimal amount + currency ("USD")
    Optional<BigDecimal> getCredits();      // Kontingent-Einheiten, wenn nicht monetär
    Optional<RateLimitInfo> getRateLimit(); // limit, remaining, resetAt (Brave X-RateLimit-*)
}

public final class RawPayload {
    String getContentType();                // application/json, text/html, text/markdown, image/png
    byte[] getBody();                       // Kopie bei Rückgabe; Alternative: Supplier (Q6)
    Optional<Charset> getCharset();
    Optional<Integer> getHttpStatus();
    Map<String, List<String>> getHeaders(); // gefiltert: keine Auth-Header!
}
```

Sicherheitsregel: `RawPayload.getHeaders()` und `Extensions` dürfen **niemals** Auth-Header, API-Keys oder Cookies enthalten; Mapper filtern vor dem Bau (Testkit-Regel, WP5).

### 4.9 SearchAnalysis (D8)

```java
public final class SearchAnalysis {
    AnalysisProvenance getProvenance();          // Pflicht: producer ("askai-intent-classifier"), version, createdAt
    Optional<SearchIntent> getIntent();          // category (INFORMATIONAL, NAVIGATIONAL, TRANSACTIONAL, COMMERCIAL, LOCAL) als offener Wertetyp, confidence 0..1
    List<EntityMention> getEntities();           // name, type?, externalIds (z. B. Wikidata), List<ElementId> mentionedIn, confidence?
    List<Topic> getTopics();                     // label, List<ElementId> members, weight?
    SearchMetrics getMetrics();                  // offene Map<MetricKey, Double> + typisierte Komfort-Getter (resultCount, uniqueDomains, featureCoverage …)
    Extensions getExtensions();
    SearchAnalysis merge(SearchAnalysis other);  // mehrere Analyzer kombinierbar, Provenance je Teilergebnis erhalten (siehe Q2)
}
```

Abgrenzung (verbindlich vorgeschlagen):

| Liegt in **SearchData / Metadata** (vom Provider geliefert) | Liegt in **SearchAnalysis** (vom Konsumenten abgeleitet) |
|---|---|
| Knowledge Panel, AI Overview, AI-Mode-Antwort, PAA, Related Searches | Themencluster über alle Treffer |
| `spell.keyword`, `query.altered` | Erkannte Suchintention |
| Brave `is_navigational`, `is_news_breaking` → `Extensions` in Metadata (`BraveExtensions.QUERY_FLAGS`) | Entitäten und deren Beziehungen |
| Treffer-Ratings, Datum, Domain | Kennzahlen (Domain-Diversität, Feature-Abdeckung) |

### 4.10 Erweiterungsmechanismus (D5)

```java
public final class ExtensionKey<T> {
    public static <T> ExtensionKey<T> of(String namespace, String name, Class<T> type); // namespace = ProviderId-Code oder Modulname
    String getNamespace(); String getName(); Class<T> getType();
}

public final class Extensions {
    static Extensions empty();
    <T> Optional<T> get(ExtensionKey<T> key);
    boolean contains(ExtensionKey<?> key);
    Set<ExtensionKey<?>> keys();
    Optional<StructuredValue> getUnmapped(String namespace);  // Rest-Felder als Baum
    Extensions with(ExtensionKey<T> key, T value);            // neue Instanz
    static Builder builder();
}

public abstract class StructuredValue {   // JSON-artiger Baum ohne JSON-Bibliothek
    // Unterklassen: ObjectValue (LinkedHashMap<String,StructuredValue>), ArrayValue, StringValue,
    //               NumberValue (BigDecimal), BooleanValue, NullValue
    Optional<StructuredValue> path(String... segments);
    Optional<String> asText(); ...
}
```

Regeln:

1. **Typisierte Keys** werden vom jeweiligen Provider-Modul veröffentlicht (z. B. `com.aresstack.serp4j.provider.brave.BraveExtensions.PROFILE`). Konsumenten, die den Provider kennen, lesen typsicher; alle anderen ignorieren die Extensions.
2. Werte von Extensions müssen selbst **unveränderlich** sein (Domänenobjekte, Strings, `StructuredValue`).
3. **Unmapped-Rest**: Felder, die der Mapper nicht in Core-Felder oder typisierte Keys überträgt, *dürfen* als `StructuredValue` unter `getUnmapped(namespace)` mitgegeben werden (per Provider-Konfiguration abschaltbar), damit nichts verloren geht, ohne dass `RawPayload` gehalten werden muss.
4. **Hochstufungsregel:** Wird ein Extension-Feld von ≥ 2 Providern geliefert und von Konsumenten gebraucht, wandert es in einer Minor-Version als Core-Feld ins Modell (Extension bleibt eine Version lang parallel, deprecated).
5. Extensions existieren auf `SearchRequest` (Provider-Optionen), `SearchMetadata`, jedem `SearchElement` und `SearchAnalysis`.

### 4.11 Dialogsuche / Stateful Sessions (Datenanteil von D10)

Ports (`ConversationalSearchProvider`, `SearchSession`, `SearchConversationRequest`) definiert WP2. WP1 schlägt nur die Daten vor, die in den Responses landen:

```java
public final class ConversationContext {        // in SearchMetadata
    SearchSessionId getSessionId();             // opaker Wert, von Serp4J vergeben (nicht Google-intern)
    int getTurnIndex();                         // 0 = Initialsuche
    Optional<String> getProviderConversationId(); // falls der Provider eine eigene ID liefert
    Optional<ElementId> getAnswerElement();     // Verweis auf das GeneratedAnswerFeature dieses Turns
}

public final class ConversationTurn {          // Verlauf, z. B. für SearchSession.getHistory() (WP2)
    int getIndex(); String getPrompt(); SearchResponse getResponse(); Instant getCreatedAt();
}
```

Ablauf aus Datensicht: Initialsuche und jede Folgefrage liefern je eine vollständige `SearchResponse`; die KI-Antwort ist ein `GeneratedAnswerFeature` mit Zitaten (`SourceReference`), Folgevorschläge stehen in `followUpSuggestions`. Damit kann jeder Konsument, der nur `SearchResponse` versteht, auch AI-Mode-Ergebnisse lesen, ohne die Session-API zu kennen.

### 4.12 Fehler

Fehler sind kein Teil von `SearchResponse` (AskAI nutzt `WebSearchException`). Vorschlag für WP2: Hierarchie `SearchException` mit `SearchErrorCategory` (AUTHENTICATION, QUOTA_EXCEEDED, RATE_LIMITED, INVALID_REQUEST, PROVIDER_ERROR, TIMEOUT, PARSE_ERROR, BLOCKED/CAPTCHA, UNSUPPORTED) sowie `ProviderId` und optional `providerErrorCode` (DataForSEO `status_code`). Teilfehler (Composite, AI Overview nicht geladen) landen als `SearchWarning` in den Metadaten.

---

## 5. Feldzuordnung je Provider (Mapper-Vorgaben)

### 5.1 Brave → Serp4J

| Brave | Ziel |
|---|---|
| (fix) | `metadata.provider = brave`, `engine = brave` |
| `query.original` / `query.altered` | `QueryInfo.originalQuery` / `effectiveQuery` |
| `query.country`, Request-`ui_lang`/`search_lang` | `metadata.locale` |
| `query.more_results_available` | `PageInfo.hasMore` |
| `query.is_navigational`, `is_news_breaking`, `bad_results` | `metadata.extensions[BraveExtensions.QUERY_FLAGS]` |
| `mixed.main/top/side` | berechnet `SerpPosition.area` und `rankAbsolute` aller Elemente |
| `web.results[i]` | `SearchResultItem(type=ORGANIC)`; `rankInGroup = i+1` |
| `.title`, `.url`, `.description` | `title`, `url`, `snippet` (Markup entfernt, `<strong>` → `highlights`) |
| `.extra_snippets[]` | `additionalSnippets` |
| `.meta_url.hostname`, `.profile.name` | `domain`, `siteName` |
| `.page_age` / `.age` | `PublishedDate.instant` / `.displayText` |
| `.thumbnail.src`, `.language` | `thumbnail`, `language` |
| `.deep_results.buttons/links` | `siteLinks` |
| `.faq`, `.article`, `.product`, `.review`, `.recipe`, `.schemas` | `item.extensions` (typisierte Keys bei Bedarf, sonst unmapped) |
| `news.results[]` / `videos.results[]` / `discussions.results[]` / `locations.results[]` | `SearchResultItem` (NEWS/VIDEO/DISCUSSION/LOCAL) + `ResultGroupFeature` |
| `faq.results[]` | `QuestionAnswerFeature(type=faq)` |
| `infobox.results[0]` | `KnowledgePanelFeature` |
| `summarizer.key` | `metadata.extensions[BraveExtensions.SUMMARIZER_KEY]` (Antwort nur über separaten Call → später `GeneratedAnswerFeature`) |
| Header `X-RateLimit-*` | `ProviderUsage.rateLimit` |

### 5.2 Bright Data → Serp4J

| Bright Data | Ziel |
|---|---|
| (fix) / `general.search_engine` | `provider = bright-data`, `engine` aus Request bzw. `general.search_engine` |
| `general.query`, `language`, `location`, `mobile` | `QueryInfo.originalQuery`, `locale`, `device` |
| `general.results_cnt`, `search_time`, `timestamp` | `PageInfo.totalResultsEstimate`, `ExecutionInfo.providerReportedDuration`, `providerTimestamp` |
| `input.original_url`, `response_id` / `input.request_id` | `ExecutionInfo.sourceUrl`, `providerRequestId` |
| `organic[]` | `SearchResultItem(ORGANIC)`: `link`→`url` (Fallback `url`), `title`, `description`→`snippet` (Fallback `snippet`), `display_link`→`displayUrl`, `rank`→`rankInGroup`, `global_rank`→`rankAbsolute`, `extensions[]`→`siteLinks`, `image`→`thumbnail`, `date`→`PublishedDate.displayText` |
| `top_ads[]` / `bottom_ads[]` | `SearchResultItem(PAID)`, `area = TOP / BOTTOM`, `referral_link` → Extension |
| `knowledge` | `KnowledgePanelFeature` (`facts[]` → `Fact`) |
| `people_also_ask[]` | `QuestionAnswerFeature(people_also_ask)` |
| `related[]` | `RelatedQueriesFeature` |
| `top_stories`, `snack_pack`, `videos`, `images` | Items + `ResultGroupFeature` |
| `ai_overview` | `GeneratedAnswerFeature(ai_overview)` |
| `pagination` | `PageInfo.pageNumber`, `hasMore` |
| `navigation`, `twitter`, `recipes`, sonstige | `GenericSerpFeature` bzw. unmapped |
| `data_format=markdown/screenshot` | kein strukturiertes Mapping; `RawPayload(text/markdown | image/png)` + Warnung |

### 5.3 DataForSEO → Serp4J

| DataForSEO | Ziel |
|---|---|
| `status_code`/`status_message` (Root und Task) ≠ 20000 | `SearchException(PROVIDER_ERROR, providerErrorCode)` (wie heute) |
| `cost` (Root), `tasks[].cost` | `ProviderUsage.cost` (USD) |
| `tasks[].id`, `tasks[].time` | `ExecutionInfo.providerRequestId`, `providerReportedDuration` |
| `tasks[].data.se`, `keyword`, `location_code`, `language_code`, `device`, `os` | `engine`, `QueryInfo.originalQuery`, `locale` (location_code als Extension), `device`, `os` → Extension |
| `result[0].spell` | `QueryInfo.spellCorrection` |
| `result[0].check_url`, `datetime`, `se_results_count` | `ExecutionInfo.sourceUrl`, `providerTimestamp`, `PageInfo.totalResultsEstimate` |
| `result[0].item_types` | `metadata.extensions[DataForSeoExtensions.ITEM_TYPES]` |
| `items[type=organic]` | `SearchResultItem(ORGANIC)`: `title`, `url`, `description`→`snippet`, `pre_snippet`/`extended_snippet`→`additionalSnippets`, `domain`, `breadcrumb`→`displayUrl`, `website_name`→`siteName`, `timestamp`→`PublishedDate`, `rating`, `links`→`siteLinks`, `highlighted`→`highlights` (best effort) |
| alle Items: `rank_group`, `rank_absolute`, `page`, `position` | `SerpPosition.rankInGroup`, `rankAbsolute`, `page`, `area` (`right` → SIDEBAR) |
| `items[type=paid]` | `SearchResultItem(PAID)` |
| `featured_snippet` | `FeaturedSnippetFeature` |
| `knowledge_graph` | `KnowledgePanelFeature` |
| `people_also_ask`, `related_searches` | `QuestionAnswerFeature`, `RelatedQueriesFeature` |
| `top_stories`, `local_pack`, `video`, `images` | Items + `ResultGroupFeature` |
| `answer_box` | `AnswerBoxFeature` |
| `ai_overview` (`markdown`, `items[]`, `references[]`, `asynchronous_ai_overview`) | `GeneratedAnswerFeature` (`complete = !asynchronous_ai_overview`) |
| `xpath`, `is_*`-Flags, alle übrigen Typen | Extensions bzw. `GenericSerpFeature` |

### 5.4 AskAI `WebSearchHit` ↔ Serp4J (Kompatibilitätsbrücke, Details WP5)

| AskAI | Serp4J |
|---|---|
| `WebSearchResult.providerId` / `searchEngine` | `metadata.provider` / `metadata.engine` (bei Composite: `item.provenance`) |
| `WebSearchResult.hits` | `data.getOrganicResults()` |
| `WebSearchResult.rawResponse` | `metadata.rawPayload` (UTF-8-Body) – nur wenn `RawPayloadPolicy.ALWAYS` |
| `WebSearchHit.rank` | `position.rankInGroup` (Fallback: Listenindex+1) |
| `WebSearchHit.title` / `url` / `snippet` | `title.orElse("")` / `url` / `snippet.orElse(first(additionalSnippets))` |
| `SearchHit.domain`, `absoluteRank`, `publishedAt` (runtime) | `domain`, `position.rankAbsolute`, `publishedDate` – künftig direkt aus Serp4J |

---

## 6. Risiken

| Risiko | Bewertung | Gegenmaßnahme |
|---|---|---|
| Modell wird zu groß / zu „SERP-SEO-lastig“ für einfache Konsumenten. | mittel | Komfortzugriffe (`getOrganicResults()`), alle Zusatzfelder optional; Mindestvertrag für Provider nur url + metadata.provider/engine/query. |
| Provider-JSON ändert sich (insb. Bright Data/DataForSEO neue Feature-Typen, AI Overview). | hoch | `GenericSerpFeature` + unmapped `StructuredValue`; Mapper tolerant (unbekannte Typen nie als Fehler). |
| Datums-/Zeitangaben nicht zuverlässig parsebar („2 days ago“). | hoch | `PublishedDate` trennt `instant` und `displayText`; nie relativ zur Abrufzeit raten. |
| `RawPayload` erzeugt Speicherlast und Leak-Gefahr (Header). | mittel | Default `NONE`, Header-Filter, `withoutRawPayload()`. |
| `Optional`-Getter in Java 8: Performance/Serialisierung. | gering | Nur im Getter, nicht als Feld; Serialisierung über eigenes Modul. |
| `ElementId`-Stabilität: IDs nur response-lokal; Composite muss umnummerieren. | mittel | Composite vergibt neue IDs und hält Original in `ResultProvenance.originalId`. |
| Unterschiedliche Rang-Semantik wird von Konsumenten falsch verglichen. | mittel | Javadoc-Vertrag zu `rankInGroup` vs. `rankAbsolute`; Composite sortiert nie über Ränge verschiedener Provider ohne eigene Strategie. |
| Abgrenzung Data ↔ Analysis verschwimmt bei KI-Features (z. B. Brave Summarizer, Provider-„intent“-Felder). | mittel | Regel D8 + Tabelle §4.9; im Review jedes neuen Felds prüfen. |
| Lizenz/Nutzungsbedingungen bei Speichern von SERP-Rohdaten. | gering–mittel | Nicht WP1-Kern; Hinweis an Integration (RawPayload-Policy dokumentieren). |

---

## 7. Offene Fragen für die Integration

| # | Frage | WP1-Empfehlung | Betrifft |
|---|---|---|---|
| Q1 | Gehört `SearchAnalysis` in `serp4j-core` oder in ein eigenes Modul? | **Typen im Core** (kleine Wertobjekte, damit `SearchResponse` sie tragen kann, ohne zyklische Module); **Analyzer-Implementierungen** in eigenen Modulen (`serp4j-analysis-*`). Alternative: `SearchResponse` ohne Analysis + Wrapper `AnalyzedSearchResponse` im Analysis-Modul – sauberer, aber bricht Angelos Vorgabe des dreiteiligen Modells. | WP2, WP4 |
| Q2 | Mehrere Analyzer: eine `SearchAnalysis` mit gemischter Provenance oder `List<SearchAnalysis>`? | Eine Instanz mit `merge()`; Provenance auf Teilergebnis-Ebene (Intent/Entity/Topic tragen optional eigene Provenance). | WP2 |
| Q3 | Google AI Mode: eigene `SearchEngineId` oder `SearchVertical.AI_MODE`? | **Vertical**, Engine bleibt `google`. | WP2, WP3 |
| Q4 | Werden Anzeigen (`PAID`) standardmäßig gemappt oder per Request-Option? | Mappen, aber per `ResultType` klar getrennt; `getOrganicResults()` enthält sie nie. | WP5 |
| Q5 | Sollen Domänenobjekte `java.io.Serializable` sein (z. B. für Caches, Swing-Transfer in AskAI)? | Nein; JSON-Modul `serp4j-json` mit stabilem Schema (versioniert, `formatVersion`). | WP4 |
| Q6 | `RawPayload.body` als `byte[]` (kopiert) oder lazily via `Supplier`? | `byte[]` mit Kopie; groß nur bei `ALWAYS`. | WP2 |
| Q7 | Wird DataForSEOs Google-AI-Mode-Endpunkt bzw. Bright Datas AI-Mode-Scraper als zusätzlicher, browserloser Weg zu AI Mode genutzt? | Datenmodell unterstützt es bereits (D10); Entscheidung über Priorität bei Integration. | WP2, WP3 |
| Q8 | Package-Namen `com.aresstack.serp4j.model.*` vs. `…core.domain.*` | Mit WP2/WP4 vereinheitlichen; WP1 hat keine Präferenz außer: Model-Package ohne Abhängigkeit zu Ports. | WP2, WP4 |
| Q9 | Echte Response-Fixtures: Wer zieht sie (mit Keys) und anonymisiert sie? Die Felder in §2 stammen für nicht gemappte Bereiche aus der API-Doku und sind nicht gegen Live-Responses geprüft. | Teil von Phase E (Testkit), Fixtures je Provider und Engine vor Implementierung der Mapper. | WP5 |
| Q10 | Composite-Merge: Deduplizierung per normalisierter URL – Normalisierungsregel im Core (`UrlNormalizer`) oder im Composite? | Im Composite (Application-Schicht); Core bleibt reine Daten. | WP2 |
| Q11 | Sollen offene Wertetypen (`ProviderId` …) eine zentrale Registry haben (Konflikte zweier Plugins mit gleichem Code)? | Nein für IDs (nur Konvention „Namespace = Artefaktname“); Registry nur für Provider-Instanzen (WP2). | WP2 |
| Q12 | Brave Summarizer / AI-Antworten bei Brave: als separater Provider-Call oder automatisch nachgeladen? | Nicht automatisch; über Conversational-/Capability-Mechanik (WP2). | WP2 |

---

## 8. Nächste Schritte nach Freigabe (nur Vorschlag)

1. Integration entscheidet Q1, Q3, Q5, Q8 → ADR „Unified Data Model“.
2. Phase A: Implementierung der Wertobjekte (`model.*`, `model.ext`) mit Unit-Tests für Validierung, Unveränderlichkeit und `equals`.
3. Phase E parallel: Fixture-Sammlung und Golden-File-Tests je Provider (Q9).
4. Phase B–D: Mapper nach den Tabellen in §5, jeweils zuerst organisch + Metadata, dann Features.
