package com.aresstack.serp4j.provider.argos;

import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.testkit.contract.SearchProviderContractTest;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import com.aresstack.serp4j.testkit.http.RequestMatcher;

import java.util.Optional;

/** Serp4J provider contract for the Argos provider over a synthetic sidecar on a borrowed transport. */
class ArgosSearchProviderContractTest extends SearchProviderContractTest<FakeHttpTransport> {

    private static final String TOKEN = "synthetic-contract-token";

    @Override
    protected FakeHttpTransport createInfrastructure() {
        FakeHttpTransport transport = FakeHttpTransport.create();
        transport.on(McpArgosWebSearchLifecycleTest.rpc("tools/call")).reply(FakeHttpStep.json(200,
                ScriptedTransport.toolResult(0, ArgosSearchOutputParserTest.syntheticFixture(), false)));
        transport.on(RequestMatcher.any()).reply(FakeHttpStep.json(200, ScriptedTransport.initResult(0)));
        return transport;
    }

    @Override
    protected SearchProvider createProvider(ProviderId id, FakeHttpTransport infrastructure) {
        return ArgosSearchProvider.builder(id,
                        McpArgosWebSearch.builder("http://127.0.0.1:47001/mcp/browser/" + TOKEN, infrastructure)
                                .build())
                .ownsSearch(true)
                .build();
    }

    @Override
    protected SearchRequest supportedRequest() {
        return SearchRequest.builder("synthetic contract query").build();
    }

    @Override
    protected Optional<String> secret() {
        return Optional.of(TOKEN);
    }
}
