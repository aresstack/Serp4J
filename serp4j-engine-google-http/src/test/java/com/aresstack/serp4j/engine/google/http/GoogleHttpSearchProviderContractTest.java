package com.aresstack.serp4j.engine.google.http;

import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.testkit.contract.SearchProviderContractTest;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;

/**
 * Contract test. The page body is the recorded browser DOM: it exercises the parser path only and does not claim that
 * Google serves this page to a cookie-free HTTP client (it does not, see docs/spikes/s0-google-http.md).
 */
class GoogleHttpSearchProviderContractTest extends SearchProviderContractTest<FakeHttpTransport> {

    @Override
    protected FakeHttpTransport createInfrastructure() {
        return FakeHttpTransport.create()
                .onAnyRequest(FakeHttpStep.fixture(Fixtures.load("google", "browser-web-de.html")));
    }

    @Override
    protected SearchProvider createProvider(ProviderId id, FakeHttpTransport infrastructure) {
        return GoogleHttpSearchProvider.builder(infrastructure).id(id).resolveGoogleGotoUrls(false).build();
    }

    @Override
    protected SearchRequest supportedRequest() {
        return SearchRequest.builder("elasticsearch java client").build();
    }
}
