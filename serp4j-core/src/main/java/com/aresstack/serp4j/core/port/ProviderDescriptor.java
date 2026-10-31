package com.aresstack.serp4j.core.port;

import com.aresstack.serp4j.core.internal.Codes;
import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.port.capability.SearchCapabilities;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable self-description of a provider instance (WP2 §4): the instance id (e.g. {@code brightdata-de}), the
 * provider type (e.g. {@code brightdata}), a display name, the technical {@link ProviderKind} and the
 * {@link SearchCapabilities}.
 *
 * <p>Invariant (WP2 §6.2): {@code getCapabilities().getConversational()} is present if and only if the instance
 * implements {@link com.aresstack.serp4j.core.port.conversation.ConversationalSearchProvider}. The descriptor
 * cannot enforce this itself; registries and the contract tests check it.</p>
 */
public final class ProviderDescriptor {

    private final ProviderId id;
    private final String providerType;
    private final String displayName;
    private final ProviderKind kind;
    private final SearchCapabilities capabilities;
    private final Map<String, String> attributes;

    private ProviderDescriptor(Builder builder) {
        this.id = builder.id;
        this.providerType = builder.providerType;
        this.displayName = builder.displayName != null ? builder.displayName : builder.id.getCode();
        this.kind = builder.kind;
        this.capabilities = builder.capabilities;
        this.attributes = Preconditions.immutableMap(builder.attributes, "attributes");
    }

    /**
     * @param id           unique id of the instance
     * @param providerType code of the provider implementation, format {@code [a-z0-9][a-z0-9._-]*}
     * @param kind         technical kind
     * @param capabilities what the instance supports
     */
    public static Builder builder(ProviderId id, String providerType, ProviderKind kind,
                                  SearchCapabilities capabilities) {
        return new Builder(id, providerType, kind, capabilities);
    }

    public ProviderId getId() {
        return id;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ProviderKind getKind() {
        return kind;
    }

    public SearchCapabilities getCapabilities() {
        return capabilities;
    }

    /** Free-form, non-secret attributes such as {@code vendorUrl} or {@code pricingModel}; unmodifiable. */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(id, providerType, kind, capabilities);
        builder.displayName = displayName;
        builder.attributes.putAll(attributes);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderDescriptor)) {
            return false;
        }
        ProviderDescriptor that = (ProviderDescriptor) o;
        return id.equals(that.id)
                && providerType.equals(that.providerType)
                && displayName.equals(that.displayName)
                && kind == that.kind
                && capabilities.equals(that.capabilities)
                && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, providerType, displayName, kind, capabilities, attributes);
    }

    @Override
    public String toString() {
        return "ProviderDescriptor{id=" + id + ", providerType='" + providerType + "', displayName='" + displayName
                + "', kind=" + kind + '}';
    }

    /** Builder for {@link ProviderDescriptor}. */
    public static final class Builder {
        private final ProviderId id;
        private final String providerType;
        private String displayName;
        private final ProviderKind kind;
        private final SearchCapabilities capabilities;
        private final Map<String, String> attributes = new LinkedHashMap<String, String>();

        private Builder(ProviderId id, String providerType, ProviderKind kind, SearchCapabilities capabilities) {
            this.id = Preconditions.requireNonNull(id, "id");
            this.providerType = Codes.normalize(providerType, "providerType");
            this.kind = Preconditions.requireNonNull(kind, "kind");
            this.capabilities = Preconditions.requireNonNull(capabilities, "capabilities");
        }

        /** Human-readable name; defaults to the id. */
        public Builder displayName(String displayName) {
            this.displayName = Preconditions.trimToNull(displayName);
            return this;
        }

        public Builder attribute(String name, String value) {
            attributes.put(Preconditions.requireText(name, "name"), Preconditions.requireNonNull(value, "value"));
            return this;
        }

        public ProviderDescriptor build() {
            return new ProviderDescriptor(this);
        }
    }
}
