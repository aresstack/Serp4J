package com.aresstack.serp4j.core.port.credential;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Source of secrets (architecture.md §9). Serp4J never stores secrets; the host decides where they live
 * (environment, encrypted file, vault) and provides them through this port. Providers resolve a secret per call
 * and close the {@link SecretValue} afterwards. Implementations must never log or print secret values.
 *
 * <p>Ownership (ADR-017): a credential provider injected into a search provider belongs to the host.</p>
 */
public interface CredentialProvider {

    /** Resolves a secret; empty when unknown. Each call returns a fresh {@link SecretValue}. */
    Optional<SecretValue> resolve(CredentialKey key);

    /** @throws IllegalStateException if the secret is not configured; the message names the key, never a value */
    default SecretValue require(CredentialKey key) {
        Optional<SecretValue> value = resolve(Preconditions.requireNonNull(key, "key"));
        if (!value.isPresent()) {
            throw new IllegalStateException("credential '" + key + "' is not configured");
        }
        return value.get();
    }

    /** A provider that knows no secrets. */
    static CredentialProvider none() {
        return key -> {
            Preconditions.requireNonNull(key, "key");
            return Optional.empty();
        };
    }

    /**
     * Asks the given providers in order and returns the first secret found. The array is copied; changing it after
     * this call does not affect the chain.
     */
    static CredentialProvider chain(CredentialProvider... providers) {
        final List<CredentialProvider> chain = Collections.unmodifiableList(
                new ArrayList<CredentialProvider>(Arrays.asList(Preconditions.requireNonNull(providers, "providers"))));
        for (CredentialProvider provider : chain) {
            Preconditions.requireNonNull(provider, "provider");
        }
        return key -> {
            Preconditions.requireNonNull(key, "key");
            for (CredentialProvider provider : chain) {
                Optional<SecretValue> value = provider.resolve(key);
                if (value.isPresent()) {
                    return value;
                }
            }
            return Optional.empty();
        };
    }
}
