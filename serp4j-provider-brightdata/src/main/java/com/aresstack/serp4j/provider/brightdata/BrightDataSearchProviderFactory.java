package com.aresstack.serp4j.provider.brightdata;

import com.aresstack.serp4j.core.port.ProviderPort;
import com.aresstack.serp4j.core.port.factory.ProviderConfiguration;
import com.aresstack.serp4j.core.port.factory.ProviderEnvironment;
import com.aresstack.serp4j.core.port.factory.SearchProviderFactory;
import com.aresstack.serp4j.http.HttpTransport;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link SearchProviderFactory} for provider type {@code brightdata}, registered for {@link java.util.ServiceLoader}.
 *
 * <p>Properties: {@code zone} (required), {@code apiUrl}, {@code googleSearchUrl}, {@code requestTimeout}
 * (ISO-8601 duration). The API key is the credential {@code apiKey} of the configuration
 * ({@link ProviderConfiguration#credentialKey(String)}). The {@link HttpTransport} comes from the environment and is
 * borrowed (ADR-017).</p>
 */
public final class BrightDataSearchProviderFactory implements SearchProviderFactory {

    /** Public no-argument constructor for {@link java.util.ServiceLoader}. */
    public BrightDataSearchProviderFactory() {
    }

    @Override
    public String getProviderType() {
        return BrightDataSearchProvider.PROVIDER_TYPE;
    }

    @Override
    public ProviderPort create(ProviderConfiguration configuration, ProviderEnvironment environment) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(environment, "environment");
        if (!BrightDataSearchProvider.PROVIDER_TYPE.equals(configuration.getProviderType())) {
            throw new IllegalArgumentException("configuration is for provider type '"
                    + configuration.getProviderType() + "', not '" + BrightDataSearchProvider.PROVIDER_TYPE + "'");
        }
        BrightDataSearchProvider.Builder builder = BrightDataSearchProvider.builder(configuration.getId())
                .transport(environment.requireService(HttpTransport.class))
                .credentials(environment.getCredentials())
                .apiKey(configuration.credentialKey(BrightDataSearchProvider.API_KEY))
                .zone(configuration.getRequired("zone"))
                .clock(environment.getClock());
        Optional<String> apiUrl = configuration.get("apiUrl");
        if (apiUrl.isPresent()) {
            builder.apiUrl(apiUrl.get());
        }
        Optional<String> googleSearchUrl = configuration.get("googleSearchUrl");
        if (googleSearchUrl.isPresent()) {
            builder.googleSearchUrl(googleSearchUrl.get());
        }
        Optional<Duration> timeout = configuration.getDuration("requestTimeout");
        if (timeout.isPresent()) {
            builder.requestTimeout(timeout.get());
        }
        return builder.build();
    }
}
