package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.core.port.credential.CredentialKey;
import com.aresstack.serp4j.core.port.credential.CredentialProvider;
import com.aresstack.serp4j.core.port.credential.SecretValue;
import com.aresstack.serp4j.testkit.contract.SearchProviderContractTest;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;

import java.util.Optional;

class DataForSeoSearchProviderContractTest extends SearchProviderContractTest<FakeHttpTransport> {

    static final String LOGIN = "contract-login";
    static final String PASSWORD = "contract-password-0815";

    static CredentialProvider credentials() {
        return (CredentialKey key) -> key.getName().endsWith(".login")
                ? Optional.of(SecretValue.of(LOGIN)) : Optional.of(SecretValue.of(PASSWORD));
    }

    @Override
    protected FakeHttpTransport createInfrastructure() {
        FakeHttpTransport transport = FakeHttpTransport.create();
        transport.onAnyRequest(FakeHttpStep.fixture(Fixtures.load("dataforseo", "google-organic-live-advanced.json")));
        return transport;
    }

    @Override
    protected SearchProvider createProvider(ProviderId id, FakeHttpTransport infrastructure) {
        return DataForSeoSearchProvider.builder(infrastructure, credentials()).id(id).build();
    }

    @Override
    protected SearchRequest supportedRequest() {
        return SearchRequest.builder("albert einstein").build();
    }

    @Override
    protected Optional<String> secret() {
        return Optional.of(PASSWORD);
    }
}
