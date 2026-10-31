package com.aresstack.serp4j.testkit.selftest;

import com.aresstack.serp4j.testkit.contract.SearchProviderContractTest;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.testkit.FakeSharedResource;
import com.aresstack.serp4j.testkit.provider.MockSearchProvider;

import java.util.Optional;

/** The mock passes its own contract (implementation-plan.md Paket E: "Mocks bestehen ihre eigenen Contract-Tests"). */
class MockSearchProviderContractTest extends SearchProviderContractTest<FakeSharedResource> {

    @Override
    protected FakeSharedResource createInfrastructure() {
        return new FakeSharedResource("shared-infrastructure");
    }

    @Override
    protected SearchProvider createProvider(ProviderId id, FakeSharedResource infrastructure) {
        return MockSearchProvider.builder(id).infrastructure(infrastructure).build();
    }

    /** The mock never sees a credential; this runs the secret checks of the contract once. */
    @Override
    protected Optional<String> secret() {
        return Optional.of("mock-secret-never-used");
    }

    @Override
    protected SearchRequest supportedRequest() {
        return SearchRequest.builder("serp4j contract").engine(SearchEngineId.GOOGLE).build();
    }
}
