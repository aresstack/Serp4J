package com.aresstack.serp4j.provider.brave;

import com.aresstack.serp4j.core.port.ProviderPort;
import com.aresstack.serp4j.core.port.factory.ProviderConfiguration;
import com.aresstack.serp4j.core.port.factory.ProviderEnvironment;
import com.aresstack.serp4j.core.port.factory.SearchProviderFactory;
import com.aresstack.serp4j.http.HttpTransport;

/**
 * {@code ServiceLoader} entry for provider type {@value BraveSearchProvider#PROVIDER_TYPE} (architecture.md §6).
 *
 * <p>Properties (all optional): {@code base-url}, {@code api-version}, {@code country}, {@code search-lang},
 * {@code ui-lang}, {@code timeout} (ISO-8601 duration or as {@link ProviderConfiguration#getDuration} reads it).
 * The token is the secret {@code api-key} ({@code <id>.api-key}, overridable via {@code credential.api-key}),
 * resolved from the environment's {@code CredentialProvider}. The {@link HttpTransport} comes from the
 * environment and stays owned by the host.
 */
public final class BraveSearchProviderFactory implements SearchProviderFactory {

    @Override
    public String getProviderType() {
        return BraveSearchProvider.PROVIDER_TYPE;
    }

    @Override
    public ProviderPort create(ProviderConfiguration configuration, ProviderEnvironment environment) {
        BraveSearchOptions.Builder options = BraveSearchOptions.builder();
        if (configuration.get("base-url").isPresent()) {
            options.baseUrl(configuration.get("base-url").get());
        }
        options.apiVersion(configuration.get("api-version").orElse(null))
                .defaultCountry(configuration.get("country").orElse(null))
                .defaultSearchLanguage(configuration.get("search-lang").orElse(null))
                .defaultUiLanguage(configuration.get("ui-lang").orElse(null));
        if (configuration.getDuration("timeout").isPresent()) {
            options.timeout(configuration.getDuration("timeout").get());
        }
        return BraveSearchProvider.builder()
                .id(configuration.getId())
                .transport(environment.requireService(HttpTransport.class))
                .credentials(environment.getCredentials())
                .credentialKey(configuration.credentialKey(BraveSearchProvider.API_KEY_SECRET))
                .options(options.build())
                .clock(environment.getClock())
                .build();
    }
}
