package com.aresstack.serp4j.engine.google.http;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.feature.RelatedQueriesFeature;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.port.ProviderKind;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.engine.google.GoogleSearchFailures;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.http.resolve.ResultUrlEnricher;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import com.aresstack.serp4j.testkit.http.RequestMatcher;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class GoogleHttpSearchProviderTest {

    /** Location header recorded in spike S0 (cookie-free GET, non-browser User-Agent). */
    private static final String RECORDED_CONSENT_LOCATION = "https://consent.google.com/ml?continue="
            + "https://www.google.com/search%3Fq%3Delasticsearch%2Bjava%2Bclient%26hl%3Dde%26gl%3Dde"
            + "&gl=DE&m=0&pc=srp&uxe=bip&cm=2&hl=de&src=1";

    private static SearchRequest request() {
        return SearchRequest.builder("elasticsearch java client")
                .locale(SearchLocale.builder().languageCode("de").countryCode("DE").build())
                .build();
    }

    @Test
    void sendsOneCookieFreeGetWithoutUdmAndWithoutFollowingRedirects() throws Exception {
        FakeHttpTransport transport = FakeHttpTransport.create()
                .onAnyRequest(FakeHttpStep.fixture(Fixtures.load("google", "browser-web-de.html")));
        GoogleHttpSearchProvider provider = GoogleHttpSearchProvider.builder(transport)
                .resolveGoogleGotoUrls(false).build();

        provider.search(request(), SearchContext.defaults()).get(5, TimeUnit.SECONDS);

        HttpRequest sent = transport.getLastRequest();
        assertThat(sent.getUrl())
                .isEqualTo("https://www.google.com/search?q=elasticsearch+java+client&hl=de&gl=de")
                .doesNotContain("udm");
        assertThat(sent.isFollowRedirects()).isFalse();
        assertThat(sent.getHeaders().getFirst("Cookie")).isEmpty();
        assertThat(sent.getHeaders().getFirst("User-Agent")).contains(GoogleHttpSearchProvider.DEFAULT_USER_AGENT);
        assertThat(provider.getDescriptor().getKind()).isEqualTo(ProviderKind.ENGINE_HTTP);
    }

    @Test
    void mapsRecognisedPageIntoNeutralModel() throws Exception {
        FakeHttpTransport transport = FakeHttpTransport.create()
                .onAnyRequest(FakeHttpStep.fixture(Fixtures.load("google", "browser-web-de.html")));
        GoogleHttpSearchProvider provider = GoogleHttpSearchProvider.builder(transport)
                .resolveGoogleGotoUrls(false).build();

        SearchResponse response = provider.search(request(), SearchContext.defaults()).get(5, TimeUnit.SECONDS);

        assertThat(response.getData().getResults()).hasSize(9);
        SearchResultItem first = response.getData().getResults().get(0);
        assertThat(first.getTitle()).contains("Java");
        assertThat(first.getSiteName()).contains("Elastic.co");
        assertThat(first.getDisplayUrl()).contains("https://www.elastic.co › clients");
        assertThat(first.getDomain()).isEmpty(); // goto target unknown, never guessed from the breadcrumb
        assertThat(first.getSiteLinks()).hasSize(2);
        assertThat(response.getData().getResults().get(7).getType()).isEqualTo(ResultType.VIDEO);
        assertThat(response.getData().getFeatures(RelatedQueriesFeature.class)).singleElement()
                .satisfies(f -> assertThat(f.getQueries()).hasSize(8));
        assertThat(response.getMetadata().get().getEngine()).contains(SearchEngineId.GOOGLE);
        assertThat(response.getMetadata().get().getWarnings()).isEmpty();
    }

    @Test
    void recordedConsentRedirectIsBlocked() {
        FakeHttpTransport transport = FakeHttpTransport.create().onAnyRequest(FakeHttpStep.respond(
                HttpResponse.builder(302).header("Location", RECORDED_CONSENT_LOCATION).build()));

        SearchProviderException e = failure(GoogleHttpSearchProvider.builder(transport).build());

        assertThat(e.getCategory()).isEqualTo(SearchErrorCategory.BLOCKED);
        assertThat(e.getDetails()).containsEntry(GoogleSearchFailures.DETAIL_OBSTACLE, "CONSENT");
        assertThat(e.getProviderStatusCode()).hasValue(302);
        assertThat(transport.getRequestCount()).isEqualTo(1); // no retry, no consent click
    }

    @Test
    void recordedJavascriptOnlyPageIsBlocked() {
        FakeHttpTransport transport = FakeHttpTransport.create()
                .onAnyRequest(FakeHttpStep.fixture(Fixtures.load("google", "http-enablejs-de.html")));

        SearchProviderException e = failure(GoogleHttpSearchProvider.builder(transport).build());

        assertThat(e.getCategory()).isEqualTo(SearchErrorCategory.BLOCKED);
        assertThat(e.getDetails()).containsEntry(GoogleSearchFailures.DETAIL_OBSTACLE, "JAVASCRIPT_REQUIRED");
    }

    @Test
    void sorryRedirectIsAChallenge() {
        FakeHttpTransport transport = FakeHttpTransport.create().onAnyRequest(FakeHttpStep.respond(
                HttpResponse.builder(302).header("Location", "https://www.google.com/sorry/index?continue=x").build()));

        SearchProviderException e = failure(GoogleHttpSearchProvider.builder(transport).build());

        assertThat(e.getDetails()).containsEntry(GoogleSearchFailures.DETAIL_OBSTACLE, "CHALLENGE");
    }

    @Test
    void httpStatusesAreClassified() {
        assertThat(failure(statusProvider(429)).getCategory()).isEqualTo(SearchErrorCategory.RATE_LIMITED);
        assertThat(failure(statusProvider(403)).getCategory()).isEqualTo(SearchErrorCategory.BLOCKED);
        assertThat(failure(statusProvider(503)).getCategory()).isEqualTo(SearchErrorCategory.UNAVAILABLE);
        assertThat(failure(statusProvider(404)).getCategory()).isEqualTo(SearchErrorCategory.RESPONSE_INVALID);
    }

    @Test
    void unknownLayoutIsResponseInvalidNotEmpty() {
        FakeHttpTransport transport = FakeHttpTransport.create().onAnyRequest(
                FakeHttpStep.text(200, "<html><body><p>neu</p></body></html>", "text/html; charset=UTF-8"));

        assertThat(failure(GoogleHttpSearchProvider.builder(transport).build()).getCategory())
                .isEqualTo(SearchErrorCategory.RESPONSE_INVALID);
    }

    @Test
    void timeoutOfTheTransportIsTimeout() {
        FakeHttpTransport transport = FakeHttpTransport.create()
                .onAnyRequest(FakeHttpStep.fail(HttpTransportException.Kind.TIMEOUT));

        assertThat(failure(GoogleHttpSearchProvider.builder(transport).build()).getCategory())
                .isEqualTo(SearchErrorCategory.TIMEOUT);
    }

    @Test
    void gotoUrlsAreResolvedWithTheSharedEnricher() throws Exception {
        FakeHttpTransport transport = FakeHttpTransport.create();
        transport.on(RequestMatcher.urlStartsWith("https://www.google.com/search"))
                .reply(FakeHttpStep.fixture(Fixtures.load("google", "browser-web-de.html")));
        transport.on(RequestMatcher.urlStartsWith("https://www.google.com/goto"))
                .reply(FakeHttpStep.respond(HttpResponse.builder(302)
                        .header("Location", "https://www.elastic.co/docs/reference/elasticsearch/clients/java")
                        .build()));
        GoogleHttpSearchProvider provider = GoogleHttpSearchProvider.builder(transport).build();

        SearchResponse response = provider.search(request(), SearchContext.defaults()).get(10, TimeUnit.SECONDS);

        SearchResultItem first = response.getData().getResults().get(0);
        assertThat(first.getUrl()).isEqualTo("https://www.elastic.co/docs/reference/elasticsearch/clients/java");
        assertThat(first.getExtensions().get(ResultUrlEnricher.URL_RESOLUTION)).contains(ResultUrlEnricher.RESOLVED);
        assertThat(first.getExtensions().get(ResultUrlEnricher.ORIGINAL_URL).get())
                .startsWith("https://www.google.com/goto?url=ANONYMIZED1");
    }

    @Test
    void closeCancelsRunningSearches() {
        FakeHttpTransport transport = FakeHttpTransport.create().onAnyRequest(FakeHttpStep.hang());
        GoogleHttpSearchProvider provider = GoogleHttpSearchProvider.builder(transport).build();
        CompletableFuture<SearchResponse> running = provider.search(request(), SearchContext.defaults());

        provider.close();

        assertThat(category(running)).isEqualTo(SearchErrorCategory.CANCELLED);
        assertThat(transport.isClosed()).isFalse();
    }

    private static GoogleHttpSearchProvider statusProvider(int status) {
        return GoogleHttpSearchProvider.builder(FakeHttpTransport.create().onAnyRequest(FakeHttpStep.status(status)))
                .build();
    }

    private static SearchProviderException failure(GoogleHttpSearchProvider provider) {
        CompletableFuture<SearchResponse> future = provider.search(request(), SearchContext.defaults());
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return (SearchProviderException) e.getCause();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return fail("expected a failure");
    }

    private static SearchErrorCategory category(CompletableFuture<SearchResponse> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return ((SearchProviderException) e.getCause()).getCategory();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return fail("expected a failure");
    }
}
