package com.aresstack.serp4j.core.port.credential;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves secrets from environment variables. The key {@code brave.api_key} maps to {@code SERP4J_BRAVE_API_KEY}:
 * prefix, then the key upper-cased with every character outside {@code A-Z0-9} replaced by {@code _}. Empty
 * variables count as absent.
 */
public final class EnvironmentCredentialProvider implements CredentialProvider {

    public static final String DEFAULT_PREFIX = "SERP4J_";

    private final String prefix;
    private final Function<String, String> environment;

    private EnvironmentCredentialProvider(String prefix, Function<String, String> environment) {
        this.prefix = prefix;
        this.environment = environment;
    }

    /** Uses {@link System#getenv(String)} and {@link #DEFAULT_PREFIX}. */
    public static EnvironmentCredentialProvider systemEnvironment() {
        return systemEnvironment(DEFAULT_PREFIX);
    }

    public static EnvironmentCredentialProvider systemEnvironment(String prefix) {
        return of(prefix, System::getenv);
    }

    /** @param environment lookup of variable name to value (null when unset); injectable for tests */
    public static EnvironmentCredentialProvider of(String prefix, Function<String, String> environment) {
        return new EnvironmentCredentialProvider(Preconditions.requireNonNull(prefix, "prefix"),
                Preconditions.requireNonNull(environment, "environment"));
    }

    /** The variable name a key is looked up under. */
    public String variableName(CredentialKey key) {
        Preconditions.requireNonNull(key, "key");
        StringBuilder name = new StringBuilder(prefix);
        for (char c : key.getName().toUpperCase(Locale.ROOT).toCharArray()) {
            name.append((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ? c : '_');
        }
        return name.toString();
    }

    @Override
    public Optional<SecretValue> resolve(CredentialKey key) {
        String value = environment.apply(variableName(key));
        return value == null || value.isEmpty() ? Optional.<SecretValue>empty() : Optional.of(SecretValue.of(value));
    }

    @Override
    public String toString() {
        return "EnvironmentCredentialProvider{prefix='" + prefix + "'}";
    }
}
