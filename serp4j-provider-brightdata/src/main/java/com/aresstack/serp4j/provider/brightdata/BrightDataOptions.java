package com.aresstack.serp4j.provider.brightdata;

import com.aresstack.serp4j.core.model.ProviderRequestOptions;
import com.aresstack.serp4j.core.model.id.ProviderId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bright-Data-specific options of one request (architecture.md §5.3). They never enter the provider-neutral
 * {@link com.aresstack.serp4j.core.model.SearchRequest}; attach them with
 * {@code SearchRequest.Builder#providerOptions(BrightDataOptions)} for the provider id that should see them.
 *
 * <p>Immutable and thread-safe.</p>
 */
public final class BrightDataOptions implements ProviderRequestOptions {

    private final ProviderId providerId;
    private final String zone;
    private final String proxyCountry;
    private final String uule;
    private final String mobileProfile;
    private final String browser;
    private final String brdJson;
    private final Map<String, String> extraUrlParameters;

    private BrightDataOptions(Builder builder) {
        this.providerId = builder.providerId;
        this.zone = builder.zone;
        this.proxyCountry = builder.proxyCountry;
        this.uule = builder.uule;
        this.mobileProfile = builder.mobileProfile;
        this.browser = builder.browser;
        this.brdJson = builder.brdJson;
        this.extraUrlParameters = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.extraUrlParameters));
    }

    /** New builder for options addressed to the provider instance {@code providerId}; all options are unset. */
    public static Builder builder(ProviderId providerId) {
        return new Builder(providerId);
    }

    @Override
    public ProviderId getProviderId() {
        return providerId;
    }

    /** Zone overriding the provider's configured zone for this request. */
    public Optional<String> getZone() {
        return Optional.ofNullable(zone);
    }

    /**
     * Proxy country ({@code country} in the request body, ISO 3166-1 alpha-2). This is where the request exits, not
     * Google's country context ({@code gl}), which comes from the request locale.
     */
    public Optional<String> getProxyCountry() {
        return Optional.ofNullable(proxyCountry);
    }

    /** Raw {@code uule} value, e.g. a canonical name from Bright Data's list; wins over the locale's location. */
    public Optional<String> getUule() {
        return Optional.ofNullable(uule);
    }

    /** Raw {@code brd_mobile} value ({@code 0}, {@code 1}, {@code ios}, {@code ipad}, {@code android}, …); wins over the device. */
    public Optional<String> getMobileProfile() {
        return Optional.ofNullable(mobileProfile);
    }

    /** {@code brd_browser} value ({@code chrome}, {@code safari}, {@code firefox}). */
    public Optional<String> getBrowser() {
        return Optional.ofNullable(browser);
    }

    /**
     * {@code brd_json} URL parameter. Unset by default: the provider asks for {@code data_format=parsed}, which the
     * recorded response shows to be sufficient (E-2).
     */
    public Optional<String> getBrdJson() {
        return Optional.ofNullable(brdJson);
    }

    /** Further Google URL parameters, appended as given after the ones the provider derives. */
    public Map<String, String> getExtraUrlParameters() {
        return extraUrlParameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BrightDataOptions)) {
            return false;
        }
        BrightDataOptions that = (BrightDataOptions) o;
        return providerId.equals(that.providerId) && Objects.equals(zone, that.zone) && Objects.equals(proxyCountry, that.proxyCountry)
                && Objects.equals(uule, that.uule) && Objects.equals(mobileProfile, that.mobileProfile)
                && Objects.equals(browser, that.browser) && Objects.equals(brdJson, that.brdJson)
                && extraUrlParameters.equals(that.extraUrlParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, zone, proxyCountry, uule, mobileProfile, browser, brdJson, extraUrlParameters);
    }

    @Override
    public String toString() {
        return "BrightDataOptions{providerId=" + providerId + ", zone=" + zone + ", proxyCountry=" + proxyCountry + ", uule=" + uule
                + ", mobileProfile=" + mobileProfile + ", browser=" + browser + ", brdJson=" + brdJson
                + ", extraUrlParameters=" + extraUrlParameters.keySet() + '}';
    }

    /** Builder for {@link BrightDataOptions}. */
    public static final class Builder {

        private final ProviderId providerId;
        private String zone;
        private String proxyCountry;
        private String uule;
        private String mobileProfile;
        private String browser;
        private String brdJson;
        private final Map<String, String> extraUrlParameters = new LinkedHashMap<String, String>();

        private Builder(ProviderId providerId) {
            this.providerId = Objects.requireNonNull(providerId, "providerId");
        }

        /** See {@link BrightDataOptions#getZone()}. */
        public Builder zone(String zone) {
            this.zone = text(zone, "zone");
            return this;
        }

        /** See {@link BrightDataOptions#getProxyCountry()}. */
        public Builder proxyCountry(String proxyCountry) {
            String value = text(proxyCountry, "proxyCountry");
            if (!value.matches("[A-Za-z]{2}")) {
                throw new IllegalArgumentException("proxyCountry must be a two-letter country code but was '" + value + "'");
            }
            this.proxyCountry = value.toUpperCase(java.util.Locale.ROOT);
            return this;
        }

        /** See {@link BrightDataOptions#getUule()}. */
        public Builder uule(String uule) {
            this.uule = text(uule, "uule");
            return this;
        }

        /** See {@link BrightDataOptions#getMobileProfile()}. */
        public Builder mobileProfile(String mobileProfile) {
            this.mobileProfile = text(mobileProfile, "mobileProfile");
            return this;
        }

        /** See {@link BrightDataOptions#getBrowser()}. */
        public Builder browser(String browser) {
            this.browser = text(browser, "browser");
            return this;
        }

        /** See {@link BrightDataOptions#getBrdJson()}. */
        public Builder brdJson(String brdJson) {
            this.brdJson = text(brdJson, "brdJson");
            return this;
        }

        /** Adds one further Google URL parameter; see {@link BrightDataOptions#getExtraUrlParameters()}. */
        public Builder extraUrlParameter(String name, String value) {
            extraUrlParameters.put(text(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /** Builds the immutable options. */
        public BrightDataOptions build() {
            return new BrightDataOptions(this);
        }

        private static String text(String value, String name) {
            Objects.requireNonNull(value, name);
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return trimmed;
        }
    }
}
