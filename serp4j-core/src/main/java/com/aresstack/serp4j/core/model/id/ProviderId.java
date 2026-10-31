package com.aresstack.serp4j.core.model.id;

import com.aresstack.serp4j.core.internal.Codes;

/**
 * Identifier of a provider <em>instance</em>, for example {@code brave} or {@code brightdata-de}.
 *
 * <p>Open value type (ADR-004): any code matching {@code [a-z0-9][a-z0-9._-]*} is valid; codes are
 * lower-cased. The core defines no provider constants, because the domain model does not know any
 * provider. Provider modules publish their default identifiers; the provider <em>type</em> (for example
 * {@code brightdata}) is carried by {@code ProviderDescriptor#getProviderType()}. Several instances of
 * the same provider type receive distinct identifiers.</p>
 *
 * <p>There is no pseudo identifier for merged responses: a merged {@code SearchMetadata} has no
 * provider and lists all contributing providers instead (ADR-008).</p>
 */
public final class ProviderId {

    private final String code;

    private ProviderId(String code) {
        this.code = code;
    }

    /** Creates an identifier from its code; the code is trimmed and lower-cased. */
    public static ProviderId of(String code) {
        return new ProviderId(Codes.normalize(code, "provider id"));
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ProviderId && code.equals(((ProviderId) o).code));
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
