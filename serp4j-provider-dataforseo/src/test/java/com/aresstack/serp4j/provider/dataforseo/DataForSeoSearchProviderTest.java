package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.DeviceType;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.data.SerpArea;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.QuestionAnswerFeature;
import com.aresstack.serp4j.core.model.feature.RelatedQueriesFeature;
import com.aresstack.serp4j.core.model.feature.FeaturedSnippetFeature;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.credential.CredentialProvider;
import com.aresstack.serp4j.core.port.factory.ProviderConfiguration;
import com.aresstack.serp4j.core.port.factory.ProviderEnvironment;
import com.aresstack.serp4j.core.port.factory.SearchProviderFactory;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ServiceLoader;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataForSeoSearchProviderTest {

    private static final ProviderId ID = ProviderId.of("dataforseo");
    private final FakeHttpTransport http = FakeHttpTransport.create();
    private DataForSeoSearchProvider provider =
            DataForSeoSearchProvider.builder(http, DataForSeoSearchProviderContractTest.credentials()).build();

    @AfterEach
    void tearDown() {
        provider.close();
        http.close();
    }

    private SearchResponse search(SearchRequest request) throws Exception {
        return provider.search(request, SearchContext.defaults()).get(5, TimeUnit.SECONDS);
    }

    private void reply(String fixture) {
        http.onAnyRequest(FakeHttpStep.fixture(Fixtures.load("dataforseo", fixture)));
    }

    private SearchProviderException failure(SearchRequest request) {
        try {
            provider.search(request, SearchContext.defaults()).join();
        } catch (CompletionException e) {
            return SearchProviderException.unwrap(e).orElseThrow(() -> new AssertionError(e));
        }
        throw new AssertionError("search did not fail");
    }

    @Test
    void postsOneLiveAdvancedTaskWithBasicAuthOnlyInHeader() throws Exception {
        reply("google-organic-live-advanced.json");
        search(SearchRequest.builder("albert einstein").locale(SearchLocale.of("DE", "de")).device(DeviceType.MOBILE)
                .limit(20).build());

        HttpRequest request = http.getLastRequest();
        assertThat(request.getMethod().name()).isEqualTo("POST");
        assertThat(request.getUrl()).isEqualTo("https://api.dataforseo.com/v3/serp/google/organic/live/advanced");
        String expected = "Basic " + Base64.getEncoder().encodeToString(
                (DataForSeoSearchProviderContractTest.LOGIN + ":" + DataForSeoSearchProviderContractTest.PASSWORD)
                        .getBytes(StandardCharsets.UTF_8));
        assertThat(request.getHeaders().getFirst("Authorization")).contains(expected);
        assertThat(request.getUrl()).doesNotContain(DataForSeoSearchProviderContractTest.PASSWORD)
                .doesNotContain(DataForSeoSearchProviderContractTest.LOGIN);
        assertThat(request.toString()).doesNotContain(DataForSeoSearchProviderContractTest.PASSWORD);

        StructuredValue body = JsonTrees.read(new String(request.getBody(), StandardCharsets.UTF_8));
        assertThat(body.getElements()).hasSize(1);
        StructuredValue task = body.getElements().get(0);
        assertThat(task.get("keyword").flatMap(StructuredValue::asText)).contains("albert einstein");
        assertThat(task.get("location_name").flatMap(StructuredValue::asText)).contains("Germany");
        assertThat(task.get("language_code").flatMap(StructuredValue::asText)).contains("de");
        assertThat(task.get("device").flatMap(StructuredValue::asText)).contains("mobile");
        assertThat(task.get("depth").get().asLong().getAsLong()).isEqualTo(20);
    }

    @Test
    void bingTargetRegularFunctionAndOptions() throws Exception {
        reply("google-organic-live-advanced.json");
        search(SearchRequest.builder("100% a+b").target(SearchTarget.web(SearchEngineId.BING))
                .providerOptions(DataForSeoOptions.builder(ID).function(DataForSeoFunction.REGULAR)
                        .locationCode(2276).tag("t-1").build())
                .build());
        HttpRequest request = http.getLastRequest();
        assertThat(request.getUrl()).isEqualTo("https://api.dataforseo.com/v3/serp/bing/organic/live/regular");
        StructuredValue task = JsonTrees.read(new String(request.getBody(), StandardCharsets.UTF_8)).getElements().get(0);
        assertThat(task.get("keyword").flatMap(StructuredValue::asText)).contains("100%25 a%2Bb");
        assertThat(task.get("location_code").get().asLong().getAsLong()).isEqualTo(2276);
        assertThat(task.get("language_code").flatMap(StructuredValue::asText)).contains("en");
        assertThat(task.get("tag").flatMap(StructuredValue::asText)).contains("t-1");
    }

    @Test
    void defaultLocationWhenRequestNamesNone() throws Exception {
        reply("google-organic-live-advanced.json");
        search(SearchRequest.builder("q").build());
        StructuredValue task = JsonTrees.read(new String(http.getLastRequest().getBody(), StandardCharsets.UTF_8))
                .getElements().get(0);
        assertThat(task.get("location_code").get().asLong().getAsLong()).isEqualTo(2840);
    }

    @Test
    void mapsResultsFeaturesAndMetadata() throws Exception {
        reply("google-organic-live-advanced.json");
        SearchResponse response = search(SearchRequest.builder("albert einstein").build());

        assertThat(response.getData().getOrganicResults()).extracting(SearchResultItem::getUrl)
                .containsExactly("https://example.org/wiki/albert-einstein", "https://physics.example.net/einstein");
        SearchResultItem first = response.getData().getOrganicResults().get(0);
        assertThat(first.getTitle()).contains("Albert Einstein");
        assertThat(first.getSnippet()).contains("German-born theoretical physicist.");
        assertThat(first.getSiteName()).contains("Example Encyclopedia");
        assertThat(first.getPosition().get().getRankAbsolute().getAsInt()).isEqualTo(2);
        assertThat(first.getPosition().get().getArea()).isEqualTo(SerpArea.MAIN);
        assertThat(first.getProvenance()).hasSize(1);
        assertThat(first.getProvenance().get(0).getProvider()).isEqualTo(ID);
        assertThat(first.getProvenance().get(0).getEngine()).isEqualTo(SearchEngineId.GOOGLE);

        assertThat(response.getData().getFeatures(FeaturedSnippetFeature.class)).hasSize(1);
        KnowledgePanelFeature kp = response.getData().getFeatures(KnowledgePanelFeature.class).get(0);
        assertThat(kp.getTitle()).isEqualTo("Albert Einstein");
        assertThat(kp.getFacts()).hasSize(1);
        assertThat(response.getData().getFeatures(QuestionAnswerFeature.class).get(0).getQuestions()).hasSize(1);
        assertThat(response.getData().getFeatures(RelatedQueriesFeature.class).get(0).getQueries()).hasSize(2);

        SearchMetadata metadata = response.getMetadata().get();
        assertThat(metadata.getProvider()).contains(ID);
        assertThat(metadata.getEngine()).contains(SearchEngineId.GOOGLE);
        assertThat(metadata.getQuery().getOriginalQuery()).isEqualTo("albert einstein");
        assertThat(metadata.getUsage().get().getCost().get().getAmount()).isEqualByComparingTo(new BigDecimal("0.002"));
        assertThat(metadata.getUsage().get().getCost().get().getCurrency()).isEqualTo("USD");
        assertThat(metadata.getExecution().get().getProviderRequestId()).contains("11011234-0000-0000-0000-000000000001");
        assertThat(metadata.getExecution().get().getProviderReportedDuration()).isPresent();
        assertThat(metadata.getExecution().get().getSourceUrl()).isPresent();
        assertThat(metadata.getPage().get().getTotalResultsEstimate().getAsLong()).isEqualTo(123000000L);
        assertThat(metadata.isComplete()).isTrue();
        assertThat(metadata.getRawPayload()).isEmpty();
        assertThat(response.getAnalysis()).isEmpty();
    }

    @Test
    void offsetSkipsLeadingOrganicResults() throws Exception {
        reply("google-organic-live-advanced.json");
        SearchResponse response = search(SearchRequest.builder("albert einstein").offset(1).limit(1).build());
        StructuredValue task = JsonTrees.read(new String(http.getLastRequest().getBody(), StandardCharsets.UTF_8))
                .getElements().get(0);
        assertThat(task.get("depth").get().asLong().getAsLong()).isEqualTo(2);
        assertThat(response.getData().getResults(ResultType.ORGANIC)).extracting(SearchResultItem::getUrl)
                .containsExactly("https://physics.example.net/einstein");
    }

    @Test
    void rawPayloadOnlyOnRequest() throws Exception {
        reply("google-organic-live-advanced.json");
        SearchResponse response = search(SearchRequest.builder("q").rawPayloadPolicy(RawPayloadPolicy.ALWAYS).build());
        assertThat(response.getMetadata().get().getRawPayload().get().getBodyAsText()).contains("\"status_code\"");
    }

    @Test
    void noResultsIsAnEmptyResponse() throws Exception {
        reply("status-40102-no-results.json");
        SearchResponse response = search(SearchRequest.builder("q").build());
        assertThat(response.getData().isEmpty()).isTrue();
        assertThat(response.getMetadata().get().isComplete()).isTrue();
    }

    @Test
    void partialResultsAreKeptAndMarked() throws Exception {
        reply("status-40106-partial.json");
        SearchResponse response = search(SearchRequest.builder("q").build());
        assertThat(response.getData().getOrganicResults()).hasSize(1);
        assertThat(response.getMetadata().get().isComplete()).isFalse();
        assertThat(response.getMetadata().get().getWarnings()).extracting(SearchWarning::getCode)
                .contains(SearchWarning.PARTIAL_RESULTS);
    }

    @ParameterizedTest
    @CsvSource({
            "status-40100-unauthorized.json, AUTHENTICATION, 40100, false",
            "status-40202-rate-limit.json, RATE_LIMITED, 40202, true",
            "status-50401-timeout.json, TIMEOUT, 50401, true",
            "status-40501-invalid-field.json, INVALID_REQUEST, 40501, false"
    })
    void statusCodesInTheBodyBecomeCategories(String fixture, SearchErrorCategory category, int code,
                                              boolean retryable) {
        reply(fixture);
        SearchProviderException e = failure(SearchRequest.builder("q").build());
        assertThat(e.getCategory()).isEqualTo(category);
        assertThat(e.getProviderStatusCode().getAsInt()).isEqualTo(code);
        assertThat(e.isRetryable()).isEqualTo(retryable);
        assertThat(e.getMessage()).doesNotContain(DataForSeoSearchProviderContractTest.PASSWORD);
    }

    @Test
    void httpErrorWithoutBody() {
        http.onAnyRequest(FakeHttpStep.status(503));
        assertThat(failure(SearchRequest.builder("q").build()).getCategory()).isEqualTo(SearchErrorCategory.UNAVAILABLE);
    }

    @Test
    void invalidJsonIsResponseInvalid() {
        http.onAnyRequest(FakeHttpStep.text(200, "<html>oops</html>", "text/html"));
        assertThat(failure(SearchRequest.builder("q").build()).getCategory())
                .isEqualTo(SearchErrorCategory.RESPONSE_INVALID);
    }

    @Test
    void transportTimeoutIsTimeout() {
        http.onAnyRequest(FakeHttpStep.fail(HttpTransportException.Kind.TIMEOUT));
        assertThat(failure(SearchRequest.builder("q").build()).getCategory()).isEqualTo(SearchErrorCategory.TIMEOUT);
    }

    @Test
    void missingCredentialsFailWithConfigurationWithoutRequest() {
        provider.close();
        provider = DataForSeoSearchProvider.builder(http, CredentialProvider.none()).build();
        assertThat(failure(SearchRequest.builder("q").build()).getCategory())
                .isEqualTo(SearchErrorCategory.CONFIGURATION);
        assertThat(http.getRequestCount()).isZero();
    }

    @Test
    void liveDepthLimitIsEnforced() {
        assertThat(failure(SearchRequest.builder("q").offset(150).limit(100).build()).getCategory())
                .isIn(SearchErrorCategory.UNSUPPORTED, SearchErrorCategory.INVALID_REQUEST);
        assertThat(http.getRequestCount()).isZero();
    }

    @Test
    void seDomainOnlyForGoogle() {
        SearchRequest request = SearchRequest.builder("q").target(SearchTarget.web(SearchEngineId.BING))
                .providerOptions(DataForSeoOptions.builder(ID).seDomain("bing.de").build()).build();
        assertThat(failure(request).getCategory()).isEqualTo(SearchErrorCategory.INVALID_REQUEST);
    }

    @Test
    void factoryIsRegisteredAndBorrowsTransport() {
        SearchProviderFactory factory = null;
        for (SearchProviderFactory f : ServiceLoader.load(SearchProviderFactory.class)) {
            if (f.getProviderType().equals("dataforseo")) {
                factory = f;
            }
        }
        assertThat(factory).isNotNull();
        DataForSeoSearchProvider created = (DataForSeoSearchProvider) factory.create(
                ProviderConfiguration.builder("dataforseo").property("function", "regular").build(),
                ProviderEnvironment.builder().service(HttpTransport.class, http)
                        .credentials(DataForSeoSearchProviderContractTest.credentials()).build());
        assertThat(created.getDescriptor().getAttributes()).containsEntry("function", "regular");
        created.close();
        assertThat(http.isClosed()).isFalse();
    }

    @Test
    void timestampAndDurationParsing() {
        assertThat(DataForSeoResponseMapper.parseTimestamp("2019-11-15 12:57:46 +00:00")).isNotNull();
        assertThat(DataForSeoResponseMapper.parseSeconds("0.0874 sec.").get().toMillis()).isEqualTo(87);
        assertThatThrownBy(() -> DataForSeoSearchProvider.builder(http, CredentialProvider.none())
                .baseUrl("https://user:pw@api.dataforseo.com")).isInstanceOf(IllegalArgumentException.class);
    }
}
