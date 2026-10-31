package com.aresstack.serp4j.core.port.factory;

import com.aresstack.serp4j.core.internal.Codes;
import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.port.credential.CredentialKey;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Flat, generic configuration of one provider instance (WP2 §8.2): instance id, provider type and string
 * properties. Typing and validation happen in the adapter. Properties never hold secrets; those are resolved
 * through the {@link ProviderEnvironment#getCredentials() credential provider} under
 * {@link #credentialKey(String)}.
 */
public final class ProviderConfiguration {

    private static final String CREDENTIAL_PROPERTY_PREFIX = "credential.";

    private final ProviderId id;
    private final String providerType;
    private final Map<String, String> properties;

    private ProviderConfiguration(Builder builder) {
        this.id = builder.id != null ? builder.id : ProviderId.of(builder.providerType);
        this.providerType = builder.providerType;
        this.properties = Preconditions.immutableMap(builder.properties, "properties");
    }

    /** @param providerType code of the provider implementation, e.g. {@code brave} */
    public static Builder builder(String providerType) {
        return new Builder(providerType);
    }

    /** Configuration without properties whose instance id equals the provider type. */
    public static ProviderConfiguration of(String providerType) {
        return builder(providerType).build();
    }

    /** Unique id of the instance; defaults to the provider type. */
    public ProviderId getId() {
        return id;
    }

    public String getProviderType() {
        return providerType;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public Optional<String> get(String name) {
        return Optional.ofNullable(properties.get(Preconditions.requireText(name, "name")));
    }

    /** @throws IllegalStateException if the property is missing */
    public String getRequired(String name) {
        Optional<String> value = get(name);
        if (!value.isPresent()) {
            throw new IllegalStateException("provider '" + id + "' needs property '" + name + "'");
        }
        return value.get();
    }

    /** @throws IllegalStateException if the value is not an integer */
    public OptionalInt getInt(String name) {
        Optional<String> value = get(name);
        if (!value.isPresent()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value.get().trim()));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("property '" + name + "' of provider '" + id
                    + "' must be an integer but was '" + value.get() + "'", e);
        }
    }

    /** Accepts {@code true}/{@code false} (case-insensitive). */
    public Optional<Boolean> getBoolean(String name) {
        Optional<String> value = get(name);
        if (!value.isPresent()) {
            return Optional.empty();
        }
        String text = value.get().trim();
        if (text.equalsIgnoreCase("true")) {
            return Optional.of(Boolean.TRUE);
        }
        if (text.equalsIgnoreCase("false")) {
            return Optional.of(Boolean.FALSE);
        }
        throw new IllegalStateException("property '" + name + "' of provider '" + id
                + "' must be true or false but was '" + text + "'");
    }

    /** Accepts ISO-8601 durations such as {@code PT30S}. */
    public Optional<Duration> getDuration(String name) {
        Optional<String> value = get(name);
        if (!value.isPresent()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.parse(value.get().trim()));
        } catch (RuntimeException e) {
            throw new IllegalStateException("property '" + name + "' of provider '" + id
                    + "' must be an ISO-8601 duration but was '" + value.get() + "'", e);
        }
    }

    /**
     * The credential key for a named secret of this instance: {@code <id>.<name>} by default, overridable with the
     * property {@code credential.<name>} so several instances can share one secret.
     */
    public CredentialKey credentialKey(String name) {
        Preconditions.requireText(name, "name");
        String override = properties.get(CREDENTIAL_PROPERTY_PREFIX + name);
        return override != null ? CredentialKey.of(override) : CredentialKey.of(id, name);
    }

    public Builder toBuilder() {
        Builder builder = new Builder(providerType);
        builder.id = id;
        builder.properties.putAll(properties);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderConfiguration)) {
            return false;
        }
        ProviderConfiguration that = (ProviderConfiguration) o;
        return id.equals(that.id) && providerType.equals(that.providerType) && properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, providerType, properties);
    }

    @Override
    public String toString() {
        return "ProviderConfiguration{id=" + id + ", providerType='" + providerType + "', properties="
                + properties.keySet() + '}';
    }

    /** Builder for {@link ProviderConfiguration}. */
    public static final class Builder {
        private ProviderId id;
        private final String providerType;
        private final Map<String, String> properties = new LinkedHashMap<String, String>();

        private Builder(String providerType) {
            this.providerType = Codes.normalize(providerType, "providerType");
        }

        public Builder id(ProviderId id) {
            this.id = id;
            return this;
        }

        public Builder property(String name, String value) {
            properties.put(Preconditions.requireText(name, "name"), Preconditions.requireNonNull(value, "value"));
            return this;
        }

        public Builder properties(Map<String, String> properties) {
            for (Map.Entry<String, String> entry : Preconditions.requireNonNull(properties, "properties").entrySet()) {
                property(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public ProviderConfiguration build() {
            return new ProviderConfiguration(this);
        }
    }
}
