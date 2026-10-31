package com.aresstack.serp4j.core.port.credential;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory credentials for tests and simple hosts. Values are copied on construction; {@link #close()} wipes
 * them. Each {@link #resolve} returns a fresh {@link SecretValue} the caller closes.
 */
public final class StaticCredentialProvider implements CredentialProvider, AutoCloseable {

    private final Map<CredentialKey, char[]> secrets;

    private StaticCredentialProvider(Map<CredentialKey, char[]> secrets) {
        this.secrets = secrets;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static StaticCredentialProvider of(CredentialKey key, char[] secret) {
        return builder().put(key, secret).build();
    }

    public static StaticCredentialProvider of(CredentialKey key, String secret) {
        return builder().put(key, secret).build();
    }

    @Override
    public Optional<SecretValue> resolve(CredentialKey key) {
        char[] value = secrets.get(Preconditions.requireNonNull(key, "key"));
        return value == null ? Optional.<SecretValue>empty() : Optional.of(SecretValue.of(value));
    }

    public Set<CredentialKey> getKeys() {
        return Collections.unmodifiableSet(secrets.keySet());
    }

    /** Wipes all secrets; later lookups find nothing. */
    @Override
    public void close() {
        for (char[] value : secrets.values()) {
            Arrays.fill(value, '\0');
        }
        secrets.clear();
    }

    @Override
    public String toString() {
        return "StaticCredentialProvider{keys=" + secrets.keySet() + '}';
    }

    /** Builder for {@link StaticCredentialProvider}. */
    public static final class Builder {
        private final Map<CredentialKey, char[]> secrets = new LinkedHashMap<CredentialKey, char[]>();

        private Builder() {
        }

        public Builder put(CredentialKey key, char[] secret) {
            secrets.put(Preconditions.requireNonNull(key, "key"), Preconditions.requireNonNull(secret, "secret").clone());
            return this;
        }

        public Builder put(CredentialKey key, String secret) {
            return put(key, Preconditions.requireNonNull(secret, "secret").toCharArray());
        }

        public Builder put(CredentialKey key, SecretValue secret) {
            return put(key, Preconditions.requireNonNull(secret, "secret").getChars());
        }

        public StaticCredentialProvider build() {
            return new StaticCredentialProvider(new LinkedHashMap<CredentialKey, char[]>(secrets));
        }
    }
}
