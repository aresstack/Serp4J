package com.aresstack.serp4j.provider.serpapi;

import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.port.CancellationSource;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.factory.ProviderConfiguration;
import com.aresstack.serp4j.core.port.factory.ProviderEnvironment;
import com.aresstack.serp4j.http.HttpMethod;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.resolve.ResultUrlEnricher;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import com.aresstack.serp4j.testkit.http.RequestMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Google /goto resolution on top of the recorded SerpApi answer {@code google-web-de.json}: 7 of the 10 organic
 * results are {@code https://www.google.com/goto?url=…} redirects. Uses the shared {@link ResultUrlEnricher} and
 * {@code GoogleGotoUrlResolver}; Google is simulated with 302 + Location, nothing goes to the network.
 */
class SerpApiGotoUrlResolutionTest {

    private static final String KEY = "unit-test-serpapi-key-0042";
    private static final String GOOGLE_FIXTURE = "google-web-de.json";
    private static final String BING_FIXTURE = "bing-web-de.json";
    private static final String GOTO_PREFIX = "https://www.google.com/goto?url=";
    private static final RequestMatcher API = RequestMatcher.urlStartsWith(SerpApiSearchOptions.DEFAULT_BASE_URL);

    private final FakeHttpTransport api = FakeHttpTransport.create();
    private final RedirectingTransport transport = new RedirectingTransport(api);
    private SerpApiSearchProvider provider = provider(SerpApiSearchOptions.defaults());

    @AfterEach
    void tearDown() {
        provider.close();
        api.close();
    }

    private SerpApiSearchProvider provider(SerpApiSearchOptions options) {
        return SerpApiSearchProvider.builder().transport(transport).apiKey(KEY).options(options).build();
    }

    private static SearchRequest google() {
        return SearchRequest.builder("serp4j java search library").locale(SearchLocale.of("DE", "de")).limit(10)
                .build();
    }

    private void apiAnswers(String fixture) {
        api.on(API).reply(FakeHttpStep.respond(Fixtures.load("serpapi", fixture).toHttpResponse()));
    }

    private static String target(String gotoUrl) {
        String token = gotoUrl.substring(GOTO_PREFIX.length()).toLowerCase(Locale.ROOT);
        return "https://target.example/" + token.substring(0, Math.min(12, token.length()));
    }

    private static SearchResponse unresolved(String fixture, SearchRequest request) throws Exception {
        try (FakeHttpTransport plain = FakeHttpTransport.create();
             SerpApiSearchProvider off = SerpApiSearchProvider.builder().transport(plain).apiKey(KEY)
                     .options(SerpApiSearchOptions.builder().resolveGoogleGotoUrls(false).build()).build()) {
            plain.on(API).reply(FakeHttpStep.respond(Fixtures.load("serpapi", fixture).toHttpResponse()));
            SearchResponse response = off.search(request, SearchContext.defaults()).get(5, TimeUnit.SECONDS);
            assertThat(plain.getRequestCount()).as("disabled resolution sends no extra request").isEqualTo(1);
            return response;
        }
    }

    /** SerpApi calls go to the fake API; each /goto is answered with a 302 to a target derived from its token. */
    private static final class RedirectingTransport implements HttpTransport {
        final FakeHttpTransport api;
        final List<HttpRequest> gotoRequests = new CopyOnWriteArrayList<HttpRequest>();
        final List<CompletableFuture<HttpResponse>> pending = new CopyOnWriteArrayList<CompletableFuture<HttpResponse>>();
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();
        volatile boolean hold;
        volatile int status = 302;

        RedirectingTransport(FakeHttpTransport api) {
            this.api = api;
        }

        @Override
        public CompletableFuture<HttpResponse> execute(HttpRequest request) {
            if (!request.getUrl().startsWith(GOTO_PREFIX)) {
                return api.execute(request);
            }
            gotoRequests.add(request);
            maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
            CompletableFuture<HttpResponse> f = new CompletableFuture<HttpResponse>();
            if (hold) {
                pending.add(f);
            } else {
                concurrent.decrementAndGet();
                f.complete(HttpResponse.builder(status).header("Location", target(request.getUrl())).build());
            }
            return f;
        }

        void releaseAll() {
            while (!pending.isEmpty()) {
                CompletableFuture<HttpResponse> f = pending.remove(0);
                concurrent.decrementAndGet();
                f.complete(HttpResponse.builder(302).header("Location", "https://target.example/x").build());
            }
        }

        @Override
        public void close() {
            throw new AssertionError("provider must not close the borrowed transport");
        }
    }

    @Test
    void recordedGotoUrlsAreResolvedAfterMappingAndEverythingElseIsKept() throws Exception {
        SearchResponse before = unresolved(GOOGLE_FIXTURE, google());
        apiAnswers(GOOGLE_FIXTURE);

        SearchResponse after = provider.search(google(), SearchContext.defaults()).get(5, TimeUnit.SECONDS);

        List<SearchResultItem> b = before.getData().getResults();
        List<SearchResultItem> a = after.getData().getResults();
        assertThat(a).hasSameSizeAs(b);
        int resolved = 0;
        for (int i = 0; i < b.size(); i++) {
            if (b.get(i).getUrl().startsWith(GOTO_PREFIX)) {
                resolved++;
                assertThat(a.get(i).getUrl()).isEqualTo(target(b.get(i).getUrl()));
                assertThat(a.get(i).getDomain()).hasValue("target.example");
                assertThat(a.get(i).getExtensions().get(ResultUrlEnricher.ORIGINAL_URL)).hasValue(b.get(i).getUrl());
                assertThat(a.get(i).getExtensions().get(ResultUrlEnricher.URL_RESOLUTION))
                        .hasValue(ResultUrlEnricher.RESOLVED);
                SearchResultItem restored = a.get(i).toBuilder().url(b.get(i).getUrl())
                        .extensions(b.get(i).getExtensions()).build();
                if (b.get(i).getDomain().isPresent()) {
                    restored = restored.toBuilder().domain(b.get(i).getDomain().get()).build();
                }
                assertThat(restored).as("only url, domain and extensions change").isEqualTo(b.get(i));
            } else {
                assertThat(a.get(i)).isEqualTo(b.get(i));
            }
        }
        assertThat(resolved).as("7 of 10 organic Google results of the recorded answer are /goto").isEqualTo(7);
        assertThat(transport.gotoRequests).hasSize(resolved);
        assertThat(after.getData().getFeatures()).isEqualTo(before.getData().getFeatures());
        assertThat(after.getMetadata().get().getWarnings()).isEqualTo(before.getMetadata().get().getWarnings());
        for (HttpRequest r : transport.gotoRequests) {
            assertThat(r.getMethod()).isEqualTo(HttpMethod.GET);
            assertThat(r.isFollowRedirects()).isFalse();
            assertThat(r.getUrl()).as("SerpApi key never goes to Google").doesNotContain(KEY).doesNotContain("api_key");
            assertThat(r.getHeaders().getFirst("Cookie")).isEmpty();
            assertThat(r.getTimeout().get()).isLessThanOrEqualTo(Duration.ofSeconds(3));
        }
        assertThat(api.getRequestCount()).as("one SerpApi call, targets never fetched").isEqualTo(1);
    }

    @Test
    void atMostFourResolutionsRunAtOnce() throws Exception {
        apiAnswers(GOOGLE_FIXTURE);
        transport.hold = true;
        CompletableFuture<SearchResponse> f = provider.search(google(), SearchContext.defaults());
        assertThat(transport.gotoRequests).hasSize(4);
        while (!f.isDone()) {
            transport.releaseAll();
        }
        assertThat(transport.maxConcurrent.get()).isEqualTo(ResultUrlEnricher.MAX_CONCURRENT);
        assertThat(transport.gotoRequests).hasSize(7);
        assertThat(f.get().getMetadata().get().getWarnings()).extracting(SearchWarning::getCode)
                .doesNotContain(ResultUrlEnricher.WARNING_CODE);
    }

    @Test
    void failuresKeepTheGotoUrlWithStatusAndOneWarning() throws Exception {
        apiAnswers(GOOGLE_FIXTURE);
        transport.status = 429;

        SearchResponse response = provider.search(google(), SearchContext.defaults()).get(5, TimeUnit.SECONDS);

        int failed = 0;
        for (SearchResultItem item : response.getData().getResults()) {
            if (item.getUrl().startsWith(GOTO_PREFIX)) {
                failed++;
                assertThat(item.getExtensions().contains(ResultUrlEnricher.ORIGINAL_URL)).isFalse();
                assertThat(item.getExtensions().get(ResultUrlEnricher.URL_RESOLUTION)).hasValue("not_a_redirect");
            } else {
                assertThat(item.getExtensions().contains(ResultUrlEnricher.URL_RESOLUTION)).isFalse();
            }
        }
        assertThat(failed).isEqualTo(7);
        List<SearchWarning> warnings = response.getMetadata().get().getWarnings();
        assertThat(warnings).filteredOn(w -> w.getCode().equals(ResultUrlEnricher.WARNING_CODE)).hasSize(1)
                .first().satisfies(w -> assertThat(w.getMessage()).contains("7 of 7").contains("not_a_redirect=7")
                        .doesNotContain("goto?url="));
    }

    @Test
    void disabledByOptionsSendsNoExtraRequest() throws Exception {
        provider.close();
        provider = provider(SerpApiSearchOptions.builder().resolveGoogleGotoUrls(false).build());
        apiAnswers(GOOGLE_FIXTURE);
        SearchResponse response = provider.search(google(), SearchContext.defaults()).get(5, TimeUnit.SECONDS);
        assertThat(transport.gotoRequests).isEmpty();
        assertThat(response.getData().getResults()).filteredOn(i -> i.getUrl().startsWith(GOTO_PREFIX)).hasSize(7);
    }

    @Test
    void factoryPropertyDisablesResolution() throws Exception {
        provider.close();
        provider = (SerpApiSearchProvider) new SerpApiSearchProviderFactory().create(
                ProviderConfiguration.builder("serpapi").property("resolve-google-goto-urls", "false").build(),
                ProviderEnvironment.builder().service(HttpTransport.class, transport)
                        .credentials(key -> java.util.Optional.of(
                                com.aresstack.serp4j.core.port.credential.SecretValue.of(KEY.toCharArray())))
                        .build());
        apiAnswers(GOOGLE_FIXTURE);
        provider.search(google(), SearchContext.defaults()).get(5, TimeUnit.SECONDS);
        assertThat(provider.getOptions().isResolveGoogleGotoUrls()).isFalse();
        assertThat(transport.gotoRequests).isEmpty();
    }

    @Test
    void bingAnswersAreNeverResolved() throws Exception {
        apiAnswers(BING_FIXTURE);
        SearchRequest bing = SearchRequest.builder("serp4j java search library").engine(SearchEngineId.BING)
                .locale(SearchLocale.of("DE", "de")).limit(10).build();
        SearchResponse response = provider.search(bing, SearchContext.defaults()).get(5, TimeUnit.SECONDS);
        assertThat(response).isEqualTo(unresolved(BING_FIXTURE, bing));
        assertThat(transport.gotoRequests).isEmpty();
    }

    @Test
    void cancellationDuringResolutionCancelsRunningRequestsAndStartsNoMore() {
        apiAnswers(GOOGLE_FIXTURE);
        transport.hold = true;
        CancellationSource cancel = new CancellationSource();
        CompletableFuture<SearchResponse> f = provider.search(google(),
                SearchContext.builder().cancellation(cancel.getSignal()).build());
        assertThat(transport.gotoRequests).hasSize(4);
        cancel.cancel();
        assertThat(f).isCompletedExceptionally();
        assertThat(transport.pending).allMatch(CompletableFuture::isCancelled);
        transport.releaseAll();
        assertThat(transport.gotoRequests).hasSize(4);
    }

    @Test
    void closeDuringResolutionFailsTheSearchButKeepsTheTransport() {
        apiAnswers(GOOGLE_FIXTURE);
        transport.hold = true;
        CompletableFuture<SearchResponse> f = provider.search(google(), SearchContext.defaults());
        provider.close();
        assertThat(f).isCompletedExceptionally();
        assertThat(transport.pending).allMatch(CompletableFuture::isCancelled);
    }

    @Test
    void urlResolveTimeoutIsBoundedToThreeSeconds() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                SerpApiSearchOptions.builder().urlResolveTimeout(Duration.ofSeconds(4)).build());
        assertThatIllegalArgumentException().isThrownBy(() ->
                SerpApiSearchOptions.builder().urlResolveTimeout(Duration.ZERO).build());
    }
}
