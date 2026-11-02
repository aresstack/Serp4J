package com.aresstack.serp4j.provider.brightdata;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.testkit.contract.MappingDefinitionContractTest;
import com.aresstack.serp4j.testkit.fixture.Fixture;
import com.aresstack.serp4j.testkit.fixture.Fixtures;

import java.util.List;

/** The definition against the recorded responses (E-1, ADR-019); golden files under golden/brightdata-google-web. */
class BrightDataMappingContractTest extends MappingDefinitionContractTest {

    @Override
    protected MappingEngine engine() {
        return BrightDataMapping.webEngine();
    }

    @Override
    protected List<Fixture> fixtures() {
        return Fixtures.list("brightdata");
    }

    @Override
    protected StructuredValue tree(Fixture fixture) {
        return BrightDataResponses.unwrap(ProviderId.of("brightdata"), fixture.getText()).serp;
    }
}
