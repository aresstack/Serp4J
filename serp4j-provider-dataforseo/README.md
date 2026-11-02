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

## Google-Weiterleitungs-URLs (`/goto`)

Google liefert DataForSEO organische Treffer zunehmend nur als `https://www.google.com/goto?url=<token>`. Bei
Google-Suchen löst der Provider diese URLs nach dem (netzwerkfreien) Mapping auf:

1. DataForSEO-Antwort wie bisher mit `DataForSeoResponseMapper` mappen.
2. Ergebnis-URLs strukturiert per `URI` prüfen: nur `https`, Host genau `google.com`/`www.google.com`, Pfad `/goto`,
   nicht leerer Parameter `url`.
3. Jede verschiedene URL einmal per `GET` ohne Redirect-Folgen abrufen (`HttpRequest.followRedirects(false)`),
   höchstens vier gleichzeitig, je Request ≤ 3 s (`urlResolveTimeout`) und nie über die Deadline des
   `SearchContext` hinaus.
4. Nur bei `301/302/303/307/308` mit absolutem `http(s)`-`Location` die Ziel-URL übernehmen: `url` wird ersetzt,
   `domain` aus dem Host abgeleitet; Titel, Snippet, Position, Provenance, `displayUrl`, `siteName` bleiben. Die
   Original-URL steht in der Extension `serp4j:original_url`, der Status in `serp4j:url_resolution`
   (`resolved` bzw. Fehlergrund). Die Orchestrierung ist der providerneutrale `ResultUrlEnricher` aus
   `serp4j-http-api`, den auch SerpApi nutzt.
5. Sonst bleibt die URL unverändert (Status `serp4j:url_resolution` = Grund, z. B. `timeout`); alle Fehlschläge einer Suche ergeben eine Warnung
   `url_resolution_failed` mit Zählung je Grund. Es wird nie über `breadcrumb`, `domain` oder `website_name`
   geraten.

Die Zielseite wird nicht abgerufen; der Request an Google trägt weder DataForSEO-Zugangsdaten noch Cookies oder
Referrer. Cancellation und `close()` brechen laufende Auflösungen ab, der geliehene `HttpTransport` bleibt offen.

**Latenz:** jede Google-Suche mit `/goto`-Treffern kostet bis zu N zusätzliche Requests an Google (N = Zahl
verschiedener `/goto`-URLs, bei 10 Treffern typischerweise 7–8), also im ungünstigsten Fall bis zu
⌈N/4⌉ × 3 s zusätzliche Laufzeit; im Normalfall (≈ 80 ms je Redirect) unter einer halben Sekunde.
Abschalten: `builder.resolveGoogleGotoUrls(false)` bzw. Factory-Property `resolveGoogleGotoUrls=false` –
dann keine zusätzlichen Requests.

**Folgeaufgabe:** `/goto`-URLs innerhalb von SERP-Features werden noch nicht aufgelöst.

Live-Prüfung ohne DataForSEO-Guthaben: `SERP4J_GOOGLE_GOTO_URL=<aktuelle /goto-URL>
[SERP4J_GOOGLE_GOTO_TARGET=<erwartetes Ziel>] ./gradlew :serp4j-provider-dataforseo:liveTest -Pserp4j.live=true`.

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
