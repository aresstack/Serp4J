package com.aresstack.serp4j.provider.serpapi;

import com.aresstack.serp4j.core.port.ProviderPort;
import com.aresstack.serp4j.core.port.factory.ProviderConfiguration;
import com.aresstack.serp4j.core.port.factory.ProviderEnvironment;
import com.aresstack.serp4j.core.port.factory.SearchProviderFactory;
import com.aresstack.serp4j.http.HttpTransport;

/**
 * {@code ServiceLoader} entry for provider type {@value SerpApiSearchProvider#PROVIDER_TYPE} (architecture.md §6).
 *
 * <p>Properties (all optional): {@code base-url}, {@code country}, {@code language}, {@code location},
 * {@code google-domain}, {@code no-cache} ({@code true}/{@code false}), {@code timeout},
 * {@code resolve-google-goto-urls} ({@code true}/{@code false}, default {@code true}), {@code url-resolve-timeout}. The key is the secret
 * {@code api-key} ({@code <id>.api-key}, overridable via {@code credential.api-key}), resolved from the
 * environment's {@code CredentialProvider}. The {@link HttpTransport} comes from the environment and stays owned by
 * the host.
 */
public final class SerpApiSearchProviderFactory implements SearchProviderFactory {

    @Override
    public String getProviderType() {
        return SerpApiSearchProvider.PROVIDER_TYPE;
    }

    @Override
    public ProviderPort create(ProviderConfiguration configuration, ProviderEnvironment environment) {
        SerpApiSearchOptions.Builder options = SerpApiSearchOptions.builder();
        if (configuration.get("base-url").isPresent()) {
            options.baseUrl(configuration.get("base-url").get());
        }
        options.defaultCountry(configuration.get("country").orElse(null))
                .defaultLanguage(configuration.get("language").orElse(null))
                .defaultLocation(configuration.get("location").orElse(null))
                .googleDomain(configuration.get("google-domain").orElse(null));
        if (configuration.get("no-cache").isPresent()) {
            options.noCache(bool(configuration, "no-cache"));
        }
        if (configuration.get("resolve-google-goto-urls").isPresent()) {
            options.resolveGoogleGotoUrls(bool(configuration, "resolve-google-goto-urls"));
        }
        if (configuration.getDuration("url-resolve-timeout").isPresent()) {
            options.urlResolveTimeout(configuration.getDuration("url-resolve-timeout").get());
        }
        if (configuration.getDuration("timeout").isPresent()) {
            options.timeout(configuration.getDuration("timeout").get());
        }
        return SerpApiSearchProvider.builder()
                .id(configuration.getId())
                .transport(environment.requireService(HttpTransport.class))
                .credentials(environment.getCredentials())
                .credentialKey(configuration.credentialKey(SerpApiSearchProvider.API_KEY_SECRET))
                .options(options.build())
                .clock(environment.getClock())
                .build();
    }

    private static boolean bool(ProviderConfiguration configuration, String name) {
        String value = configuration.get(name).get().trim();
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(name + " must be true or false: " + value);
        }
        return Boolean.parseBoolean(value);
    }
}
