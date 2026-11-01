package com.aresstack.serp4j.provider.brightdata;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.RelatedQueriesFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.credential.CredentialKey;
import com.aresstack.serp4j.core.port.credential.CredentialProvider;
import com.aresstack.serp4j.core.port.credential.StaticCredentialProvider;
import com.aresstack.serp4j.core.port.factory.ProviderConfiguration;
import com.aresstack.serp4j.core.port.factory.ProviderEnvironment;
import com.aresstack.serp4j.core.port.factory.SearchProviderFactory;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import com.aresstack.serp4j.testkit.http.RequestMatcher;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.ServiceLoader;

import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertCompletes;
import static com.aresstack.serp4j.testkit.assertion.Serp4jAssertions.assertFailsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrightDataSearchProviderTest {

    private static final ProviderId ID = ProviderId.of("brightdata");
    private static final String KEY = "test-key-abcdef0123456789";

    private static BrightDataSearchProvider provider(FakeHttpTransport http, CredentialProvider credentials) {
        return BrightDataSearchProvider.builder(ID).transport(http).credentials(credentials).zone("serp_api1").build();
    }

    private static BrightDataSearchProvider provider(FakeHttpTransport http) {
        return provider(http, StaticCredentialProvider.of(CredentialKey.of(ID, "apiKey"), KEY));
    }

    private static FakeHttpTransport recorded() {
        return FakeHttpTransport.create().onAnyRequest(
                FakeHttpStep.fixture(Fixtures.loadRecorded("brightdata", "google-web-pizza.json")));
    }

    @Test
    void sendsDocumentedRequestWithKeyOnlyInHeader() {
        FakeHttpTransport http = recorded();
        SearchRequest request = SearchRequest.builder("pizza").offset(10)
                .providerOptions(BrightDataOptions.builder(ID).proxyCountry("us").build())
                .build();

        assertCompletes(provider(http).search(request, SearchContext.defaults()));

        HttpRequest sent = http.getLastRequest();
        assertThat(sent.getMethod().name()).isEqualTo("POST");
        assertThat(sent.getUrl()).isEqualTo("https://api.brightdata.com/request");
        assertThat(sent.getHeaders().getFirst("Authorization")).contains("Bearer " + KEY);
        assertThat(sent.getHeaders().isSensitiveHeader("Authorization")).isTrue();
        String body = new String(sent.getBody(), StandardCharsets.UTF_8);
        StructuredValue json = JsonTrees.read(body);
        assertThat(json.get("zone").get().asText()).contains("serp_api1");
        assertThat(json.get("format").get().asText()).contains("json");
        assertThat(json.get("data_format").get().asText()).contains("parsed");
        assertThat(json.get("url").get().asText().get()).isEqualTo("https://www.google.com/search?q=pizza&start=10");
        assertThat(body).doesNotContain(KEY);
        assertThat(sent.getUrl()).doesNotContain(KEY);
        assertThat(sent.toString()).doesNotContain(KEY);
    }

    @Test
    void mapsRecordedResponseKeepingRankAndGlobalRankApart() {
        SearchResponse response = assertCompletes(provider(recorded())
                .search(SearchRequest.builder("pizza").build(), SearchContext.defaults()));

        List<SearchResultItem> organic = response.getData().getOrganicResults();
        assertThat(organic).hasSize(9);
        SearchResultItem first = organic.get(0);
        assertThat(first.getPosition().get().getRankInGroup().getAsInt()).isEqualTo(1);
        assertThat(first.getPosition().get().getRankAbsolute().getAsInt()).isEqualTo(12);
        assertThat(first.getTitle()).isPresent();
        assertThat(first.getUrl()).startsWith("http");

        assertThat(response.getData().getResults()).anyMatch(item -> item.getType().equals(ResultType.VIDEO));
        assertThat(response.getData().getResults()).anyMatch(item -> item.getType().equals(ResultType.of("short_video")));
        assertThat(response.getData().getFeatures(KnowledgePanelFeature.class)).hasSize(1);
        assertThat(response.getData().getFeatures(RelatedQueriesFeature.class)).hasSize(1);
        assertThat(response.getData().getFeatures()).anyMatch(f -> f.getType().equals(SerpFeatureType.PEOPLE_ALSO_ASK));

        SearchMetadata metadata = response.getMetadata().get();
        assertThat(metadata.getProvider()).contains(ID);
        assertThat(metadata.getQuery().getOriginalQuery()).isEqualTo("pizza");
        assertThat(metadata.getExecution().get().getProviderRequestId()).contains("hl_85d10ea0_cf37srpsq4p");
        assertThat(metadata.getExecution().get().getSourceUrl()).contains("https://www.google.com/search?q=pizza");
        assertThat(metadata.getPage().get().getTotalResultsEstimate().getAsLong()).isEqualTo(1230000000L);
        assertThat(metadata.getPage().get().getHasMore()).contains(Boolean.TRUE);
        assertThat(metadata.getPage().get().getPageNumber().getAsInt()).isEqualTo(1);
        assertThat(metadata.getRawPayload()).isEmpty();
    }

    @Test
    void rawPayloadOnlyWhenAsked() {
        SearchResponse response = assertCompletes(provider(recorded()).search(
                SearchRequest.builder("pizza").rawPayloadPolicy(RawPayloadPolicy.ALWAYS).build(),
                SearchContext.defaults()));
        assertThat(response.getMetadata().get().getRawPayload().get().getBodyAsText()).contains("\"status_code\":200");
    }

    @Test
    void zoneFromOptionsWins() {
        FakeHttpTransport http = recorded();
        SearchRequest request = SearchRequest.builder("pizza")
                .providerOptions(BrightDataOptions.builder(ID).zone("other_zone").build()).build();
        assertCompletes(provider(http).search(request, SearchContext.defaults()));
        assertThat(new String(http.getLastRequest().getBody(), StandardCharsets.UTF_8)).contains("\"other_zone\"");
    }

    @Test
    void httpStatusesBecomeCategories() {
        assertFailure(FakeHttpStep.status(401), SearchErrorCategory.AUTHENTICATION);
        assertFailure(FakeHttpStep.status(400), SearchErrorCategory.INVALID_REQUEST);
        assertFailure(FakeHttpStep.status(503), SearchErrorCategory.UNAVAILABLE);
        SearchProviderException limited = assertFailure(FakeHttpStep.respond(
                HttpResponse.builder(429).header("Retry-After", "7").build()), SearchErrorCategory.RATE_LIMITED);
        assertThat(limited.getRetryAfter()).contains(Duration.ofSeconds(7));
        assertThat(limited.getProviderStatusCode().getAsInt()).isEqualTo(429);
        assertFailure(FakeHttpStep.fail(HttpTransportException.Kind.TIMEOUT), SearchErrorCategory.TIMEOUT);
        assertFailure(FakeHttpStep.fail(HttpTransportException.Kind.CONNECT), SearchErrorCategory.UNAVAILABLE);
    }

    @Test
    void wrapperProblemsAreReported() {
        // Synthetic inputs, written for this test only: they exercise the wrapper handling, not the SERP schema.
        assertFailure(FakeHttpStep.json(200, "{\"status_code\":429,\"headers\":{},\"body\":\"\"}"),
                SearchErrorCategory.BLOCKED);
        assertFailure(FakeHttpStep.json(200, "{\"status_code\":200,\"headers\":{},\"body\":\"<html>\"}"),
                SearchErrorCategory.RESPONSE_INVALID);
        assertFailure(FakeHttpStep.text(200, "not json", "text/plain"), SearchErrorCategory.RESPONSE_INVALID);
    }

    @Test
    void missingKeyIsConfigurationErrorWithoutCall() {
        FakeHttpTransport http = recorded();
        assertFailsWith(provider(http, CredentialProvider.none())
                .search(SearchRequest.builder("pizza").build(), SearchContext.defaults()),
                SearchErrorCategory.CONFIGURATION);
        assertThat(http.getRequestCount()).isZero();
    }

    @Test
    void builderNeedsZone() {
        assertThatThrownBy(() -> BrightDataSearchProvider.builder(ID).transport(recorded())
                .credentials(CredentialProvider.none()).build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void factoryIsFoundThroughServiceLoaderAndBorrowsTransport() {
        SearchProviderFactory factory = null;
        for (SearchProviderFactory candidate : ServiceLoader.load(SearchProviderFactory.class)) {
            if (candidate.getProviderType().equals("brightdata")) {
                factory = candidate;
            }
        }
        assertThat(factory).isNotNull();
        FakeHttpTransport http = recorded();
        ProviderConfiguration configuration = ProviderConfiguration.builder("brightdata")
                .property("zone", "serp_api1").property("requestTimeout", "PT30S").build();
        ProviderEnvironment environment = ProviderEnvironment.builder()
                .service(HttpTransport.class, http)
                .credentials(StaticCredentialProvider.of(configuration.credentialKey("apiKey"), KEY))
                .build();
        BrightDataSearchProvider created = (BrightDataSearchProvider) factory.create(configuration, environment);
        assertCompletes(created.search(SearchRequest.builder("pizza").build(), SearchContext.defaults()));
        assertThat(http.getLastRequest().getTimeout()).contains(Duration.ofSeconds(30));
        created.close();
        assertThat(http.isClosed()).isFalse();
    }

    private static SearchProviderException assertFailure(FakeHttpStep step, SearchErrorCategory category) {
        FakeHttpTransport http = FakeHttpTransport.create().onAnyRequest(step);
        SearchProviderException failure = assertFailsWith(provider(http)
                .search(SearchRequest.builder("pizza").build(), SearchContext.defaults()), category);
        assertThat(failure.getMessage()).doesNotContain(KEY);
        return failure;
    }
}
