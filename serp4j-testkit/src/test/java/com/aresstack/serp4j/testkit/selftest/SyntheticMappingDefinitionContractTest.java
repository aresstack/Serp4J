package com.aresstack.serp4j.testkit.selftest;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.MappingDefinitions;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.testkit.contract.MappingDefinitionContractTest;
import com.aresstack.serp4j.testkit.fixture.Fixture;
import com.aresstack.serp4j.testkit.fixture.Fixtures;

import java.util.List;

/**
 * Runs {@link MappingDefinitionContractTest} against a synthetic definition and fixture. Synthetic data is allowed
 * here only because no provider definition lives in the testkit; provider modules use recorded fixtures (E-1).
 */
class SyntheticMappingDefinitionContractTest extends MappingDefinitionContractTest {

    static MappingEngine syntheticEngine() {
        return MappingEngine.of(MappingDefinitions.from(JsonTrees.readResource(
                SyntheticMappingDefinitionContractTest.class, "/mapping/synthetic-web.json")));
    }

    @Override
    protected MappingEngine engine() {
        return syntheticEngine();
    }

    @Override
    protected List<Fixture> fixtures() {
        return Fixtures.list("testkit-mapping");
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
