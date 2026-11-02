package com.aresstack.serp4j.provider.brave;

import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.MappingDefinition;
import com.aresstack.serp4j.mapping.MappingDefinitions;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.mapping.MappingResult;
import com.aresstack.serp4j.provider.brave.internal.BraveResponseMapper;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the declarative definition against the synthetic fixture. The full {@code MappingDefinitionContractTest}
 * from the testkit accepts only recorded fixtures (E-1) and is added once a recorded Brave answer exists.
 */
class BraveMappingDefinitionTest {

    @Test
    void definitionLoadsAndMapsTheSyntheticFixtureWithoutWarnings() {
        MappingDefinition definition = MappingDefinitions.from(
                JsonTrees.readResource(BraveSearchProvider.class, BraveResponseMapper.DEFINITION_RESOURCE));
        assertThat(definition.getId()).isEqualTo("brave-web");
        MappingResult result = MappingEngine.builder(definition).provider(ProviderId.of("brave")).build()
                .map(JsonTrees.read(Fixtures.load("brave", "web-search-synthetic.json").getText()));
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getData().getResults()).hasSize(3);
        assertThat(result.getData().getFeatures()).hasSize(2);
    }
}
