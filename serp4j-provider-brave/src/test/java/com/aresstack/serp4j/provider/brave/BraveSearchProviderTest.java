package com.aresstack.serp4j.provider.brave;

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
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.model.metadata.SpellCorrection;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class BraveSearchProviderTest {

    private static final String TOKEN = "unit-test-brave-token-0042";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);

    private final FakeHttpTransport transport = FakeHttpTransport.create();
    private BraveSearchProvider provider = BraveSearchProvider.builder().transport(transport).apiKey(TOKEN).build();

    @AfterEach
    void tearDown() {
        provider.close();
        transport.close();
    }

    private void respondWith(String fixture) {
        // toHttpResponse() honours the status from the fixture's sidecar (error fixtures are 422/429).
        transport.onAnyRequest(FakeHttpStep.respond(Fixtures.load("brave", fixture).toHttpResponse()));
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
    void buildsWebSearchRequestWithTokenOnlyInHeader() {
        respondWith("web-search-synthetic.json");
        search(SearchRequest.builder("serp4j  brave")
                .locale(SearchLocale.builder().countryCode("de").languageCode("de").uiLanguage("de-DE").build())
                .limit(10).offset(20)
                .safeSearch(SafeSearch.STRICT)
                .timeRange(TimeRange.pastWeek())
                .build());

        HttpRequest request = transport.getLastRequest();
        assertThat(request.getUrl()).startsWith("https://api.search.brave.com/res/v1/web/search?")
                .contains("q=serp4j")
                .contains("country=DE").contains("search_lang=de").contains("ui_lang=de-DE")
                .contains("count=10").contains("offset=2")
                .contains("safesearch=strict").contains("freshness=pw")
                .doesNotContain(TOKEN);
        assertEquals(Optional.of(TOKEN), request.getHeaders().getFirst("X-Subscription-Token"));
        assertThat(request.getHeaders().isSensitiveHeader("X-Subscription-Token")).isTrue();
        assertThat(request.toString()).doesNotContain(TOKEN);
        assertEquals(Optional.of("application/json"), request.getHeaders().getFirst("Accept"));
    }

    @Test
    void usesInstanceDefaultsAndProviderOptions() {
        provider.close();
        provider = BraveSearchProvider.builder().transport(transport).apiKey(TOKEN)
                .options(BraveSearchOptions.builder().defaultCountry("DE").defaultSearchLanguage("de")
                        .defaultUiLanguage("de-DE").apiVersion("2025-01-01").build())
                .build();
        respondWith("web-search-synthetic.json");
        search(SearchRequest.builder("q")
                .locale(SearchLocale.builder().countryCode("AT").location(GeoLocation.coordinates(48.2, 16.37))
                        .build())
                .timeRange(TimeRange.between(Instant.parse("2022-04-01T00:00:00Z"),
                        Instant.parse("2022-07-30T00:00:00Z")))
                .providerOptions(BraveRequestOptions.builder(BraveSearchProvider.DEFAULT_ID)
                        .spellcheck(false).extraSnippets(true).resultFilter("web", "news")
                        .units(BraveRequestOptions.Units.METRIC).goggle("https://example.org/g.goggle").build())
                .build());

        HttpRequest request = transport.getLastRequest();
        assertThat(request.getUrl()).contains("country=AT").contains("search_lang=de").contains("ui_lang=de-DE")
                .contains("freshness=2022-04-01to2022-07-30").contains("spellcheck=false")
                .contains("extra_snippets=true").contains("result_filter=web%2Cnews").contains("units=metric")
                .contains("goggles=");
        assertEquals(Optional.of("2025-01-01"), request.getHeaders().getFirst("api-version"));
        assertEquals(Optional.of("48.2"), request.getHeaders().getFirst("X-Loc-Lat"));
        assertEquals(Optional.of("16.37"), request.getHeaders().getFirst("X-Loc-Long"));
    }

    @Test
    void offsetMustBeAMultipleOfTheLimit() {
        assertEquals(SearchErrorCategory.INVALID_REQUEST,
                searchFails(SearchRequest.builder("q").limit(10).offset(5).build()).getCategory());
        assertEquals(0, transport.getRequestCount());
    }

    @Test
    void moreThanTenPagesIsInvalid() {
        assertEquals(SearchErrorCategory.INVALID_REQUEST,
                searchFails(SearchRequest.builder("q").limit(5).offset(50).build()).getCategory());
    }

    @Test
    void limitAboveTwentyIsUnsupportedWhenRequired() {
        assertEquals(SearchErrorCategory.UNSUPPORTED, searchFails(SearchRequest.builder("q").limit(50)
                .require(SearchFeature.MAX_RESULTS).build()).getCategory());
        assertEquals(0, transport.getRequestCount());
    }

    @Test
    void optionalLimitAboveTwentyIsCappedWithWarning() {
        respondWith("web-search-synthetic.json");
        SearchResponse response = search(SearchRequest.builder("q").limit(50).build());
        assertThat(transport.getLastRequest().getUrl()).contains("count=20");
        assertThat(response.getMetadata().get().getWarnings()).extracting(SearchWarning::getCode)
                .contains(SearchWarning.FEATURE_IGNORED);
    }

    @Test
    void providerOptionsOfTheWrongTypeAreRejected() {
        ProviderRequestOptions foreign = () -> BraveSearchProvider.DEFAULT_ID;
        SearchProviderException failure = searchFails(SearchRequest.builder("q").providerOptions(foreign).build());
        assertEquals(SearchErrorCategory.INVALID_REQUEST, failure.getCategory());
        assertThat(failure.getMessage()).contains("BraveRequestOptions");
        assertEquals(0, transport.getRequestCount());
    }

    @Test
    void optionsForOtherProvidersAreIgnored() {
        respondWith("web-search-synthetic.json");
        ProviderRequestOptions other = () -> ProviderId.of("dataforseo");
        search(SearchRequest.builder("q").providerOptions(other).build());
        assertEquals(1, transport.getRequestCount());
    }

    @Test
    void tooLongQueryIsInvalid() {
        StringBuilder words = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            words.append("w").append(i).append(' ');
        }
        assertEquals(SearchErrorCategory.INVALID_REQUEST,
                searchFails(SearchRequest.builder(words.toString()).build()).getCategory());
    }

    @Test
    void openEndedUntilRangeIsInvalid() {
        assertEquals(SearchErrorCategory.INVALID_REQUEST, searchFails(SearchRequest.builder("q")
                .timeRange(TimeRange.until(Instant.parse("2025-01-01T00:00:00Z"))).build()).getCategory());
    }

    @Test
    void sinceRangeEndsToday() {
        provider.close();
        provider = BraveSearchProvider.builder().transport(transport).apiKey(TOKEN).clock(CLOCK).build();
        respondWith("web-search-synthetic.json");
        search(SearchRequest.builder("q").timeRange(TimeRange.since(Instant.parse("2026-10-01T00:00:00Z"))).build());
        assertThat(transport.getLastRequest().getUrl()).contains("freshness=2026-10-01to2026-11-01");
    }

    @Test
    void credentialsAreResolvedPerSearchFromTheCredentialProvider() {
        provider.close();
        CredentialProvider credentials = key -> key.equals(CredentialKey.of("brave.api-key"))
                ? Optional.of(SecretValue.of("rotating-token")) : Optional.<SecretValue>empty();
        provider = BraveSearchProvider.builder().transport(transport).credentials(credentials).build();
        respondWith("web-search-synthetic.json");
        search(SearchRequest.builder("q").build());
        assertEquals(Optional.of("rotating-token"),
                transport.getLastRequest().getHeaders().getFirst("X-Subscription-Token"));
    }

    @Test
    void missingCredentialIsAConfigurationError() {
        provider.close();
        provider = BraveSearchProvider.builder().transport(transport).credentials(CredentialProvider.none()).build();
        SearchProviderException failure = searchFails(SearchRequest.builder("q").build());
        assertEquals(SearchErrorCategory.CONFIGURATION, failure.getCategory());
        assertThat(failure.getMessage()).contains("brave.api-key");
        assertEquals(0, transport.getRequestCount());
    }

    // ---- response mapping ------------------------------------------------------------------------------------

    @Test
    void mapsWebResultsFeaturesAndMetadata() {
        respondWith("web-search-synthetic.json");
        SearchResponse response = search(SearchRequest.builder("serp4j brave search").build());

        SearchMetadata metadata = response.getMetadata().get();
        assertEquals(Optional.of(BraveSearchProvider.DEFAULT_ID), metadata.getProvider());
        assertEquals(Optional.of(SearchEngineId.BRAVE), metadata.getEngine());
        assertEquals(Optional.of(SearchVertical.WEB), metadata.getVertical());
        assertEquals("serp4j brave search", metadata.getQuery().getOriginalQuery());
        assertEquals(Optional.of("serp4j brave search api"), metadata.getQuery().getEffectiveQuery());
        SpellCorrection correction = metadata.getQuery().getSpellCorrection().get();
        assertEquals(SpellCorrection.Type.SHOWING_RESULTS_FOR, correction.getType());
        assertEquals(Optional.of(true), metadata.getPage().get().getHasMore());
        assertEquals(1, metadata.getPage().get().getPageNumber().getAsInt());
        assertEquals(Optional.of("synthetic-summary-key"), metadata.getExtensions().get(BraveExtensions.SUMMARIZER_KEY));
        assertEquals(Optional.of(false), metadata.getExtensions().get(BraveExtensions.IS_NAVIGATIONAL));
        assertThat(metadata.getExtensions().getUnmapped("brave")).isPresent();
        assertThat(metadata.getExecution().get().getSourceUrl().get()).doesNotContain(TOKEN);
        assertThat(metadata.getRawPayload()).isEmpty();

        assertThat(response.getData().getResults()).hasSize(3);
        SearchResultItem first = response.getData().getResults().get(0);
        assertEquals(ResultType.ORGANIC, first.getType());
        assertEquals("https://www.example.com/docs/search", first.getUrl());
        assertEquals(Optional.of("Example Search API"), first.getTitle());
        assertEquals(Optional.of("Synthetic description with highlight."), first.getSnippet());
        assertThat(first.getAdditionalSnippets()).hasSize(2);
        assertEquals(Optional.of("www.example.com"), first.getDomain());
        assertEquals(Optional.of("Example"), first.getSiteName());
        assertEquals(Optional.of("en"), first.getLanguage());
        assertEquals(1, first.getPosition().get().getRankInGroup().getAsInt());
        assertEquals(Optional.of("October 1, 2026"), first.getExtensions().get(BraveExtensions.AGE));
        assertThat(first.getExtensions().getUnmapped("brave")).isPresent();

        assertThat(response.getData().getResults()).extracting(SearchResultItem::getType)
                .contains(ResultType.NEWS);
        KnowledgePanelFeature panel = response.getData().getFeatures(KnowledgePanelFeature.class).get(0);
        assertEquals("Example Entity", panel.getTitle());
        assertThat(response.getData().getFeatures(SerpFeatureType.FAQ)).hasSize(1);
        assertThat(response.getAnalysis()).isEmpty();
    }

    @Test
    void keepsRawPayloadOnlyOnRequest() {
        respondWith("web-search-synthetic.json");
        SearchResponse response = search(SearchRequest.builder("q").rawPayloadPolicy(RawPayloadPolicy.ALWAYS).build());
        assertThat(response.getMetadata().get().getRawPayload().get().getBodyAsText()).contains("\"web\"");
    }

    @Test
    void invalidJsonIsResponseInvalid() {
        transport.onAnyRequest(FakeHttpStep.text(200, "<html>oops</html>", "text/html"));
        assertEquals(SearchErrorCategory.RESPONSE_INVALID, searchFails(SearchRequest.builder("q").build()).getCategory());
    }

    // ---- errors ----------------------------------------------------------------------------------------------

    @Test
    void quotaLimitedMapsToQuotaExhausted() {
        respondWith("error-quota-synthetic.json");
        SearchProviderException failure = searchFails(SearchRequest.builder("q").build());
        assertEquals(SearchErrorCategory.QUOTA_EXHAUSTED, failure.getCategory());
        assertEquals(429, failure.getProviderStatusCode().getAsInt());
        assertEquals("QUOTA_LIMITED", failure.getDetails().get("errorCode"));
        assertThat(failure.isRetryable()).isFalse();
    }

    @Test
    void rateLimitedMapsToRateLimitedWithRetryAfter() {
        transport.onAnyRequest(FakeHttpStep.respond(HttpResponse.builder(429)
                .textBody(Fixtures.load("brave", "error-rate-synthetic.json").getText(), "application/json")
                .header("Retry-After", "2").build()));
        SearchProviderException failure = searchFails(SearchRequest.builder("q").build());
        assertEquals(SearchErrorCategory.RATE_LIMITED, failure.getCategory());
        assertEquals(Optional.of(Duration.ofSeconds(2)), failure.getRetryAfter());
        assertThat(failure.isRetryable()).isTrue();
    }

    @Test
    void validationErrorIsInvalidRequest() {
        respondWith("error-validation-synthetic.json");
        SearchProviderException failure = searchFails(SearchRequest.builder("q").build());
        assertEquals(SearchErrorCategory.INVALID_REQUEST, failure.getCategory());
        assertThat(failure.getMessage()).contains("422").contains("VALIDATION");
    }

    @Test
    void unauthorizedIsAuthentication() {
        transport.onAnyRequest(FakeHttpStep.json(401, "{\"type\":\"ErrorResponse\",\"error\":{\"status\":401}}"));
        assertEquals(SearchErrorCategory.AUTHENTICATION, searchFails(SearchRequest.builder("q").build()).getCategory());
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
    void transportConnectFailureIsUnavailableAndNeverLeaksTheTransportException() {
        transport.onAnyRequest(FakeHttpStep.fail(HttpTransportException.Kind.CONNECT));
        SearchProviderException failure = searchFails(SearchRequest.builder("q").build());
        assertEquals(SearchErrorCategory.UNAVAILABLE, failure.getCategory());
        assertThat(failure.getMessage()).doesNotContain(TOKEN);
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
    }

    @Test
    void requestOptionsValidateTheirValues() {
        ProviderId id = BraveSearchProvider.DEFAULT_ID;
        assertThrows(IllegalArgumentException.class, () -> BraveRequestOptions.builder(id).resultFilter("images"));
        assertThrows(IllegalArgumentException.class, () -> BraveRequestOptions.builder(id)
                .goggle("a").goggle("b").goggle("c").goggle("d").build());
        assertThrows(IllegalArgumentException.class, () -> BraveSearchOptions.builder().defaultCountry("DEU").build());
        assertThrows(IllegalArgumentException.class, () -> BraveSearchOptions.builder().baseUrl("ftp://x").build());
        assertThrows(IllegalStateException.class, () -> BraveSearchProvider.builder().apiKey("k").build());
    }
}
