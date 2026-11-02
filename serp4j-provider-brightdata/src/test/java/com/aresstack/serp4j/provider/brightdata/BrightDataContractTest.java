package com.aresstack.serp4j.provider.brightdata;

import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.core.port.credential.CredentialKey;
import com.aresstack.serp4j.core.port.credential.StaticCredentialProvider;
import com.aresstack.serp4j.testkit.contract.SearchProviderContractTest;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import com.aresstack.serp4j.testkit.http.RequestMatcher;

import java.util.Optional;

class BrightDataContractTest extends SearchProviderContractTest<FakeHttpTransport> {

    static final String SECRET = "contract-brightdata-key-0123456789";

    @Override
    protected FakeHttpTransport createInfrastructure() {
        return FakeHttpTransport.create()
                .on(RequestMatcher.post(BrightDataSearchProvider.DEFAULT_API_URL))
                .reply(FakeHttpStep.fixture(Fixtures.loadRecorded("brightdata", "google-web-pizza.json")));
    }

    @Override
    protected SearchProvider createProvider(ProviderId id, FakeHttpTransport infrastructure) {
        return BrightDataSearchProvider.builder(id)
                .transport(infrastructure)
                .credentials(StaticCredentialProvider.of(CredentialKey.of(id, BrightDataSearchProvider.API_KEY), SECRET))
                .zone("serp_api1")
                .build();
    }

    @Override
    protected SearchRequest supportedRequest() {
        return SearchRequest.builder("pizza").engine(SearchEngineId.GOOGLE).build();
    }

    @Override
    protected Optional<String> secret() {
        return Optional.of(SECRET);
    }
}
