# serp4j-provider-dataforseo

Cloud-Adapter für die [DataForSEO SERP API v3](https://docs.dataforseo.com/v3/serp/overview/) (Java 8).
Implementiert den Core-Port `SearchProvider`; HTTP läuft über einen injizierten `HttpTransport`
(z. B. `AhcHttpTransport` aus `serp4j-http-ahc`), der dem Aufrufer gehört (ADR-017).

```java
HttpTransport http = AhcHttpTransport.create();          // gehört dem Host
CredentialProvider credentials = EnvironmentCredentialProvider.systemEnvironment(); // SERP4J_DATAFORSEO_LOGIN …
try (DataForSeoSearchProvider dataForSeo = DataForSeoSearchProvider.builder(http, credentials).build()) {
    SearchResponse response = dataForSeo.search(
            SearchRequest.builder("albert einstein").locale(SearchLocale.of("DE", "de")).limit(10).build(),
            SearchContext.defaults()).join();
}
http.close();
```

## Umfang

| Thema | Stand |
|---|---|
| Engines | Google, Bing (`SearchTarget.web(...)`, Default Google) |
| Endpunkte | `live/advanced` (Default), `live/regular` (`DataForSeoFunction`, je Provider oder je Request) |
| Location | `DataForSeoOptions.locationCode/locationName` → Koordinaten oder Name aus `SearchLocale.location` → Ländername aus `countryCode` → Default 2840 (USA) |
| Sprache | `SearchLocale.languageCode` → `DataForSeoOptions.languageName` → Default `en` |
| Seiten | `depth` = offset + limit (Live max. 200); Ergebnisse vor dem Offset werden verworfen |
| Kosten | `cost` des Tasks → `ProviderUsage.cost` (USD) |
| Nicht enthalten | Standard-Ablauf `task_post`/`tasks_ready`/`task_get`, HTML, Locations/Languages-Lookup, AI Mode (Paket D2) |

## Zugangsdaten

`CredentialKey`s `<providerId>.login` und `<providerId>.password` (über die Factory mit `credential.login` /
`credential.password` umbenennbar). Sie werden je Request aufgelöst und nur im Header
`Authorization: Basic …` gesendet, nie in URL, Body, Fehlermeldung oder `toString()`.

## Statuscodes

DataForSEO antwortet meist mit HTTP 200 und meldet Fehler im Body (`status_code` der Hülle und des Tasks).

| Code | Ergebnis |
|---|---|
| 20000 | Erfolg |
| 40102 | leeres Ergebnis, kein Fehler |
| 40106 | Teilergebnis: `SearchMetadata.isComplete() == false`, Warnung `partial_results` |
| 40100 | `AUTHENTICATION` |
| 40104, 40207 | `CONFIGURATION` |
| 40200, 40203, 40210 | `QUOTA_EXHAUSTED` |
| 40202, 40209 | `RATE_LIMITED` (retryable) |
| 40101, 40103, 50000, 50301, sonst 5xxxx | `UNAVAILABLE` (retryable) |
| 50401 | `TIMEOUT` (retryable) |
| übrige 4xxxx | `INVALID_REQUEST` |

## Fixtures

Alle Fixtures unter `src/test/resources/fixtures/dataforseo/` sind **synthetisch** (`provenance=synthetic`), nach
der API-Referenz geschrieben. Sobald eine echte, anonymisierte Antwort vorliegt, wird sie als `recorded` ergänzt und
die Golden-Files werden neu erzeugt (`SERP4J_UPDATE_GOLDEN=1`). Live-Tests: `./gradlew :serp4j-provider-dataforseo:liveTest -Pserp4j.live=true`
(noch keine vorhanden).
