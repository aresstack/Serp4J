package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.port.ProviderPort;
import com.aresstack.serp4j.core.port.factory.ProviderConfiguration;
import com.aresstack.serp4j.core.port.factory.ProviderEnvironment;
import com.aresstack.serp4j.core.port.factory.SearchProviderFactory;
import com.aresstack.serp4j.http.HttpTransport;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@link SearchProviderFactory} for provider type {@code dataforseo} (registered via {@link java.util.ServiceLoader}).
 *
 * <p>Properties: {@code baseUrl}, {@code function} ({@code regular}|{@code advanced}), {@code defaultLocationCode},
 * {@code defaultLanguageCode}. Credentials: {@code login} and {@code password} via
 * {@link ProviderConfiguration#credentialKey(String)}. The environment must provide an {@link HttpTransport}.</p>
 */
public final class DataForSeoSearchProviderFactory implements SearchProviderFactory {

    @Override
    public String getProviderType() {
        return DataForSeoSearchProvider.PROVIDER_TYPE;
    }

    @Override
    public ProviderPort create(ProviderConfiguration configuration, ProviderEnvironment environment) {
        DataForSeoSearchProvider.Builder b = DataForSeoSearchProvider
                .builder(environment.requireService(HttpTransport.class), environment.getCredentials())
                .id(configuration.getId())
                .clock(environment.getClock())
                .loginKey(configuration.credentialKey("login"))
                .passwordKey(configuration.credentialKey("password"));
        Optional<String> baseUrl = configuration.get("baseUrl");
        if (baseUrl.isPresent()) {
            b.baseUrl(baseUrl.get());
        }
        Optional<String> function = configuration.get("function");
        if (function.isPresent()) {
            b.function(DataForSeoFunction.valueOf(function.get().trim().toUpperCase(Locale.ROOT)));
        }
        OptionalInt location = configuration.getInt("defaultLocationCode");
        if (location.isPresent()) {
            b.defaultLocationCode(location.getAsInt());
        }
        Optional<String> language = configuration.get("defaultLanguageCode");
        if (language.isPresent()) {
            b.defaultLanguageCode(language.get());
        }
        return b.build();
    }
}
