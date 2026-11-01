package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.model.ProviderRequestOptions;
import com.aresstack.serp4j.core.model.id.ProviderId;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * DataForSEO-specific request options (API reference §4.8). Everything here is optional; anything that may cost
 * extra (e.g. a higher depth) is only sent when set explicitly.
 */
public final class DataForSeoOptions implements ProviderRequestOptions {

    private final ProviderId providerId;
    private final Integer locationCode;
    private final String locationName;
    private final String languageName;
    private final String seDomain;
    private final String os;
    private final DataForSeoFunction function;
    private final String tag;

    private DataForSeoOptions(Builder b) {
        this.providerId = b.providerId;
        this.locationCode = b.locationCode;
        this.locationName = b.locationName;
        this.languageName = b.languageName;
        this.seDomain = b.seDomain;
        this.os = b.os;
        this.function = b.function;
        this.tag = b.tag;
    }

    /** Options addressed to the provider with the given id. */
    public static Builder builder(ProviderId providerId) {
        return new Builder(providerId);
    }

    @Override
    public ProviderId getProviderId() {
        return providerId;
    }

    /** DataForSEO {@code location_code}; wins over {@link #getLocationName()} and the request locale. */
    public OptionalInt getLocationCode() {
        return locationCode == null ? OptionalInt.empty() : OptionalInt.of(locationCode);
    }

    /** DataForSEO {@code location_name}, e.g. {@code "Germany"} or {@code "Berlin,Berlin,Germany"}. */
    public Optional<String> getLocationName() {
        return Optional.ofNullable(locationName);
    }

    /** DataForSEO {@code language_name}; only used when the request has no language code. */
    public Optional<String> getLanguageName() {
        return Optional.ofNullable(languageName);
    }

    /** {@code se_domain} (Google only), e.g. {@code google.de}. */
    public Optional<String> getSeDomain() {
        return Optional.ofNullable(seDomain);
    }

    /** {@code os}: {@code windows}/{@code macos} for desktop, {@code android}/{@code ios} for mobile. */
    public Optional<String> getOs() {
        return Optional.ofNullable(os);
    }

    /** Result function for this request; overrides the provider default. */
    public Optional<DataForSeoFunction> getFunction() {
        return Optional.ofNullable(function);
    }

    /** Own task identifier ({@code tag}, at most 255 characters). */
    public Optional<String> getTag() {
        return Optional.ofNullable(tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataForSeoOptions)) {
            return false;
        }
        DataForSeoOptions that = (DataForSeoOptions) o;
        return providerId.equals(that.providerId) && Objects.equals(locationCode, that.locationCode)
                && Objects.equals(locationName, that.locationName) && Objects.equals(languageName, that.languageName)
                && Objects.equals(seDomain, that.seDomain) && Objects.equals(os, that.os)
                && function == that.function && Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, locationCode, locationName, languageName, seDomain, os, function, tag);
    }

    @Override
    public String toString() {
        return "DataForSeoOptions{providerId=" + providerId + ", locationCode=" + locationCode + ", locationName="
                + locationName + ", seDomain=" + seDomain + ", function=" + function + '}';
    }

    /** Builder for {@link DataForSeoOptions}. */
    public static final class Builder {
        private final ProviderId providerId;
        private Integer locationCode;
        private String locationName;
        private String languageName;
        private String seDomain;
        private String os;
        private DataForSeoFunction function;
        private String tag;

        private Builder(ProviderId providerId) {
            this.providerId = Objects.requireNonNull(providerId, "providerId");
        }

        public Builder locationCode(int locationCode) {
            if (locationCode <= 0) {
                throw new IllegalArgumentException("locationCode must be positive but was " + locationCode);
            }
            this.locationCode = locationCode;
            return this;
        }

        public Builder locationName(String locationName) {
            this.locationName = trimToNull(locationName);
            return this;
        }

        public Builder languageName(String languageName) {
            this.languageName = trimToNull(languageName);
            return this;
        }

        public Builder seDomain(String seDomain) {
            this.seDomain = trimToNull(seDomain);
            return this;
        }

        public Builder os(String os) {
            this.os = trimToNull(os);
            return this;
        }

        public Builder function(DataForSeoFunction function) {
            this.function = function;
            return this;
        }

        public Builder tag(String tag) {
            String t = trimToNull(tag);
            if (t != null && t.length() > 255) {
                throw new IllegalArgumentException("tag must have at most 255 characters");
            }
            this.tag = t;
            return this;
        }

        public DataForSeoOptions build() {
            return new DataForSeoOptions(this);
        }

        private static String trimToNull(String s) {
            if (s == null) {
                return null;
            }
            String t = s.trim();
            return t.isEmpty() ? null : t;
        }
    }
}
