package com.aresstack.serp4j.core.contract;

import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.core.testsupport.FakeSearchProvider;
import com.aresstack.serp4j.core.testsupport.FakeSharedResource;

/** Runs the provider contract against the reference fake; shows how an adapter module plugs in. */
class FakeSearchProviderContractTest extends SearchProviderContract {

    @Override
    protected SearchProvider createProvider(ProviderId id, FakeSharedResource shared) {
        return new FakeSearchProvider(id, shared);
    }

    @Override
    protected SearchRequest supportedRequest() {
        return SearchRequest.builder("serp4j contract").engine(SearchEngineId.GOOGLE).build();
    }
}
