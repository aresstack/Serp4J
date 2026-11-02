package com.aresstack.serp4j.provider.brave;

import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.port.ProviderPort;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.credential.CredentialKey;
import com.aresstack.serp4j.core.port.credential.SecretValue;
import com.aresstack.serp4j.core.port.factory.ProviderConfiguration;
import com.aresstack.serp4j.core.port.factory.ProviderEnvironment;
import com.aresstack.serp4j.core.port.factory.SearchProviderFactory;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BraveSearchProviderFactoryTest {

    @Test
    void serviceLoaderFindsTheFactoryAndItBuildsAWorkingProvider() throws Exception {
        SearchProviderFactory factory = null;
        for (SearchProviderFactory candidate : ServiceLoader.load(SearchProviderFactory.class)) {
            if (candidate.getProviderType().equals("brave")) {
                factory = candidate;
            }
        }
        assertThat(factory).isInstanceOf(BraveSearchProviderFactory.class);

        FakeHttpTransport transport = FakeHttpTransport.create()
                .onAnyRequest(FakeHttpStep.fixture(Fixtures.load("brave", "web-search-synthetic.json")));
        ProviderConfiguration configuration = ProviderConfiguration.builder("brave")
                .id(ProviderId.of("brave-de"))
                .property("country", "DE")
                .property("search-lang", "de")
                .build();
        ProviderEnvironment environment = ProviderEnvironment.builder()
                .service(HttpTransport.class, transport)
                .credentials(key -> key.equals(CredentialKey.of("brave-de.api-key"))
                        ? Optional.of(SecretValue.of("factory-token")) : Optional.<SecretValue>empty())
                .build();

        try (ProviderPort port = factory.create(configuration, environment)) {
            BraveSearchProvider provider = (BraveSearchProvider) port;
            assertEquals(ProviderId.of("brave-de"), provider.getDescriptor().getId());
            provider.search(SearchRequest.builder("q").build(), SearchContext.defaults()).get(5, TimeUnit.SECONDS);
        }
        assertThat(transport.getLastRequest().getUrl()).contains("country=DE").contains("search_lang=de");
        assertEquals(Optional.of("factory-token"),
                transport.getLastRequest().getHeaders().getFirst("X-Subscription-Token"));
        assertThat(transport.isClosed()).isFalse();
    }
}
