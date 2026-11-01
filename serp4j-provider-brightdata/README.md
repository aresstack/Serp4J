# serp4j-provider-brightdata

Bright Data SERP API als `SearchProvider` (Java 8). Grundlage: `serp4j-brightdata-api-referenceV2.md` (2026-11-01)
und eine echte Antwort (`src/test/resources/fixtures/brightdata/google-web-pizza.json`).

```java
SearchProvider brightData = BrightDataSearchProvider.builder(ProviderId.of("brightdata"))
        .transport(httpTransport)                       // geliehen, z. B. AhcHttpTransport
        .credentials(credentials)                       // Schlüssel "brightdata/apiKey"
        .zone("serp_api1")                              // Zone aus dem Bright-Data-Konto
        .build();

SearchResponse response = brightData.search(SearchRequest.builder("pizza")
        .locale(SearchLocale.of("DE", "de"))
        .providerOptions(BrightDataOptions.builder(ProviderId.of("brightdata")).proxyCountry("DE").build())
        .build(), SearchContext.defaults()).join();
```

| Aspekt | Umsetzung |
|---|---|
| Endpoint | `POST /request`, Body `{zone, url, format: "json", data_format: "parsed"[, country]}` |
| Ziel | nur Google Web (andere Engines im Export nicht dokumentiert) |
| Auth | `Authorization: Bearer …` als sensibler Header; nie in URL, Body, Log oder Exception |
| Antwort | Wrapper `status_code`/`headers`/`body`; `body` (JSON-String) → Mapping `brightdata-google-web` v1 |
| Ränge | `rank` = Position im Bereich (`rankInGroup`), `global_rank` = Position auf der SERP (`rankAbsolute`) |
| Fehler | 400 → INVALID_REQUEST, 401/403 → AUTHENTICATION, 429 → RATE_LIMITED (+Retry-After), 5xx → UNAVAILABLE; Ziel-Status 403/429 → BLOCKED |
| Factory | `type=brightdata`, Properties `zone` (Pflicht), `apiUrl`, `googleSearchUrl`, `requestTimeout` (ISO-8601) |

Offen: Async-API (`/serp/req`, `/serp/get_result`), `QuestionAnswerFeature` für `people_also_ask.answers`,
weitere echte Fixtures (Pagination, Mobile, andere Länder).
