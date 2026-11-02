package com.aresstack.serp4j.provider.serpapi;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.GeoLocation;
import com.aresstack.serp4j.core.model.ProviderRequestOptions;
import com.aresstack.serp4j.core.model.SafeSearch;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.TimeRange;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.model.metadata.SpellCorrection;
import com.aresstack.serp4j.core.port.CancellationSource;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.credential.CredentialKey;
import com.aresstack.serp4j.core.port.credential.CredentialProvider;
import com.aresstack.serp4j.core.port.credential.SecretValue;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class SerpApiSearchProviderTest {

    private static final String KEY = "unit-test-serpapi-key-0042";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
    private static final SearchTarget BING = SearchTarget.of(SearchEngineId.BING, SearchVertical.WEB);

    private final FakeHttpTransport transport = FakeHttpTransport.create();
    private SerpApiSearchProvider provider = SerpApiSearchProvider.builder().transport(transport).apiKey(KEY).build();

    @AfterEach
    void tearDown() {
        provider.close();
        transport.close();
    }

    private void respondWith(String fixture) {
        transport.onAnyRequest(FakeHttpStep.respond(Fixtures.load("serpapi", fixture).toHttpResponse()));
    }

    private SearchResponse search(SearchRequest request) {
        try {
            return provider.search(request, SearchContext.defaults()).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("search failed", e);
        }
    }

    private SearchProviderException searchFails(SearchRequest request) {
        CompletableFuture<SearchResponse> future = provider.search(request, SearchContext.defaults());
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(SearchProviderException.class);
            return (SearchProviderException) e.getCause();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return fail("expected a failure");
    }

    // ---- request ---------------------------------------------------------------------------------------------

    @Test
    void buildsGoogleRequestWithKeyOnlyAsMaskedQueryParameter() {
        respondWith("google-web-synthetic.json");
        search(SearchRequest.builder("serp4j  java")
                .locale(SearchLocale.builder().countryCode("DE").languageCode("de")
                        .location(GeoLocation.named("Berlin,Germany")).build())
                .limit(20).offset(40)
                .safeSearch(SafeSearch.STRICT)
                .timeRange(TimeRange.pastWeek())
                .build());

        HttpRequest request = transport.getLastRequest();
        assertThat(request.getUrl()).startsWith("https://serpapi.com/search.json?")
                .contains("engine=google").contains("q=serp4j")
                .contains("gl=de").contains("hl=de").contains("location=Berlin%2CGermany")
                .contains("num=20").contains("start=40")
                .contains("safe=active").contains("tbs=qdr%3Aw")
                .contains("api_key=" + KEY)
                .doesNotContain("no_cache");
        assertThat(request.getRedactedUrl()).doesNotContain(KEY).contains("api_key=");
        assertThat(request.toString()).doesNotContain(KEY);
        assertThat(request.getHeaders().asMap().toString()).doesNotContain(KEY);
        assertEquals(Optional.of("application/json"), request.getHeaders().getFirst("Accept"));
    }

    @Test
    void buildsBingRequest() {
        provider.close();
        provider = SerpApiSearchProvider.builder().transport(transport).apiKey(KEY).clock(CLOCK).build();
        respondWith("bing-web-synthetic.json");
        search(SearchRequest.builder("serp4j").target(BING)
                .locale(SearchLocale.builder().countryCode("de").languageCode("de").build())
                .limit(10).offset(10)
                .safeSearch(SafeSearch.OFF)
                .timeRange(TimeRange.between(Instant.parse("2026-10-01T00:00:00Z"),
                        Instant.parse("2026-10-31T00:00:00Z")))
                .build());

        String url = transport.getLastRequest().getUrl();
        assertThat(url).contains("engine=bing").contains("cc=DE").contains("mkt=de-DE")
                .contains("count=10").contains("first=11").contains("safeSearch=Off")
                .contains("filters=ex1%3A%22ez5_20362_20392%22")
                .doesNotContain("num=").doesNotContain("gl=");
    }

    @Test
    void bingCapsTheCountAtFiftyWithWarning() {
        respondWith("bing-web-synthetic.json");
        SearchResponse response = search(SearchRequest.builder("q").target(BING).limit(80).build());
        assertThat(transport.getLastRequest().getUrl()).contains("count=50");
        assertThat(response.getMetadata().get().getWarnings()).extracting(SearchWarning::getCode)
                .contains(SearchWarning.FEATURE_IGNORED);
    }

    @Test
    void bingCountAboveFiftyIsUnsupportedWhenRequired() {
        assertEquals(SearchErrorCategory.UNSUPPORTED, searchFails(SearchRequest.builder("q").target(BING).limit(80)
                .require(SearchFeature.MAX_RESULTS).build()).getCategory());
        assertEquals(0, transport.getRequestCount());
    }

    @Test
    void unsupportedEngineIsRejectedWithoutHttpCall() {
        SearchProviderException failure = searchFails(SearchRequest.builder("q")
                .target(SearchTarget.of(SearchEngineId.BRAVE, SearchVertical.WEB)).build());
        assertEquals(SearchErrorCategory.UNSUPPORTED, failure.getCategory());
        assertEquals(0, transport.getRequestCount());
    }

    @Test
    void usesInstanceDefaultsAndProviderOptions() {
        provider.close();
        provider = SerpApiSearchProvider.builder().transport(transport).apiKey(KEY)
                .options(SerpApiSearchOptions.builder().defaultCountry("AT").defaultLanguage("de")
                        .defaultLocation("Vienna,Austria").googleDomain("google.at").build())
                .build();
        respondWith("google-web-synthetic.json");
        search(SearchRequest.builder("q")
                .providerOptions(SerpApiRequestOptions.builder(SerpApiSearchProvider.DEFAULT_ID)
                        .noCache(true).device(SerpApiRequestOptions.Device.MOBILE).parameter("nfpr", "1").build())
                .build());

        assertThat(transport.getLastRequest().getUrl()).contains("gl=at").contains("hl=de")
                .contains("location=Vienna%2CAustria").contains("google_domain=google.at")
                .contains("no_cache=true").contains("device=mobile").contains("nfpr=1")
                .doesNotContain("start=");
    }

    @Test
    void customGoogleRangeUsesCdrSyntax() {
        respondWith("google-web-synthetic.json");
        search(SearchRequest.builder("q").timeRange(TimeRange.between(Instant.parse("2022-04-01T00:00:00Z"),
                Instant.parse("2022-07-30T00:00:00Z"))).build());
        assertThat(transport.getLastRequest().getUrl())
                .contains("tbs=cdr%3A1%2Ccd_min%3A4%2F1%2F2022%2Ccd_max%3A7%2F30%2F2022");
    }

    @Test
    void coordinatesWithoutNameAreIgnoredWithWarningWhenOptional() {
        respondWith("google-web-synthetic.json");
        SearchResponse response = search(SearchRequest.builder("q")
                .locale(SearchLocale.builder().location(GeoLocation.coordinates(48.2, 16.37)).build()).build());
        assertThat(transport.getLastRequest().getUrl()).doesNotContain("location=");
        assertThat(response.getMetadata().get().getWarnings()).extracting(SearchWarning::getCode)
                .contains(SearchWarning.FEATURE_IGNORED);
    }

    @Test
    void coordinatesWithoutNameAreUnsupportedWhenRequired() {
        assertEquals(SearchErrorCategory.UNSUPPORTED, searchFails(SearchRequest.builder("q")
                .locale(SearchLocale.builder().location(GeoLocation.coordinates(48.2, 16.37)).build())
                .require(SearchFeature.GEO_LOCATION).build()).getCategory());
        assertEquals(0, transport.getRequestCount());
    }

    @Test
    void providerOptionsOfTheWrongTypeAreRejectedBeforeCredentialsAndHttp() {
        AtomicInteger lookups = new AtomicInteger();
        provider.close();
        provider = SerpApiSearchProvider.builder().transport(transport).credentials(key -> {
            lookups.incrementAndGet();
            return Optional.of(SecretValue.of(KEY));
        }).build();
        ProviderRequestOptions foreign = () -> SerpApiSearchProvider.DEFAULT_ID;
        SearchProviderException failure = searchFails(SearchRequest.builder("q").providerOptions(foreign).build());
        assertEquals(SearchErrorCategory.INVALID_REQUEST, failure.getCategory());
        assertThat(failure.getMessage()).contains("SerpApiRequestOptions");
        assertEquals(0, transport.getRequestCount());
        assertEquals(0, lookups.get());
    }

    @Test
    void optionsForOtherProvidersAreIgnored() {
        respondWith("google-web-synthetic.json");
        ProviderRequestOptions other = () -> ProviderId.of("dataforseo");
        search(SearchRequest.builder("q").providerOptions(other).build());
        assertEquals(1, transport.getRequestCount());
    }

    @Test
    void reservedParametersCannotBeOverridden() {
        SerpApiRequestOptions.Builder options = SerpApiRequestOptions.builder(SerpApiSearchProvider.DEFAULT_ID);
        assertThrows(IllegalArgumentException.class, () -> options.parameter("api_key", "x"));
        assertThrows(IllegalArgumentException.class, () -> options.parameter("ENGINE", "yahoo"));
        assertThrows(IllegalArgumentException.class, () -> options.parameter("a&b", "x"));
        assertThrows(IllegalArgumentException.class, () -> options.googleDomain("example.com"));
    }

    @Test
    void secretValueIsClosedAfterUse() {
        SecretValue[] issued = new SecretValue[1];
        provider.close();
        provider = SerpApiSearchProvider.builder().transport(transport).credentials(key -> {
            issued[0] = SecretValue.of(KEY);
            return Optional.of(issued[0]);
        }).build();
        respondWith("google-web-synthetic.json");
        search(SearchRequest.builder("q").build());
        assertThat(issued[0].isCleared()).isTrue();
    }

    @Test
    void credentialsAreResolvedPerSearchFromTheCredentialProvider() {
        provider.close();
        CredentialProvider credentials = key -> key.equals(CredentialKey.of("serpapi.api-key"))
                ? Optional.of(SecretValue.of("rotating-key")) : Optional.<SecretValue>empty();
        provider = SerpApiSearchProvider.builder().transport(transport).credentials(credentials).build();
        respondWith("google-web-synthetic.json");
        search(SearchRequest.builder("q").build());
        assertThat(transport.getLastRequest().getUrl()).contains("api_key=rotating-key");
    }

    @Test
    void missingCredentialIsAConfigurationError() {
        provider.close();
        provider = SerpApiSearchProvider.builder().transport(transport).credentials(CredentialProvider.none()).build();
        SearchProviderException failure = searchFails(SearchRequest.builder("q").build());
        assertEquals(SearchErrorCategory.CONFIGURATION, failure.getCategory());
        assertThat(failure.getMessage()).contains("serpapi.api-key");
        assertEquals(0, transport.getRequestCount());
    }

    @Test
    void cancelledContextSendsNothing() {
        CancellationSource source = new CancellationSource();
        source.cancel();
        SearchContext context = SearchContext.builder().cancellation(source.getSignal()).build();
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> provider.search(SearchRequest.builder("q").build(), context).get(5, TimeUnit.SECONDS));
        assertEquals(SearchErrorCategory.CANCELLED, ((SearchProviderException) e.getCause()).getCategory());
        assertEquals(0, transport.getRequestCount());
    }

    @Test
    void successfulResponseAfterTheDeadlineIsATimeout() {
        transport.onAnyRequest(FakeHttpStep.respond(Fixtures.load("serpapi", "google-web-synthetic.json")
                .toHttpResponse()).after(Duration.ofMillis(300)));
        SearchContext context = SearchContext.builder().timeout(Instant.now(), Duration.ofMillis(100)).build();
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> provider.search(SearchRequest.builder("q").build(), context).get(5, TimeUnit.SECONDS));
        assertEquals(SearchErrorCategory.TIMEOUT, ((SearchProviderException) e.getCause()).getCategory());
    }

    @Test
    void openEndedUntilRangeIsInvalidForBingWithoutHttpCall() {
        assertEquals(SearchErrorCategory.INVALID_REQUEST, searchFails(SearchRequest.builder("q").target(BING)
                .timeRange(TimeRange.until(Instant.parse("2025-01-01T00:00:00Z"))).build()).getCategory());
        assertEquals(0, transport.getRequestCount());
    }

    // ---- response mapping ------------------------------------------------------------------------------------

    @Test
    void mapsGoogleResultsFeaturesAndMetadata() {
        respondWith("google-web-synthetic.json");
        SearchResponse response = search(SearchRequest.builder("serp4j java search").build());

        SearchMetadata metadata = response.getMetadata().get();
        assertEquals(Optional.of(SerpApiSearchProvider.DEFAULT_ID), metadata.getProvider());
        assertEquals(Optional.of(SearchEngineId.GOOGLE), metadata.getEngine());
        assertEquals(Optional.of(SearchVertical.WEB), metadata.getVertical());
        assertEquals("serp4j java search", metadata.getQuery().getOriginalQuery());
        assertEquals(Optional.of("serp4j java search library"), metadata.getQuery().getEffectiveQuery());
        assertEquals(SpellCorrection.Type.SHOWING_RESULTS_FOR,
                metadata.getQuery().getSpellCorrection().get().getType());
        assertEquals(Optional.of(true), metadata.getPage().get().getHasMore());
        assertEquals(1, metadata.getPage().get().getPageNumber().getAsInt());
        assertEquals(Optional.of("Success"), metadata.getExtensions().get(SerpApiExtensions.STATUS));
        assertEquals(Optional.of(new BigDecimal("1.42")),
                metadata.getExtensions().get(SerpApiExtensions.TOTAL_TIME_TAKEN));
        assertThat(metadata.getExecution().get().getSourceUrl().get()).doesNotContain(KEY);
        assertThat(metadata.toString()).doesNotContain(KEY);
        assertThat(metadata.getRawPayload()).isEmpty();

        SearchResultItem first = response.getData().getResults().stream()
                .filter(i -> i.getType().equals(ResultType.ORGANIC)).findFirst().get();
        assertEquals("https://www.example.com/docs/search", first.getUrl());
        assertEquals(Optional.of("Example Search Library for Java"), first.getTitle());
        assertEquals(Optional.of("Synthetic snippet of the first organic result."), first.getSnippet());
        assertEquals(Optional.of("www.example.com"), first.getDomain());
        assertEquals(Optional.of("Example"), first.getSiteName());
        assertEquals(1, first.getPosition().get().getRankInGroup().getAsInt());
        assertEquals(Optional.of("Oct 1, 2026"), first.getExtensions().get(SerpApiExtensions.DATE));

        assertThat(response.getData().getResults()).extracting(SearchResultItem::getType)
                .contains(ResultType.ORGANIC, ResultType.PAID, ResultType.NEWS);
        KnowledgePanelFeature panel = response.getData().getFeatures(KnowledgePanelFeature.class).get(0);
        assertEquals("Example Entity", panel.getTitle());
        assertThat(response.getData().getFeatures(SerpFeatureType.PEOPLE_ALSO_ASK)).hasSize(2);
        assertThat(response.getData().getFeatures(SerpFeatureType.ANSWER_BOX)).hasSize(1);
        assertThat(response.getData().getFeatures(SerpFeatureType.RELATED_SEARCHES)).hasSize(1);
    }

    @Test
    void mapsBingResults() {
        respondWith("bing-web-synthetic.json");
        SearchResponse response = search(SearchRequest.builder("serp4j java search").target(BING).build());
        assertEquals(Optional.of(SearchEngineId.BING), response.getMetadata().get().getEngine());
        assertThat(response.getData().getResults()).hasSize(2);
        assertEquals(Optional.of(true), response.getMetadata().get().getPage().get().getHasMore());
    }

    @Test
    void noResultsAnswerIsAnEmptySuccess() {
        respondWith("google-no-results-synthetic.json");
        SearchResponse response = search(SearchRequest.builder("zzzz synthetic nothing").build());
        assertThat(response.getData().getResults()).isEmpty();
        assertEquals(Optional.of(false), response.getMetadata().get().getPage().get().getHasMore());
    }

    @Test
    void errorStatusInSuccessfulHttpAnswerIsUnavailable() {
        transport.onAnyRequest(FakeHttpStep.json(200,
                "{\"search_metadata\":{\"status\":\"Error\"},\"error\":\"Couldn't fetch the page.\"}"));
        assertEquals(SearchErrorCategory.UNAVAILABLE, searchFails(SearchRequest.builder("q").build()).getCategory());
    }

    @Test
    void keepsRawPayloadOnlyOnRequest() {
        respondWith("google-web-synthetic.json");
        SearchResponse response = search(SearchRequest.builder("q").rawPayloadPolicy(RawPayloadPolicy.ALWAYS).build());
        assertThat(response.getMetadata().get().getRawPayload().get().getBodyAsText()).contains("organic_results");
        assertThat(response.getMetadata().get().getWarnings()).extracting(SearchWarning::getCode)
                .doesNotContain(SearchWarning.FEATURE_IGNORED);
    }

    @Test
    void onErrorKeepsRawPayloadWhenMappingWarns() {
        transport.onAnyRequest(FakeHttpStep.json(200,
                "{\"organic_results\":[{\"position\":1,\"title\":\"no link\"},"
                        + "{\"position\":2,\"title\":\"ok\",\"link\":\"https://example.org\"}]}"));
        SearchResponse response = search(SearchRequest.builder("q").rawPayloadPolicy(RawPayloadPolicy.ON_ERROR)
                .build());
        assertThat(response.getData().getResults()).hasSize(1);
        assertThat(response.getMetadata().get().getRawPayload()).isPresent();
    }

    @Test
    void invalidJsonIsResponseInvalid() {
        transport.onAnyRequest(FakeHttpStep.text(200, "<html>oops</html>", "text/html"));
        assertEquals(SearchErrorCategory.RESPONSE_INVALID, searchFails(SearchRequest.builder("q").build()).getCategory());
    }

    // ---- errors ----------------------------------------------------------------------------------------------

    @Test
    void invalidKeyIsAuthentication() {
        respondWith("error-invalid-key-synthetic.json");
        SearchProviderException failure = searchFails(SearchRequest.builder("q").build());
        assertEquals(SearchErrorCategory.AUTHENTICATION, failure.getCategory());
        assertEquals(401, failure.getProviderStatusCode().getAsInt());
        assertThat(failure.getMessage()).contains("Invalid API key").doesNotContain(KEY);
    }

    @Test
    void outOfSearchesIsQuotaExhausted() {
        respondWith("error-out-of-searches-synthetic.json");
        SearchProviderException failure = searchFails(SearchRequest.builder("q").build());
        assertEquals(SearchErrorCategory.QUOTA_EXHAUSTED, failure.getCategory());
        assertThat(failure.isRetryable()).isFalse();
    }

    @Test
    void hourlyLimitIsRateLimitedWithRetryAfter() {
        transport.onAnyRequest(FakeHttpStep.respond(HttpResponse.builder(429)
                .textBody("{\"error\":\"Too many requests in the last hour.\"}", "application/json")
                .header("Retry-After", "30").build()));
        SearchProviderException failure = searchFails(SearchRequest.builder("q").build());
        assertEquals(SearchErrorCategory.RATE_LIMITED, failure.getCategory());
        assertEquals(Optional.of(Duration.ofSeconds(30)), failure.getRetryAfter());
    }

    @Test
    void badRequestIsInvalidRequestAndMasksAnEchoedKey() {
        transport.onAnyRequest(FakeHttpStep.json(400,
                "{\"error\":\"Unsupported parameter in https://serpapi.com/search.json?api_key=" + KEY + "&x=1\"}"));
        SearchProviderException failure = searchFails(SearchRequest.builder("q").build());
        assertEquals(SearchErrorCategory.INVALID_REQUEST, failure.getCategory());
        assertThat(failure.getMessage()).doesNotContain(KEY).contains("api_key=***");
    }

    @Test
    void serverErrorIsUnavailable() {
        transport.onAnyRequest(FakeHttpStep.status(503));
        assertEquals(SearchErrorCategory.UNAVAILABLE, searchFails(SearchRequest.builder("q").build()).getCategory());
    }

    @Test
    void transportTimeoutIsTimeout() {
        transport.onAnyRequest(FakeHttpStep.fail(HttpTransportException.Kind.TIMEOUT));
        assertEquals(SearchErrorCategory.TIMEOUT, searchFails(SearchRequest.builder("q").build()).getCategory());
    }

    @Test
    void transportFailureNeverLeaksTheKey() {
        transport.onAnyRequest(FakeHttpStep.fail(HttpTransportException.Kind.CONNECT));
        SearchProviderException failure = searchFails(SearchRequest.builder("q").build());
        assertEquals(SearchErrorCategory.UNAVAILABLE, failure.getCategory());
        assertThat(failure.toString()).doesNotContain(KEY);
        assertThat(failure.getCause()).isNull();
    }

    @Test
    void closeCancelsRunningSearchAndKeepsTransportOpen() {
        transport.onAnyRequest(FakeHttpStep.hang());
        CompletableFuture<SearchResponse> running = provider.search(SearchRequest.builder("q").build(),
                SearchContext.defaults());
        provider.close();
        ExecutionException e = assertThrows(ExecutionException.class, () -> running.get(5, TimeUnit.SECONDS));
        assertEquals(SearchErrorCategory.CANCELLED, ((SearchProviderException) e.getCause()).getCategory());
        assertThat(transport.isClosed()).isFalse();
        assertThat(provider.toString()).doesNotContain(KEY);
    }

    @Test
    void optionsValidateTheirValues() {
        assertThrows(IllegalArgumentException.class, () -> SerpApiSearchOptions.builder().defaultCountry("DEU").build());
        assertThrows(IllegalArgumentException.class, () -> SerpApiSearchOptions.builder().baseUrl("ftp://x").build());
        assertThrows(IllegalArgumentException.class, () -> SerpApiSearchOptions.builder().googleDomain("bing.com")
                .build());
        assertThrows(IllegalStateException.class, () -> SerpApiSearchProvider.builder().apiKey("k").build());
    }
}
