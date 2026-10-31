package com.aresstack.serp4j.core.port.factory;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.port.credential.CredentialProvider;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Shared infrastructure a {@link SearchProviderFactory} may use (architecture.md §5.1): credentials, a clock and
 * typed services the core does not know (an HTTP transport, a browser runtime, a scheduler). Services are looked up
 * by type so the core stays free of {@code serp4j-http-api} and {@code serp4j-browser-api}.
 *
 * <p>Ownership (ADR-017 rule 5): every service in the environment belongs to whoever built the environment;
 * providers created from it never close them.</p>
 */
public interface ProviderEnvironment {

    /** A service by type; empty when the host registered none. */
    <T> Optional<T> getService(Class<T> type);

    /** @throws IllegalStateException if no service of the type is registered */
    default <T> T requireService(Class<T> type) {
        Optional<T> service = getService(Preconditions.requireNonNull(type, "type"));
        if (!service.isPresent()) {
            throw new IllegalStateException("no service of type " + type.getName() + " in the provider environment");
        }
        return service.get();
    }

    CredentialProvider getCredentials();

    Clock getClock();

    static Builder builder() {
        return new Builder();
    }

    /** Builder for a simple immutable environment; defaults: no credentials, system UTC clock, no services. */
    final class Builder {
        private CredentialProvider credentials = CredentialProvider.none();
        private Clock clock = Clock.systemUTC();
        private final Map<Class<?>, Object> services = new LinkedHashMap<Class<?>, Object>();

        private Builder() {
        }

        public Builder credentials(CredentialProvider credentials) {
            this.credentials = Preconditions.requireNonNull(credentials, "credentials");
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Preconditions.requireNonNull(clock, "clock");
            return this;
        }

        /** Registers a service under the given type; the host keeps ownership of the instance. */
        public <T> Builder service(Class<T> type, T service) {
            Preconditions.requireNonNull(type, "type");
            Preconditions.requireNonNull(service, "service");
            if (!type.isInstance(service)) {
                throw new IllegalArgumentException("service of type " + service.getClass().getName()
                        + " is not a " + type.getName());
            }
            services.put(type, service);
            return this;
        }

        public ProviderEnvironment build() {
            final CredentialProvider credentialProvider = credentials;
            final Clock environmentClock = clock;
            final Map<Class<?>, Object> registered = new LinkedHashMap<Class<?>, Object>(services);
            return new ProviderEnvironment() {
                @Override
                public <T> Optional<T> getService(Class<T> type) {
                    Object service = registered.get(Preconditions.requireNonNull(type, "type"));
                    return service == null ? Optional.<T>empty() : Optional.of(type.cast(service));
                }

                @Override
                public CredentialProvider getCredentials() {
                    return credentialProvider;
                }

                @Override
                public Clock getClock() {
                    return environmentClock;
                }

                @Override
                public String toString() {
                    return "ProviderEnvironment{services=" + registered.keySet() + '}';
                }
            };
        }
    }
}
