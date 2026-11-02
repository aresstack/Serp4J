package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.testkit.fixture.Fixture;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first <b>recorded</b> DataForSEO answer ({@code fixtures/dataforseo/google-organic-live-advanced-de.json},
 * live run 2026-11-02, E-1), anonymized as documented in its sidecar. The mapping output is pinned by
 * {@link DataForSeoMappingContractTest} (golden file); this test runs the answer end to end through the provider.
 */
class DataForSeoRecordedFixtureTest {

    private static final String FIXTURE = "google-organic-live-advanced-de.json";

    @Test
    void fixtureIsRecordedAndAnonymized() {
        Fixture fixture = Fixtures.load("dataforseo", FIXTURE);
        assertThat(fixture.isRecorded()).isTrue();
        assertThat(fixture.isAnonymized()).isTrue();
        assertThat(fixture.getText()).doesNotContain("4845928fa615").doesNotContain("\"cost\":0.002");
    }

    @Test
    void providerMapsTheRecordedAnswerEndToEnd() throws Exception {
        try (FakeHttpTransport http = FakeHttpTransport.create();
             DataForSeoSearchProvider provider =
                     DataForSeoSearchProvider.builder(http, DataForSeoSearchProviderContractTest.credentials())
                             .resolveGoogleGotoUrls(false) // pure mapping regression, no extra request
                             .build()) {
            http.onAnyRequest(FakeHttpStep.fixture(Fixtures.load("dataforseo", FIXTURE)));

            SearchResponse response = provider.search(SearchRequest.builder("serp4j java search library")
                            .locale(SearchLocale.of("DE", "de"))
                            .limit(10)
                            .rawPayloadPolicy(RawPayloadPolicy.ALWAYS)
                            .build(), SearchContext.defaults())
                    .get(5, TimeUnit.SECONDS);

            List<SearchResultItem> organic = response.getData().getOrganicResults();
            assertThat(organic).hasSize(10);
            // Google now hands DataForSEO some results only as google.com/goto redirects; the real answer shows it.
            assertThat(organic.get(0).getUrl()).startsWith("https://google.com/goto?url=");
            assertThat(organic.get(0).getTitle()).hasValue("Search API | Java Transport Client (deprecated) [7.17]");
            assertThat(organic.get(2).getUrl()).isEqualTo("https://github.com/thiagolocatelli/parse4j");
            assertThat(organic.get(2).getPosition().get().getRankAbsolute().getAsInt()).isEqualTo(3);
            assertThat(organic.get(2).getProvenance().get(0).getEngine()).isEqualTo(SearchEngineId.GOOGLE);

            SearchMetadata metadata = response.getMetadata().get();
            assertThat(metadata.getEngine()).contains(SearchEngineId.GOOGLE);
            assertThat(metadata.getExecution().get().getProviderRequestId())
                    .contains("00000000-0000-0000-0000-000000000000");
            assertThat(metadata.getPage().get().getTotalResultsEstimate().getAsLong()).isEqualTo(74L);
            assertThat(metadata.isComplete()).isTrue();
            assertThat(http.getRequestCount()).isEqualTo(1);
            assertThat(metadata.getRawPayload().get().getBodyLength())
                    .isEqualTo(Fixtures.load("dataforseo", FIXTURE).getBytes().length);
        }
    }
}
