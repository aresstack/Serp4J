package com.aresstack.serp4j.provider.serpapi;

import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.MappingDefinition;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.mapping.MappingResult;
import com.aresstack.serp4j.provider.serpapi.internal.SerpApiResponseMapper;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks both declarative definitions against the synthetic fixtures. The full {@code MappingDefinitionContractTest}
 * from the testkit accepts only recorded fixtures (E-1) and is added once a recorded SerpApi answer exists.
 */
class SerpApiMappingDefinitionTest {

    private static MappingResult map(MappingDefinition definition, String fixture) {
        return MappingEngine.builder(definition).provider(ProviderId.of("serpapi")).build()
                .map(JsonTrees.read(Fixtures.load("serpapi", fixture).getText()));
    }

    @Test
    void googleDefinitionMapsTheSyntheticFixtureWithoutWarnings() {
        MappingDefinition definition = SerpApiResponseMapper.googleDefinition();
        assertThat(definition.getId()).isEqualTo("serpapi-google");
        MappingResult result = map(definition, "google-web-synthetic.json");
        assertThat(result.getWarnings()).isEmpty();
        // 3 organic + 1 ad + 1 top story
        assertThat(result.getData().getResults()).hasSize(5);
        // knowledge graph, answer box, 2 related questions, related searches
        assertThat(result.getData().getFeatures()).hasSize(5);
    }

    @Test
    void bingDefinitionMapsTheSyntheticFixtureWithoutWarnings() {
        MappingDefinition definition = SerpApiResponseMapper.bingDefinition();
        assertThat(definition.getId()).isEqualTo("serpapi-bing");
        MappingResult result = map(definition, "bing-web-synthetic.json");
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getData().getResults()).hasSize(2);
        assertThat(result.getData().getFeatures()).hasSize(1);
    }
}
