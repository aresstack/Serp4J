package com.aresstack.serp4j.provider.serpapi;

import com.aresstack.serp4j.core.model.ProviderRequestOptions;
import com.aresstack.serp4j.core.model.id.ProviderId;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * SerpApi-specific options of one request (ADR-006), stored in {@code SearchRequest} under the provider instance
 * id: {@code SearchRequest.builder(q).providerOptions(SerpApiRequestOptions.builder(id).noCache(true).build())}.
 *
 * <p>Unset values are not sent, so the instance defaults or SerpApi's own defaults apply.
 */
public final class SerpApiRequestOptions implements ProviderRequestOptions {

    /** Value of {@code device}. */
    public enum Device {
        DESKTOP, TABLET, MOBILE;

        String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Parameters the adapter derives itself; {@link Builder#parameter} rejects them, so an extra parameter can never
     * replace the key, the engine, the query or the paging.
     */
    public static final Set<String> RESERVED_PARAMETERS = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("api_key", "engine", "q", "output", "start", "num", "first", "count", "async",
                    "no_cache", "device", "location", "google_domain", "gl", "hl", "cc", "mkt", "safe",
                    "safesearch", "tbs", "filters")));

    private final ProviderId providerId;
    private final Boolean noCache;
    private final Device device;
    private final String location;
    private final String googleDomain;
    private final Map<String, String> parameters;

    private SerpApiRequestOptions(Builder builder) {
        this.providerId = builder.providerId;
        this.noCache = builder.noCache;
        this.device = builder.device;
        this.location = builder.location;
        this.googleDomain = builder.googleDomain;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.parameters));
    }

    public static Builder builder(ProviderId providerId) {
        return new Builder(Objects.requireNonNull(providerId, "providerId"));
    }

    @Override
    public ProviderId getProviderId() {
        return providerId;
    }

    /** {@code no_cache}; overrides {@link SerpApiSearchOptions#isNoCache()} for this request. */
    public Optional<Boolean> getNoCache() {
        return Optional.ofNullable(noCache);
    }

    public Optional<Device> getDevice() {
        return Optional.ofNullable(device);
    }

    /** SerpApi {@code location}, e.g. {@code "Austin, Texas, United States"}; wins over the request's GeoLocation. */
    public Optional<String> getLocation() {
        return Optional.ofNullable(location);
    }

    /** Google domain for this request, e.g. {@code google.at}. Ignored for Bing. */
    public Optional<String> getGoogleDomain() {
        return Optional.ofNullable(googleDomain);
    }

    /** Further engine parameters sent unchanged (e.g. {@code lr}, {@code uule}, {@code nfpr}). */
    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SerpApiRequestOptions)) {
            return false;
        }
        SerpApiRequestOptions that = (SerpApiRequestOptions) o;
        return providerId.equals(that.providerId) && Objects.equals(noCache, that.noCache) && device == that.device
                && Objects.equals(location, that.location) && Objects.equals(googleDomain, that.googleDomain)
                && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, noCache, device, location, googleDomain, parameters);
    }

    @Override
    public String toString() {
        return "SerpApiRequestOptions{provider=" + providerId + ", noCache=" + noCache + ", device=" + device
                + ", location=" + location + ", googleDomain=" + googleDomain + ", parameters=" + parameters + '}';
    }

    public static final class Builder {
        private final ProviderId providerId;
        private Boolean noCache;
        private Device device;
        private String location;
        private String googleDomain;
        private final Map<String, String> parameters = new LinkedHashMap<String, String>();

        private Builder(ProviderId providerId) {
            this.providerId = providerId;
        }

        public Builder noCache(boolean noCache) {
            this.noCache = noCache;
            return this;
        }

        public Builder device(Device device) {
            this.device = device;
            return this;
        }

        public Builder location(String location) {
            this.location = location == null || location.trim().isEmpty() ? null : location.trim();
            return this;
        }

        public Builder googleDomain(String googleDomain) {
            String domain = googleDomain == null || googleDomain.trim().isEmpty() ? null : googleDomain.trim();
            if (domain != null && !domain.matches("([a-z0-9-]+\\.)*google\\.[a-z.]{2,10}")) {
                throw new IllegalArgumentException("googleDomain must be a Google domain like google.de: " + domain);
            }
            this.googleDomain = domain;
            return this;
        }

        /**
         * Adds an engine parameter the adapter does not model.
         *
         * @throws IllegalArgumentException for a blank or malformed name or one of {@link #RESERVED_PARAMETERS}
         */
        public Builder parameter(String name, String value) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            String key = name.trim();
            if (!key.matches("[A-Za-z][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("parameter name must be alphanumeric: " + name);
            }
            if (RESERVED_PARAMETERS.contains(key.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("parameter '" + key
                        + "' is set by the adapter and cannot be passed as an extra parameter");
            }
            parameters.put(key, value);
            return this;
        }

        public SerpApiRequestOptions build() {
            return new SerpApiRequestOptions(this);
        }
    }
}
