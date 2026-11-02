package com.aresstack.serp4j.provider.serpapi;

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
import com.aresstack.serp4j.mapping.MappingDefinition;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.mapping.MappingResult;
import com.aresstack.serp4j.mapping.verify.MappingSnapshot;
import com.aresstack.serp4j.provider.serpapi.internal.SerpApiResponseMapper;
import com.aresstack.serp4j.testkit.fixture.Fixture;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first <b>recorded</b> SerpApi answers ({@code fixtures/serpapi/google-web-de.json} and {@code bing-web-de.json},
 * live run 2026-11-02 with {@code SerpApiLiveTest}, anonymized, E-1).
 *
 * <p>Both answers only contain organic results, related searches and pagination, so the full
 * {@code MappingDefinitionContractTest} (every path must hit) still needs answers with ads, knowledge graph, answer box
 * and related questions. Until then this test pins the mapped output to golden files
 * ({@code golden/serpapi/<fixture>.snapshot.json}; regenerate with {@code SERP4J_UPDATE_GOLDEN=1}) and runs both
 * answers end to end through the provider.</p>
 */
class SerpApiRecordedFixtureTest {

    @ParameterizedTest
    @ValueSource(strings = {"google-web-de.json", "bing-web-de.json"})
    void fixtureIsRecordedAndAnonymized(String name) {
        Fixture fixture = Fixtures.load("serpapi", name);
        assertThat(fixture.isRecorded()).isTrue();
        assertThat(fixture.isAnonymized()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"google-web-de.json", "bing-web-de.json"})
    void mappedOutputMatchesGoldenFileWithoutWarnings(String name) throws IOException {
        MappingDefinition definition = name.startsWith("bing") ? SerpApiResponseMapper.bingDefinition()
                : SerpApiResponseMapper.googleDefinition();
        MappingResult result = MappingEngine.builder(definition).provider(ProviderId.of("serpapi")).build()
                .map(JsonTrees.read(Fixtures.load("serpapi", name).getText()));

        assertThat(result.getWarnings()).isEmpty();
        String golden = "golden/serpapi/" + name + ".snapshot.json";
        String snapshot = MappingSnapshot.render(result);
        if ("1".equals(System.getenv("SERP4J_UPDATE_GOLDEN"))) {
            Path target = Paths.get("src", "test", "resources", "golden", "serpapi", name + ".snapshot.json");
            Files.createDirectories(target.getParent());
            Files.write(target, snapshot.getBytes(StandardCharsets.UTF_8));
            return;
        }
        assertThat(normalize(snapshot)).isEqualTo(normalize(read(golden)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"google-web-de.json", "bing-web-de.json"})
    void providerMapsTheRecordedAnswerEndToEnd(String name) throws Exception {
        SearchEngineId engine = name.startsWith("bing") ? SearchEngineId.BING : SearchEngineId.GOOGLE;
        try (FakeHttpTransport transport = FakeHttpTransport.create();
             SerpApiSearchProvider provider = SerpApiSearchProvider.builder().transport(transport)
                     .apiKey("unit-test-serpapi-key-0042").build()) {
            transport.onAnyRequest(FakeHttpStep.respond(Fixtures.load("serpapi", name).toHttpResponse()));

            SearchResponse response = provider.search(SearchRequest.builder("serp4j java search library")
                            .engine(engine)
                            .locale(SearchLocale.of("DE", "de"))
                            .limit(10)
                            .rawPayloadPolicy(RawPayloadPolicy.ON_ERROR)
                            .build(), SearchContext.defaults())
                    .get(5, TimeUnit.SECONDS);

            List<SearchResultItem> organic = response.getData().getResults(ResultType.ORGANIC);
            assertThat(organic).hasSize(10);
            assertThat(organic.get(0).getPosition().get().getRankInGroup().getAsInt()).isEqualTo(1);
            assertThat(response.getMetadata().get().getEngine()).hasValue(engine);
            assertThat(response.getMetadata().get().getWarnings()).isEmpty();
            assertThat(response.getMetadata().get().getRawPayload()).isEmpty();
            assertThat(response.getMetadata().get().getPage().get().getHasMore()).hasValue(true);
            assertThat(response.getMetadata().get().getPage().get().getTotalResultsEstimate().isPresent()).isTrue();
        }
    }

    private static String read(String resource) throws IOException {
        InputStream in = SerpApiRecordedFixtureTest.class.getClassLoader().getResourceAsStream(resource);
        assertThat(in).as(resource).isNotNull();
        try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").trim();
    }
}
