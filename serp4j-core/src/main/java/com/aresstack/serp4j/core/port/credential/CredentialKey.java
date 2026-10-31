package com.aresstack.serp4j.core.port.credential;

import com.aresstack.serp4j.core.internal.Codes;
import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ProviderId;

/**
 * Name of a secret, e.g. {@code brave.api_key} or {@code brightdata-de.password}. Format
 * {@code [a-z0-9][a-z0-9._-]*}, lower-cased. Convention: {@code <provider id>.<secret name>}.
 */
public final class CredentialKey {

    private final String name;

    private CredentialKey(String name) {
        this.name = name;
    }

    public static CredentialKey of(String name) {
        return new CredentialKey(Codes.normalize(name, "credential key"));
    }

    /** {@code <provider id>.<secret name>}. */
    public static CredentialKey of(ProviderId provider, String secretName) {
        Preconditions.requireNonNull(provider, "provider");
        return of(provider.getCode() + "." + Preconditions.requireText(secretName, "secretName"));
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof CredentialKey && name.equals(((CredentialKey) o).name));
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
