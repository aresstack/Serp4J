package com.aresstack.serp4j.core.port.factory;

import com.aresstack.serp4j.core.port.ProviderPort;

/**
 * Creates provider instances from configuration (WP2 §8.2). Discoverable through
 * {@link java.util.ServiceLoader} ({@code META-INF/services/com.aresstack.serp4j.core.port.factory.SearchProviderFactory}),
 * so hosts can configure providers without knowing adapter classes.
 *
 * <p>Ownership (ADR-017): infrastructure the factory takes from the {@link ProviderEnvironment} is borrowed; the
 * created provider must not close it. Infrastructure the factory creates itself belongs to the created provider.</p>
 */
public interface SearchProviderFactory {

    /** The provider type this factory builds, e.g. {@code brave}; matches {@code ProviderConfiguration#getProviderType()}. */
    String getProviderType();

    /**
     * Creates a provider. Validates the configuration eagerly and fails fast with {@link IllegalStateException} or
     * {@link IllegalArgumentException} for missing or invalid settings; must not perform network calls.
     *
     * @return a {@link com.aresstack.serp4j.core.port.SearchProvider}, a
     * {@link com.aresstack.serp4j.core.port.conversation.ConversationalSearchProvider} or both
     */
    ProviderPort create(ProviderConfiguration configuration, ProviderEnvironment environment);
}
