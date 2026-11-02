package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.testkit.contract.MappingDefinitionContractTest;
import com.aresstack.serp4j.testkit.fixture.Fixture;
import com.aresstack.serp4j.testkit.fixture.Fixtures;

import java.util.Arrays;
import java.util.List;

/**
 * Mapping definition against the fixtures. Until a recorded DataForSEO answer exists (E-1), the fixture is
 * synthetic and marked as such; Angelo's decision of 2026-11-01: implement against the API reference now, adjust
 * after a real answer.
 */
class DataForSeoMappingContractTest extends MappingDefinitionContractTest {

    @Override
    protected MappingEngine engine() {
        return DataForSeoResponseMapper.createEngine(ProviderId.of("dataforseo"));
    }

    @Override
    protected List<Fixture> fixtures() {
        return Arrays.asList(Fixtures.load("dataforseo", "google-organic-live-advanced.json"),
                Fixtures.load("dataforseo", "google-organic-live-advanced-features.json"));
    }

    @Override
    protected StructuredValue tree(Fixture fixture) {
        return JsonTrees.read(fixture.getText());
    }

    @Override
    protected boolean allowSyntheticFixtures() {
        return true;
    }
}
