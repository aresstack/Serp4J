package com.aresstack.serp4j.provider.argos;

import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.port.ProviderPort;
import com.aresstack.serp4j.core.port.credential.SecretValue;
import com.aresstack.serp4j.core.port.factory.ProviderConfiguration;
import com.aresstack.serp4j.core.port.factory.ProviderEnvironment;
import com.aresstack.serp4j.core.port.factory.SearchProviderFactory;
import com.aresstack.serp4j.http.HttpTransport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Plugin entry point (registered in {@code META-INF/services}) for provider type {@code argos}.
 *
 * <p>Properties of the {@link ProviderConfiguration}:</p>
 * <ul>
 *   <li>{@code endpoint}: full MCP endpoint of the running AskAI/Argos browser sidecar, e.g.
 *       {@code http://127.0.0.1:47001/mcp/browser/<token>}; <em>or</em></li>
 *   <li>{@code port}: loopback port of the sidecar; the token then comes from the credential {@code token}
 *       ({@link ProviderConfiguration#credentialKey(String)}), the endpoint is
 *       {@code http://127.0.0.1:<port>/mcp/browser/<token>}.</li>
 *   <li>{@code timeout} (optional, ISO-8601, default {@code PT90S}), {@code initializeTimeout} (optional, ISO-8601, default
 *       {@code PT30S}, budget of the shared MCP initialization), {@code engines} (optional, comma separated,
 *       default {@code duckduckgo,bing}), {@code maxResults} (optional, default 50), {@code displayName}.</li>
 * </ul>
 *
 * <p>The {@link HttpTransport} comes from the {@link ProviderEnvironment} and stays the host's (ADR-017). The created
 * provider owns the {@link McpArgosWebSearch} it was given. The factory does not start the sidecar; that remains the
 * job of the host (in AskAI: {@code BrowserMcpSidecarProcess}).</p>
 */
public final class ArgosSearchProviderFactory implements SearchProviderFactory {

    public static final String ENDPOINT = "endpoint";
    public static final String PORT = "port";
    public static final String TOKEN_CREDENTIAL = "token";
    public static final String TIMEOUT = "timeout";
    public static final String INITIALIZE_TIMEOUT = "initializeTimeout";
    public static final String ENGINES = "engines";
    public static final String MAX_RESULTS = "maxResults";
    public static final String DISPLAY_NAME = "displayName";

    @Override
    public String getProviderType() {
        return ArgosSearchProvider.PROVIDER_TYPE;
    }

    @Override
    public ProviderPort create(ProviderConfiguration configuration, ProviderEnvironment environment) {
        if (configuration == null) {
            throw new NullPointerException("configuration must not be null");
        }
        if (environment == null) {
            throw new NullPointerException("environment must not be null");
        }
        HttpTransport transport = environment.requireService(HttpTransport.class);
        McpArgosWebSearch.Builder search = McpArgosWebSearch.builder(endpoint(configuration, environment), transport);
        Optional<Duration> timeout = configuration.getDuration(TIMEOUT);
        if (timeout.isPresent()) {
            search.timeout(timeout.get());
        }
        Optional<Duration> initializeTimeout = configuration.getDuration(INITIALIZE_TIMEOUT);
        if (initializeTimeout.isPresent()) {
            search.initializeTimeout(initializeTimeout.get());
        }
        ArgosSearchProvider.Builder provider = ArgosSearchProvider.builder(configuration.getId(), search.build())
                .ownsSearch(true)
                .clock(environment.getClock());
        Optional<String> engines = configuration.get(ENGINES);
        if (engines.isPresent()) {
            List<SearchEngineId> ids = new ArrayList<SearchEngineId>();
            for (String engine : engines.get().split(",")) {
                if (!engine.trim().isEmpty()) {
                    ids.add(SearchEngineId.of(engine.trim()));
                }
            }
            provider.engines(ids);
        }
        OptionalInt maxResults = configuration.getInt(MAX_RESULTS);
        if (maxResults.isPresent()) {
            provider.maxResults(maxResults.getAsInt());
        }
        Optional<String> displayName = configuration.get(DISPLAY_NAME);
        if (displayName.isPresent()) {
            provider.displayName(displayName.get());
        }
        return provider.build();
    }

    private static String endpoint(ProviderConfiguration configuration, ProviderEnvironment environment) {
        Optional<String> endpoint = configuration.get(ENDPOINT);
        if (endpoint.isPresent()) {
            return endpoint.get();
        }
        OptionalInt port = configuration.getInt(PORT);
        if (!port.isPresent()) {
            throw new IllegalStateException("provider '" + configuration.getId() + "' needs property '" + ENDPOINT
                    + "' or '" + PORT + "'");
        }
        if (port.getAsInt() < 1 || port.getAsInt() > 65535) {
            throw new IllegalStateException("property '" + PORT + "' of provider '" + configuration.getId()
                    + "' must be a TCP port but was " + port.getAsInt());
        }
        SecretValue token = environment.getCredentials().require(configuration.credentialKey(TOKEN_CREDENTIAL));
        try {
            return "http://127.0.0.1:" + port.getAsInt() + "/mcp/browser/" + new String(token.getChars());
        } finally {
            token.close();
        }
    }
}
