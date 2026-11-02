package com.aresstack.serp4j.provider.argos;

import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.port.ProviderPort;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.SearchProvider;
import com.aresstack.serp4j.core.port.credential.StaticCredentialProvider;
import com.aresstack.serp4j.core.port.factory.ProviderConfiguration;
import com.aresstack.serp4j.core.port.factory.ProviderEnvironment;
import com.aresstack.serp4j.core.port.factory.SearchProviderFactory;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The sidecar is simulated with synthetic answers. */
class ArgosSearchProviderFactoryTest {

    private static ScriptedTransport syntheticSidecar() {
        return new ScriptedTransport(request -> {
            String method = ScriptedTransport.method(request);
            long id = ScriptedTransport.id(request);
            if ("initialize".equals(method)) {
                return ScriptedTransport.json(200, ScriptedTransport.initResult(id));
            }
            if ("notifications/initialized".equals(method)) {
                return HttpResponse.builder(202).build();
            }
            return ScriptedTransport.json(200,
                    ScriptedTransport.toolResult(id, ArgosSearchOutputParserTest.syntheticFixture(), false));
        });
    }

    @Test
    void isFoundByTheServiceLoader() {
        List<String> types = new ArrayList<String>();
        for (SearchProviderFactory factory : ServiceLoader.load(SearchProviderFactory.class)) {
            types.add(factory.getProviderType());
        }
        assertThat(types).contains("argos");
    }

    @Test
    void createsAWorkingProviderFromPortAndTokenCredential() {
        ScriptedTransport transport = syntheticSidecar();
        ProviderConfiguration configuration = ProviderConfiguration.builder("argos")
                .property("port", "47001")
                .property("engines", "duckduckgo, startpage")
                .property("maxResults", "10")
                .property("timeout", "PT20S")
                .build();
        ProviderEnvironment environment = ProviderEnvironment.builder()
                .service(HttpTransport.class, transport)
                .credentials(StaticCredentialProvider.builder()
                        .put(configuration.credentialKey("token"), "synthetic-token".toCharArray()).build())
                .build();

        ProviderPort port = new ArgosSearchProviderFactory().create(configuration, environment);
        SearchResponse response = ((SearchProvider) port).search(SearchRequest.builder("q").build(),
                SearchContext.defaults()).join();
        port.close();

        assertThat(response.getData().getResults()).hasSize(3);
        assertThat(transport.requests.get(0).getUrl()).isEqualTo("http://127.0.0.1:47001/mcp/browser/synthetic-token");
        assertThat(port.getDescriptor().getCapabilities().getTargets()).containsExactly(
                SearchTarget.of(SearchEngineId.DUCKDUCKGO, SearchVertical.WEB),
                SearchTarget.of(SearchEngineId.of("startpage"), SearchVertical.WEB));
        assertThat(port.getDescriptor().getCapabilities().getMaxResultsPerRequest().getAsInt()).isEqualTo(10);
        assertThat(transport.closed).isFalse();
    }

    @Test
    void needsEndpointOrPortAndATransport() {
        ProviderEnvironment withTransport = ProviderEnvironment.builder()
                .service(HttpTransport.class, syntheticSidecar()).build();
        assertThatThrownBy(() -> new ArgosSearchProviderFactory().create(ProviderConfiguration.of("argos"),
                withTransport)).isInstanceOf(IllegalStateException.class).hasMessageContaining("endpoint");
        assertThatThrownBy(() -> new ArgosSearchProviderFactory().create(ProviderConfiguration.builder("argos")
                .property("endpoint", "http://127.0.0.1:1/mcp/browser/t").build(), ProviderEnvironment.builder()
                .build())).isInstanceOf(IllegalStateException.class);
        assertThat(new ArgosSearchProviderFactory().create(ProviderConfiguration.builder("argos")
                .property("endpoint", "http://127.0.0.1:1/mcp/browser/t").build(), withTransport)
                .getDescriptor().getProviderType()).isEqualTo("argos");
    }
}
