package com.aresstack.serp4j.provider.serpapi;

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

class SerpApiSearchProviderFactoryTest {

    @Test
    void serviceLoaderFindsTheFactoryAndItBuildsAWorkingProvider() throws Exception {
        SearchProviderFactory factory = null;
        for (SearchProviderFactory candidate : ServiceLoader.load(SearchProviderFactory.class)) {
            if (candidate.getProviderType().equals("serpapi")) {
                factory = candidate;
            }
        }
        assertThat(factory).isInstanceOf(SerpApiSearchProviderFactory.class);

        FakeHttpTransport transport = FakeHttpTransport.create()
                .onAnyRequest(FakeHttpStep.fixture(Fixtures.load("serpapi", "google-web-synthetic.json")));
        ProviderConfiguration configuration = ProviderConfiguration.builder("serpapi")
                .id(ProviderId.of("serpapi-de"))
                .property("country", "DE")
                .property("language", "de")
                .build();
        ProviderEnvironment environment = ProviderEnvironment.builder()
                .service(HttpTransport.class, transport)
                .credentials(key -> key.equals(CredentialKey.of("serpapi-de.api-key"))
                        ? Optional.of(SecretValue.of("factory-key")) : Optional.<SecretValue>empty())
                .build();

        try (ProviderPort port = factory.create(configuration, environment)) {
            SerpApiSearchProvider provider = (SerpApiSearchProvider) port;
            assertEquals(ProviderId.of("serpapi-de"), provider.getDescriptor().getId());
            provider.search(SearchRequest.builder("q").build(), SearchContext.defaults()).get(5, TimeUnit.SECONDS);
        }
        assertThat(transport.getLastRequest().getUrl()).contains("gl=de").contains("hl=de").contains("api_key=factory-key");
        assertThat(transport.getLastRequest().getRedactedUrl()).doesNotContain("factory-key");
        assertThat(transport.isClosed()).isFalse();
    }
}
