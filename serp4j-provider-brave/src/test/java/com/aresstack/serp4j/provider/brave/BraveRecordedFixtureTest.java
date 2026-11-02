package com.aresstack.serp4j.provider.brave;

import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.mapping.MappingResult;
import com.aresstack.serp4j.mapping.verify.MappingSnapshot;
import com.aresstack.serp4j.provider.brave.internal.BraveResponseMapper;
import com.aresstack.serp4j.testkit.fixture.Fixture;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first <b>recorded</b> Brave answer ({@code fixtures/brave/web-search-de.json}, live run 2026-11-02, E-1).
 *
 * <p>The full {@code MappingDefinitionContractTest} still needs recorded answers that contain the remaining sections
 * of {@code brave-web} (news, videos, faq, discussions, infobox, locations, query.altered, summarizer, rich); one
 * plain web answer cannot hit them. Until then this test pins the mapped output of the recorded answer to its golden
 * file ({@code golden/brave-web/brave/web-search-de.json.snapshot.json}, regenerate with {@code SERP4J_UPDATE_GOLDEN=1}
 * through the contract test once it exists) and runs it end to end through the provider.</p>
 */
class BraveRecordedFixtureTest {

    private static final String FIXTURE = "web-search-de.json";
    private static final String GOLDEN = "golden/brave-web/brave/web-search-de.json.snapshot.json";

    @Test
    void fixtureIsRecordedAndAnonymized() {
        Fixture fixture = Fixtures.load("brave", FIXTURE);
        assertThat(fixture.isRecorded()).isTrue();
        assertThat(fixture.isAnonymized()).isTrue();
    }

    @Test
    void mappedOutputMatchesGoldenFileWithoutWarnings() throws IOException {
        MappingEngine engine = MappingEngine.builder(BraveResponseMapper.webDefinition())
                .provider(ProviderId.of("brave")).build();
        MappingResult result = engine.map(JsonTrees.read(Fixtures.load("brave", FIXTURE).getText()));

        assertThat(result.getWarnings()).isEmpty();
        assertThat(normalize(MappingSnapshot.render(result))).isEqualTo(normalize(read(GOLDEN)));
    }

    @Test
    void providerMapsTheRecordedAnswerEndToEnd() throws Exception {
        try (FakeHttpTransport transport = FakeHttpTransport.create();
             BraveSearchProvider provider = BraveSearchProvider.builder().transport(transport)
                     .apiKey("unit-test-brave-token-0042").build()) {
            transport.onAnyRequest(FakeHttpStep.respond(Fixtures.load("brave", FIXTURE).toHttpResponse()));

            SearchResponse response = provider.search(SearchRequest.builder("serp4j java search library")
                            .locale(SearchLocale.builder().countryCode("DE").languageCode("de").uiLanguage("de-DE")
                                    .build())
                            .limit(10)
                            .rawPayloadPolicy(RawPayloadPolicy.ALWAYS)
                            .build(), SearchContext.defaults())
                    .get(5, TimeUnit.SECONDS);

            List<SearchResultItem> organic = response.getData().getResults(ResultType.ORGANIC);
            assertThat(organic).hasSize(10);
            assertThat(organic.get(0).getUrl()).isEqualTo("https://github.com/rumpelstiltzkin/serpapi4j");
            assertThat(organic.get(0).getDomain()).hasValue("github.com");
            assertThat(response.getMetadata().get().getEngine()).hasValue(SearchEngineId.BRAVE);
            assertThat(response.getMetadata().get().getWarnings()).isEmpty();
            assertThat(response.getMetadata().get().getRawPayload().get().getBodyLength()).isEqualTo(29334);
        }
    }

    private static String read(String resource) throws IOException {
        InputStream in = BraveRecordedFixtureTest.class.getClassLoader().getResourceAsStream(resource);
        assertThat(in).as(resource).isNotNull();
        try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").trim();
    }
}
