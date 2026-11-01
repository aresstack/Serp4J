package com.aresstack.serp4j.provider.brave;

import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.testkit.contract.SearchProviderContractTest;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;

import java.util.Optional;

class BraveSearchProviderContractTest extends SearchProviderContractTest<FakeHttpTransport> {

    private static final String SECRET = "contract-brave-token-7f3a9c";

    @Override
    protected FakeHttpTransport createInfrastructure() {
        return FakeHttpTransport.create()
                .onAnyRequest(FakeHttpStep.fixture(Fixtures.load("brave", "web-search-synthetic.json")));
    }

    @Override
    protected SearchProvider createProvider(ProviderId id, FakeHttpTransport infrastructure) {
        return BraveSearchProvider.builder().id(id).transport(infrastructure).apiKey(SECRET).build();
    }

    @Override
    protected SearchRequest supportedRequest() {
        return SearchRequest.builder("serp4j brave search").build();
    }

    @Override
    protected Optional<String> secret() {
        return Optional.of(SECRET);
    }
}
