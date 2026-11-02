package com.aresstack.serp4j.provider.serpapi;

import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.testkit.contract.SearchProviderContractTest;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;

import java.util.Optional;

class SerpApiSearchProviderContractTest extends SearchProviderContractTest<FakeHttpTransport> {

    private static final String SECRET = "contract-serpapi-key-5d81e0";

    @Override
    protected FakeHttpTransport createInfrastructure() {
        return FakeHttpTransport.create()
                .onAnyRequest(FakeHttpStep.fixture(Fixtures.load("serpapi", "google-web-synthetic.json")));
    }

    @Override
    protected SearchProvider createProvider(ProviderId id, FakeHttpTransport infrastructure) {
        return SerpApiSearchProvider.builder().id(id).transport(infrastructure).apiKey(SECRET).build();
    }

    @Override
    protected SearchRequest supportedRequest() {
        return SearchRequest.builder("serp4j java search").build();
    }

    @Override
    protected Optional<String> secret() {
        return Optional.of(SECRET);
    }
}
