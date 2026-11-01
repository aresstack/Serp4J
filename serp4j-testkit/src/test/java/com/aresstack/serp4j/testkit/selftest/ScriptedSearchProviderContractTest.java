package com.aresstack.serp4j.testkit.selftest;

import com.aresstack.serp4j.testkit.contract.SearchProviderContractTest;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.testkit.FakeSharedResource;
import com.aresstack.serp4j.testkit.provider.Outcome;
import com.aresstack.serp4j.testkit.provider.ScriptedSearchProvider;

import java.time.Duration;

/** The scripted mock passes the provider contract, here with latency and an explicit target. */
class ScriptedSearchProviderContractTest extends SearchProviderContractTest<FakeSharedResource> {

    @Override
    protected FakeSharedResource createInfrastructure() {
        return new FakeSharedResource("shared-infrastructure");
    }

    @Override
    protected SearchProvider createProvider(ProviderId id, FakeSharedResource infrastructure) {
        return ScriptedSearchProvider.builder(id)
                .infrastructure(infrastructure)
                .latency(Duration.ofMillis(5))
                .thenAlways(Outcome.syntheticResults(2))
                .build();
    }

    @Override
    protected SearchRequest supportedRequest() {
        return SearchRequest.builder("serp4j contract")
                .target(SearchTarget.of(SearchEngineId.GOOGLE, SearchVertical.NEWS))
                .build();
    }
}
