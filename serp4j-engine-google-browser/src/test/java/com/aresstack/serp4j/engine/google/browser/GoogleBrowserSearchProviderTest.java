package com.aresstack.serp4j.engine.google.browser;

import com.aresstack.serp4j.browser.testkit.FakeBrowserRuntime;
import com.aresstack.serp4j.browser.testkit.FakePage;
import com.aresstack.serp4j.browser.testkit.FakeWeb;
import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.feature.RelatedQueriesFeature;
import com.aresstack.serp4j.core.port.ProviderKind;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.engine.google.GoogleSearchFailures;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.resolve.ResultUrlEnricher;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class GoogleBrowserSearchProviderTest {

    private static final String SEARCH = "https://www.google.com/search";

    private static SearchRequest request() {
        return SearchRequest.builder("elasticsearch java client")
                .locale(SearchLocale.builder().languageCode("de").countryCode("DE").build())
                .build();
    }

    private static FakeBrowserRuntime runtimeServing(FakePage searchPage) {
        return FakeBrowserRuntime.builder().web(FakeWeb.builder().page(SEARCH, searchPage)
                .page("https://consent.google.com/ml", FakePage.builder().title("Bevor Sie zu Google weitergehen")
                        .html("<html><body><form action=\"https://consent.google.com/save\"></form></body></html>")
                        .build())
                .build()).build();
    }

    private static FakePage recordedResultPage() {
        return FakePage.builder().title("elasticsearch java client - Google Suche")
                .html(Fixtures.loadRecorded("google", "browser-web-de.html").getText()).build();
    }

    @Test
    void loadsTheClassicSearchUrlInAnEphemeralSessionAndClosesIt() throws Exception {
        FakeBrowserRuntime runtime = runtimeServing(recordedResultPage());
        GoogleBrowserSearchProvider provider = GoogleBrowserSearchProvider.builder(runtime).build();

        SearchResponse response = provider.search(request(), SearchContext.defaults()).get(10, TimeUnit.SECONDS);

        assertThat(runtime.getSessions()).singleElement().satisfies(session -> {
            assertThat(session.getNavigations()).containsExactly(SEARCH + "?q=elasticsearch+java+client&hl=de&gl=de");
            assertThat(session.getNavigations().get(0)).doesNotContain("udm");
            assertThat(session.getOptions().getProfileMode().isPersistent()).isFalse();
        });
        assertThat(runtime.getOpenSessionCount()).isZero();
        assertThat(provider.getDescriptor().getKind()).isEqualTo(ProviderKind.BROWSER);
        assertThat(response.getData().getResults()).hasSize(9);
        assertThat(response.getData().getResults().get(7).getType()).isEqualTo(ResultType.VIDEO);
        assertThat(response.getData().getFeatures(RelatedQueriesFeature.class)).hasSize(1);
        provider.close();
        assertThat(runtime.isClosed()).isFalse();
    }

    @Test
    void consentRedirectWithoutResultsIsBlockedAndNothingIsClicked() {
        FakeBrowserRuntime runtime = runtimeServing(FakePage.redirect("https://consent.google.com/ml?continue=x"));
        GoogleBrowserSearchProvider provider = GoogleBrowserSearchProvider.builder(runtime).build();

        SearchProviderException e = failure(provider.search(request(), SearchContext.defaults()));

        assertThat(e.getCategory()).isEqualTo(SearchErrorCategory.BLOCKED);
        assertThat(e.getDetails()).containsEntry(GoogleSearchFailures.DETAIL_OBSTACLE, "CONSENT");
        assertThat(runtime.getSessions().get(0).getInteractions()).isEmpty();
        assertThat(runtime.getOpenSessionCount()).isZero();
    }

    @Test
    void sorryPageIsAChallenge() {
        FakeBrowserRuntime runtime = FakeBrowserRuntime.builder().web(FakeWeb.builder()
                .page(SEARCH, FakePage.redirect("https://www.google.com/sorry/index?continue=x"))
                .page("https://www.google.com/sorry/index", FakePage.builder().status(429)
                        .html("<html><body><div id=\"captcha-form\"></div></body></html>").build())
                .build()).build();

        SearchProviderException e = failure(GoogleBrowserSearchProvider.builder(runtime).build()
                .search(request(), SearchContext.defaults()));

        assertThat(e.getDetails()).containsEntry(GoogleSearchFailures.DETAIL_OBSTACLE, "CHALLENGE");
    }

    @Test
    void httpErrorPagesKeepTheirCategory() {
        FakeBrowserRuntime unavailable = runtimeServing(FakePage.builder().status(503)
                .html("<html><body>Service Unavailable</body></html>").build());
        SearchProviderException e = failure(GoogleBrowserSearchProvider.builder(unavailable).build()
                .search(request(), SearchContext.defaults()));
        assertThat(e.getCategory()).isEqualTo(SearchErrorCategory.UNAVAILABLE);
        assertThat(e.getProviderStatusCode()).hasValue(503);

        FakeBrowserRuntime forbidden = runtimeServing(FakePage.builder().status(403)
                .html("<html><body>Forbidden</body></html>").build());
        assertThat(failure(GoogleBrowserSearchProvider.builder(forbidden).build()
                .search(request(), SearchContext.defaults())).getCategory()).isEqualTo(SearchErrorCategory.BLOCKED);
    }

    @Test
    void waitingForAFreeSessionRespectsTheSearchDeadline() {
        FakeBrowserRuntime runtime = FakeBrowserRuntime.builder().maxConcurrentSessions(1).web(FakeWeb.builder()
                .page(SEARCH, FakePage.builder().delay(Duration.ofSeconds(3)).html("<html></html>").build())
                .build()).build();
        GoogleBrowserSearchProvider provider = GoogleBrowserSearchProvider.builder(runtime).build();
        CompletableFuture<SearchResponse> occupying = provider.search(request(), SearchContext.defaults());
        long until = System.currentTimeMillis() + 5000;
        while (runtime.getOpenSessionCount() == 0 && System.currentTimeMillis() < until) {
            Thread.yield();
        }

        long started = System.nanoTime();
        SearchProviderException e = failure(provider.search(request(), SearchContext.builder()
                .timeout(java.time.Instant.now(), Duration.ofMillis(300)).build()));

        assertThat(e.getCategory()).isEqualTo(SearchErrorCategory.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        provider.close();
        occupying.cancel(true);
    }

    @Test
    void changedLayoutIsResponseInvalid() {
        FakeBrowserRuntime runtime = runtimeServing(FakePage.builder()
                .html("<html><body><main>neu</main></body></html>").build());

        assertThat(failure(GoogleBrowserSearchProvider.builder(runtime).build()
                .search(request(), SearchContext.defaults())).getCategory())
                .isEqualTo(SearchErrorCategory.RESPONSE_INVALID);
    }

    @Test
    void slowPageRunsIntoTheNavigationTimeout() {
        FakeBrowserRuntime runtime = runtimeServing(FakePage.builder().delay(Duration.ofSeconds(5))
                .html("<html></html>").build());
        GoogleBrowserSearchProvider provider = GoogleBrowserSearchProvider.builder(runtime)
                .navigationTimeout(Duration.ofMillis(200)).build();

        assertThat(failure(provider.search(request(), SearchContext.defaults())).getCategory())
                .isEqualTo(SearchErrorCategory.TIMEOUT);
    }

    @Test
    void gotoUrlsAreResolvedWhenATransportIsGiven() throws Exception {
        FakeHttpTransport transport = FakeHttpTransport.create().onAnyRequest(FakeHttpStep.respond(
                HttpResponse.builder(302).header("Location", "https://github.com/elastic/elasticsearch-java").build()));
        GoogleBrowserSearchProvider provider = GoogleBrowserSearchProvider.builder(runtimeServing(recordedResultPage()))
                .gotoResolutionTransport(transport).build();

        SearchResponse response = provider.search(request(), SearchContext.defaults()).get(10, TimeUnit.SECONDS);

        assertThat(response.getData().getResults().get(1).getUrl())
                .isEqualTo("https://github.com/elastic/elasticsearch-java");
        assertThat(response.getData().getResults().get(1).getExtensions().get(ResultUrlEnricher.URL_RESOLUTION))
                .contains(ResultUrlEnricher.RESOLVED);
    }

    @Test
    void closeCancelsRunningSearchAndClosesItsSession() throws Exception {
        FakeBrowserRuntime runtime = runtimeServing(FakePage.builder().delay(Duration.ofSeconds(2))
                .html("<html></html>").build());
        GoogleBrowserSearchProvider provider = GoogleBrowserSearchProvider.builder(runtime).build();
        CompletableFuture<SearchResponse> running = provider.search(request(), SearchContext.defaults());
        long until = System.currentTimeMillis() + 5000;
        while (runtime.getOpenSessionCount() == 0 && System.currentTimeMillis() < until) {
            Thread.sleep(10);
        }

        long closing = System.nanoTime();
        provider.close();

        // close() must not wait for the running navigation (the session is closed off this thread)
        assertThat(Duration.ofNanos(System.nanoTime() - closing)).isLessThan(Duration.ofSeconds(1));
        assertThat(failure(running).getCategory()).isEqualTo(SearchErrorCategory.CANCELLED);
        until = System.currentTimeMillis() + 5000;
        while (runtime.getOpenSessionCount() > 0 && System.currentTimeMillis() < until) {
            Thread.sleep(10);
        }
        assertThat(runtime.getOpenSessionCount()).isZero();
    }

    private static SearchProviderException failure(CompletableFuture<SearchResponse> future) {
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return (SearchProviderException) e.getCause();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return fail("expected a failure");
    }
}
