package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.port.CancellationSource;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.factory.ProviderConfiguration;
import com.aresstack.serp4j.core.port.factory.ProviderEnvironment;
import com.aresstack.serp4j.http.HttpMethod;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.resolve.ResultUrlEnricher;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import com.aresstack.serp4j.testkit.http.RequestMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Google /goto resolution on top of the recorded DataForSEO answer (PR #22): 7 of 10 organic results are redirects. */
class DataForSeoGotoUrlResolutionTest {

    private static final String RECORDED = "google-organic-live-advanced-de.json";
    private static final String GOTO_PREFIX = "https://google.com/goto?url=";
    private static final RequestMatcher API = RequestMatcher.post(DataForSeoSearchProvider.DEFAULT_BASE_URL);
    private static final RequestMatcher GOOGLE = RequestMatcher.of(
            r -> r.getMethod() == HttpMethod.GET && r.getUrl().startsWith(GOTO_PREFIX), "GET google /goto");

    private final FakeHttpTransport http = FakeHttpTransport.create();
    private DataForSeoSearchProvider provider = DataForSeoSearchProvider
            .builder(http, DataForSeoSearchProviderContractTest.credentials()).build();

    @AfterEach
    void tearDown() {
        provider.close();
        assertThat(http.isClosed()).as("borrowed transport stays open (ADR-017)").isFalse();
        http.close();
    }

    private static SearchRequest request() {
        return SearchRequest.builder("serp4j java search library").locale(SearchLocale.of("DE", "de")).limit(10)
                .build();
    }

    private SearchResponse search(SearchRequest request) throws Exception {
        return provider.search(request, SearchContext.defaults()).get(10, TimeUnit.SECONDS);
    }

    private void recordedApiAnswer() {
        http.on(API).reply(FakeHttpStep.fixture(Fixtures.load("dataforseo", RECORDED)));
    }

    private static String target(String gotoUrl) {
        return "https://target.example/" + gotoUrl.substring(GOTO_PREFIX.length()).toLowerCase(java.util.Locale.ROOT);
    }

    /** Answers each /goto with a 302 to a target derived from its token. */
    private static final class RedirectingTransport implements HttpTransport {
        final FakeHttpTransport api;
        final List<HttpRequest> gotoRequests = new java.util.concurrent.CopyOnWriteArrayList<HttpRequest>();
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();
        final List<CompletableFuture<HttpResponse>> pending =
                new java.util.concurrent.CopyOnWriteArrayList<CompletableFuture<HttpResponse>>();
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
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            final HttpResponse response = HttpResponse.builder(status).header("Location", target(request.getUrl()))
                    .build();
            CompletableFuture<HttpResponse> f = new CompletableFuture<HttpResponse>();
            if (hold) {
                pending.add(f);
            } else {
                concurrent.decrementAndGet();
                f.complete(response);
            }
            return f;
        }

        void releaseAll() {
            while (!pending.isEmpty()) {
                CompletableFuture<HttpResponse> f = pending.remove(0);
                concurrent.decrementAndGet(); // before completing: the resolver starts the next request at once
                f.complete(HttpResponse.builder(302).header("Location", "https://target.example/x").build());
            }
        }

        @Override
        public void close() {
            throw new AssertionError("provider must not close the borrowed transport");
        }
    }

    @Test
    void recordedGotoUrlsAreResolvedAndEverythingElseIsKept() throws Exception {
        recordedApiAnswer();
        SearchResponse unresolved;
        try (FakeHttpTransport plain = FakeHttpTransport.create();
             DataForSeoSearchProvider off = DataForSeoSearchProvider
                     .builder(plain, DataForSeoSearchProviderContractTest.credentials())
                     .resolveGoogleGotoUrls(false).build()) {
            plain.on(API).reply(FakeHttpStep.fixture(Fixtures.load("dataforseo", RECORDED)));
            unresolved = off.search(request(), SearchContext.defaults()).get(10, TimeUnit.SECONDS);
        }
        RedirectingTransport transport = new RedirectingTransport(http);
        provider = DataForSeoSearchProvider.builder(transport, DataForSeoSearchProviderContractTest.credentials())
                .build();

        SearchResponse response = search(request());

        List<SearchResultItem> before = unresolved.getData().getResults();
        List<SearchResultItem> after = response.getData().getResults();
        assertThat(after).hasSameSizeAs(before);
        int resolved = 0;
        for (int i = 0; i < before.size(); i++) {
            SearchResultItem b = before.get(i);
            SearchResultItem a = after.get(i);
            if (b.getUrl().startsWith(GOTO_PREFIX)) {
                resolved++;
                assertThat(a.getUrl()).isEqualTo(target(b.getUrl()));
                assertThat(a.getDomain()).hasValue("target.example");
                assertThat(a.getExtensions().get(ResultUrlEnricher.ORIGINAL_URL)).hasValue(b.getUrl());
                // every other field is kept
                SearchResultItem restored = a.toBuilder().url(b.getUrl()).extensions(b.getExtensions())
                        .clearSiteLinks().siteLinks(b.getSiteLinks()).build();
                assertThat(b.getDomain().isPresent() ? restored.toBuilder().domain(b.getDomain().get()).build()
                        : restored).isEqualTo(b);
                assertThat(a.getDisplayUrl()).isEqualTo(b.getDisplayUrl());
                assertThat(a.getSiteName()).isEqualTo(b.getSiteName());
            } else {
                assertThat(a).isEqualTo(b);
            }
        }
        // 7 of the 10 organic results of the recorded answer are /goto redirects
        assertThat(resolved).isEqualTo(7);
        // plus one /goto site link of result 4 (stackoverflow.com) – nested URLs share the same run
        assertThat(transport.gotoRequests).hasSize(8);
        assertThat(response.getData().getFeatures()).isEqualTo(unresolved.getData().getFeatures());
        assertThat(response.getMetadata().get().getWarnings()).isEqualTo(unresolved.getMetadata().get().getWarnings());
        assertThat(response.getMetadata().get().getEngine()).contains(SearchEngineId.GOOGLE);
        for (HttpRequest r : transport.gotoRequests) {
            assertThat(r.getMethod()).isEqualTo(HttpMethod.GET);
            assertThat(r.isFollowRedirects()).isFalse();
            assertThat(r.getHeaders().getFirst("Authorization")).as("no DataForSEO credentials to Google").isEmpty();
            assertThat(r.getHeaders().getFirst("Cookie")).isEmpty();
            assertThat(r.getTimeout().get()).isLessThanOrEqualTo(Duration.ofSeconds(3));
            assertThat(r.toString()).doesNotContain(DataForSeoSearchProviderContractTest.PASSWORD);
        }
        // the target pages themselves are never requested
        assertThat(http.getRequests()).hasSize(1);
        assertThat(transport.gotoRequests).noneMatch(r -> r.getUrl().startsWith("https://target.example"));
    }

    @Test
    void atMostFourResolutionsRunAtOnce() throws Exception {
        recordedApiAnswer();
        RedirectingTransport transport = new RedirectingTransport(http);
        transport.hold = true;
        provider = DataForSeoSearchProvider.builder(transport, DataForSeoSearchProviderContractTest.credentials())
                .build();
        CompletableFuture<SearchResponse> f = provider.search(request(), SearchContext.defaults());
        assertThat(transport.gotoRequests).hasSize(4);
        while (!f.isDone()) {
            transport.releaseAll();
        }
        assertThat(transport.maxConcurrent.get()).isEqualTo(4);
        assertThat(transport.gotoRequests).as("7 result URLs + 1 site link, limit shared").hasSize(8);
        assertThat(f.get().getData().getResults().get(0).getUrl()).isEqualTo("https://target.example/x");
    }

    @Test
    void recordedGotoSiteLinkIsResolvedAndTracedOnItsResult() throws Exception {
        recordedApiAnswer();
        RedirectingTransport transport = new RedirectingTransport(http);
        provider = DataForSeoSearchProvider.builder(transport, DataForSeoSearchProviderContractTest.credentials())
                .build();

        SearchResponse response = search(request());

        SearchResultItem owner = response.getData().getResults().get(3);
        assertThat(owner.getSiteLinks()).hasSize(3);
        String gotoLink = "https://google.com/goto?url=ANONYMIZED-REDIRECT-TOKEN-04";
        assertThat(owner.getSiteLinks().get(0).getUrl()).isEqualTo(target(gotoLink));
        assertThat(owner.getSiteLinks().get(0).getTitle())
                .hasValue("How do I include the sat4j library in eclipse java IDE?");
        assertThat(owner.getSiteLinks().get(1).getUrl())
                .isEqualTo("https://stackoverflow.com/questions/34248706/extending-eclipse-java-search");
        assertThat(owner.getSiteLinks().get(2).getUrl()).as("google.com/search is no redirect")
                .startsWith("https://www.google.com/search?");
        StructuredValue trace = owner.getExtensions().get(ResultUrlEnricher.NESTED_URL_RESOLUTIONS).get();
        assertThat(trace.getElements()).hasSize(1);
        assertThat(trace.path("0", "field").flatMap(StructuredValue::asText)).hasValue("siteLinks[0]");
        assertThat(trace.get(0).get().get("original_url").flatMap(StructuredValue::asText)).hasValue(gotoLink);
        assertThat(trace.get(0).get().get("url_resolution").flatMap(StructuredValue::asText))
                .hasValue(ResultUrlEnricher.RESOLVED);
        assertThat(trace.get(0).get().get("url").flatMap(StructuredValue::asText)).hasValue(target(gotoLink));
        assertThat(response.getData().getResults()).filteredOn(i -> i != owner)
                .allMatch(i -> !i.getExtensions().contains(ResultUrlEnricher.NESTED_URL_RESOLUTIONS));
    }

    @Test
    void duplicateUrlsAreResolvedOnce() throws Exception {
        String sameGoto = "https://www.google.com/goto?url=SAME";
        http.on(API).reply(FakeHttpStep.json(200, apiAnswer(sameGoto, sameGoto, "https://direct.example/a")));
        http.on(RequestMatcher.urlEquals(sameGoto)).reply(FakeHttpStep.respond(
                HttpResponse.builder(302).header("Location", "https://real.example/page").build()));

        SearchResponse response = search(request());

        assertThat(response.getData().getResults()).extracting(SearchResultItem::getUrl)
                .containsExactly("https://real.example/page", "https://real.example/page", "https://direct.example/a");
        http.assertRequestCount(RequestMatcher.urlEquals(sameGoto), 1);
        assertThat(http.getRequestCount()).as("direct URL needs no request").isEqualTo(2);
        http.assertNoUnmatchedRequests();
    }

    @Test
    void failuresKeepTheOriginalResultAndAreSummarisedInOneWarning() throws Exception {
        String a = "https://www.google.com/goto?url=A";
        String b = "https://www.google.com/goto?url=B";
        String c = "https://www.google.com/goto?url=C";
        String d = "https://www.google.com/goto?url=D";
        String e = "https://www.google.com/goto?url=E";
        String f = "https://www.google.com/goto?url=F";
        http.on(API).reply(FakeHttpStep.json(200, apiAnswer(a, b, c, d, e, f)));
        http.on(RequestMatcher.urlEquals(a)).reply(FakeHttpStep.status(403));
        http.on(RequestMatcher.urlEquals(b)).reply(FakeHttpStep.status(429));
        http.on(RequestMatcher.urlEquals(c)).reply(FakeHttpStep.fail(HttpTransportException.Kind.TIMEOUT));
        http.on(RequestMatcher.urlEquals(d)).reply(FakeHttpStep.status(302));
        http.on(RequestMatcher.urlEquals(e)).reply(FakeHttpStep.respond(
                HttpResponse.builder(302).header("Location", "/relative").build()));
        http.on(RequestMatcher.urlEquals(f)).reply(FakeHttpStep.status(500));

        SearchResponse response = search(request());

        assertThat(response.getData().getResults()).extracting(SearchResultItem::getUrl)
                .containsExactly(a, b, c, d, e, f);
        assertThat(response.getData().getResults())
                .allMatch(i -> !i.getExtensions().contains(ResultUrlEnricher.ORIGINAL_URL))
                .allMatch(i -> i.getDomain().equals(java.util.Optional.of("www.google.com")));
        List<SearchWarning> warnings = new ArrayList<SearchWarning>();
        for (SearchWarning w : response.getMetadata().get().getWarnings()) {
            if (w.getCode().equals(ResultUrlEnricher.WARNING_CODE)) {
                warnings.add(w);
            }
        }
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getMessage()).contains("6 of 6").contains("not_a_redirect=3")
                .contains("timeout=1").contains("invalid_location=2").doesNotContain("goto?url=");
        assertThat(response.getMetadata().get().isComplete()).isTrue();
    }

    @Test
    void disabledResolutionSendsNoExtraRequest() throws Exception {
        recordedApiAnswer();
        provider = DataForSeoSearchProvider.builder(http, DataForSeoSearchProviderContractTest.credentials())
                .resolveGoogleGotoUrls(false).build();
        SearchResponse response = search(request());
        assertThat(http.getRequestCount()).isEqualTo(1);
        assertThat(response.getData().getResults().get(0).getUrl()).startsWith(GOTO_PREFIX);
    }

    @Test
    void factoryOptionDisablesResolution() throws Exception {
        recordedApiAnswer();
        provider.close();
        provider = (DataForSeoSearchProvider) new DataForSeoSearchProviderFactory().create(
                ProviderConfiguration.builder("dataforseo").property("resolveGoogleGotoUrls", "false").build(),
                ProviderEnvironment.builder().service(HttpTransport.class, http)
                        .credentials(DataForSeoSearchProviderContractTest.credentials()).build());
        search(request());
        assertThat(http.getRequestCount()).isEqualTo(1);
    }

    @Test
    void bingResultsAreNeverResolved() throws Exception {
        String gotoUrl = "https://www.google.com/goto?url=BING";
        http.on(API).reply(FakeHttpStep.json(200, apiAnswer(gotoUrl)));
        SearchResponse response = search(SearchRequest.builder("q").target(SearchTarget.web(SearchEngineId.BING))
                .build());
        assertThat(response.getData().getResults().get(0).getUrl()).isEqualTo(gotoUrl);
        assertThat(http.getRequestCount()).isEqualTo(1);
    }

    @Test
    void cancellationDuringResolutionCancelsRunningRequestsAndStartsNoMore() {
        recordedApiAnswer();
        RedirectingTransport transport = new RedirectingTransport(http);
        transport.hold = true;
        provider = DataForSeoSearchProvider.builder(transport, DataForSeoSearchProviderContractTest.credentials())
                .build();
        CancellationSource cancel = new CancellationSource();
        CompletableFuture<SearchResponse> f = provider.search(request(),
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
        recordedApiAnswer();
        RedirectingTransport transport = new RedirectingTransport(http);
        transport.hold = true;
        provider = DataForSeoSearchProvider.builder(transport, DataForSeoSearchProviderContractTest.credentials())
                .build();
        CompletableFuture<SearchResponse> f = provider.search(request(), SearchContext.defaults());
        provider.close();
        assertThat(f).isCompletedExceptionally();
        assertThat(transport.pending).allMatch(CompletableFuture::isCancelled);
    }

    @Test
    void deadlineBoundsEachResolutionAndExpiredDeadlineFallsBack() throws Exception {
        recordedApiAnswer();
        java.time.Instant start = java.time.Instant.parse("2026-11-02T05:00:00Z");
        MutableClock clock = new MutableClock(start);
        RedirectingTransport transport = new RedirectingTransport(http);
        transport.hold = true;
        provider = DataForSeoSearchProvider.builder(transport, DataForSeoSearchProviderContractTest.credentials())
                .clock(clock).build();
        CompletableFuture<SearchResponse> f = provider.search(request(),
                SearchContext.builder().deadline(start.plusMillis(1500)).build());
        assertThat(transport.gotoRequests).hasSize(4)
                .allMatch(r -> r.getTimeout().get().compareTo(Duration.ofMillis(1500)) <= 0);
        clock.now = start.plusSeconds(2); // deadline passed: no further request is started
        transport.releaseAll();
        SearchResponse response = f.get(5, TimeUnit.SECONDS);
        assertThat(transport.gotoRequests).hasSize(4);
        assertThat(response.getMetadata().get().getWarnings()).extracting(SearchWarning::getCode)
                .contains(ResultUrlEnricher.WARNING_CODE);
        assertThat(response.getData().getResults().get(0).getUrl()).isEqualTo("https://target.example/x");
    }

    @Test
    void urlResolveTimeoutIsBoundedToThreeSeconds() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() ->
                DataForSeoSearchProvider.builder(http, DataForSeoSearchProviderContractTest.credentials())
                        .urlResolveTimeout(Duration.ofSeconds(4)));
    }

    static final class MutableClock extends java.time.Clock {
        volatile java.time.Instant now;

        MutableClock(java.time.Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return now;
        }
    }

    /** Minimal DataForSEO live/advanced answer with one organic item per URL (synthetic). */
    static String apiAnswer(String... urls) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < urls.length; i++) {
            items.append(i == 0 ? "" : ",").append("{\"type\":\"organic\",\"rank_group\":").append(i + 1)
                    .append(",\"rank_absolute\":").append(i + 1).append(",\"page\":1,\"position\":\"left\",")
                    .append("\"domain\":\"www.google.com\",\"title\":\"T").append(i).append("\",\"url\":\"")
                    .append(urls[i]).append("\",\"description\":\"d\"}");
        }
        return "{\"version\":\"0.1\",\"status_code\":20000,\"status_message\":\"Ok.\",\"time\":\"0.1 sec.\","
                + "\"cost\":0.002,\"tasks_count\":1,\"tasks_error\":0,\"tasks\":[{\"id\":\"t\",\"status_code\":20000,"
                + "\"status_message\":\"Ok.\",\"time\":\"0.1 sec.\",\"cost\":0.002,\"result_count\":1,"
                + "\"path\":[\"v3\"],\"data\":{},\"result\":[{\"keyword\":\"q\",\"type\":\"organic\","
                + "\"se_domain\":\"google.com\",\"location_code\":2840,\"language_code\":\"en\","
                + "\"check_url\":\"https://www.google.com/search?q=q\",\"datetime\":\"2026-11-02 05:00:00 +00:00\","
                + "\"spell\":null,\"item_types\":[\"organic\"],\"se_results_count\":10,\"items_count\":" + urls.length
                + ",\"items\":[" + items + "]}]}]}";
    }

}
