package com.aresstack.serp4j.provider.serpapi;

import com.aresstack.serp4j.core.model.SearchLocale;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable instance-wide settings of a {@link SerpApiSearchProvider}.
 *
 * <p>Locale defaults apply only when a request carries no locale of its own. Without them SerpApi uses the engine
 * defaults (Google: {@code gl=us}, {@code hl=en}).
 */
public final class SerpApiSearchOptions {

    /** Base address of the official API. */
    public static final String DEFAULT_BASE_URL = "https://serpapi.com";
    /** SerpApi waits for the engine; uncached searches regularly take several seconds. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final String baseUrl;
    private final String defaultCountry;
    private final String defaultLanguage;
    private final String defaultLocation;
    private final String googleDomain;
    private final boolean noCache;
    private final Duration timeout;

    private SerpApiSearchOptions(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.defaultCountry = builder.defaultCountry;
        this.defaultLanguage = builder.defaultLanguage;
        this.defaultLocation = builder.defaultLocation;
        this.googleDomain = builder.googleDomain;
        this.noCache = builder.noCache;
        this.timeout = builder.timeout;
    }

    public static SerpApiSearchOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Base URL without trailing slash, e.g. {@value #DEFAULT_BASE_URL}. */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** Two-letter country used when a request names none ({@code gl} for Google, {@code cc} for Bing). */
    public Optional<String> getDefaultCountry() {
        return Optional.ofNullable(defaultCountry);
    }

    /** Language used when a request names none ({@code hl} for Google, part of {@code mkt} for Bing). */
    public Optional<String> getDefaultLanguage() {
        return Optional.ofNullable(defaultLanguage);
    }

    /** SerpApi {@code location} (e.g. {@code "Berlin,Germany"}) used when a request names no location. */
    public Optional<String> getDefaultLocation() {
        return Optional.ofNullable(defaultLocation);
    }

    /** Google domain, e.g. {@code google.de}; empty = {@code google.com}. Ignored for Bing. */
    public Optional<String> getGoogleDomain() {
        return Optional.ofNullable(googleDomain);
    }

    /**
     * Whether every search sends {@code no_cache=true}. Default {@code false}: SerpApi serves identical searches from
     * its one-hour cache and does not count cached answers against the monthly quota.
     */
    public boolean isNoCache() {
        return noCache;
    }

    /** Upper bound for one HTTP call; a shorter search deadline wins. */
    public Duration getTimeout() {
        return timeout;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.baseUrl = baseUrl;
        builder.defaultCountry = defaultCountry;
        builder.defaultLanguage = defaultLanguage;
        builder.defaultLocation = defaultLocation;
        builder.googleDomain = googleDomain;
        builder.noCache = noCache;
        builder.timeout = timeout;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SerpApiSearchOptions)) {
            return false;
        }
        SerpApiSearchOptions that = (SerpApiSearchOptions) o;
        return noCache == that.noCache && baseUrl.equals(that.baseUrl)
                && Objects.equals(defaultCountry, that.defaultCountry)
                && Objects.equals(defaultLanguage, that.defaultLanguage)
                && Objects.equals(defaultLocation, that.defaultLocation)
                && Objects.equals(googleDomain, that.googleDomain) && timeout.equals(that.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseUrl, defaultCountry, defaultLanguage, defaultLocation, googleDomain, noCache, timeout);
    }

    @Override
    public String toString() {
        return "SerpApiSearchOptions{baseUrl=" + baseUrl + ", country=" + defaultCountry + ", language="
                + defaultLanguage + ", location=" + defaultLocation + ", googleDomain=" + googleDomain
                + ", noCache=" + noCache + ", timeout=" + timeout + '}';
    }

    public static final class Builder {
        private String baseUrl = DEFAULT_BASE_URL;
        private String defaultCountry;
        private String defaultLanguage;
        private String defaultLocation;
        private String googleDomain;
        private boolean noCache;
        private Duration timeout = DEFAULT_TIMEOUT;

        private Builder() {
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            return this;
        }

        public Builder defaultCountry(String defaultCountry) {
            this.defaultCountry = blankToNull(defaultCountry);
            return this;
        }

        public Builder defaultLanguage(String defaultLanguage) {
            this.defaultLanguage = blankToNull(defaultLanguage);
            return this;
        }

        public Builder defaultLocation(String defaultLocation) {
            this.defaultLocation = blankToNull(defaultLocation);
            return this;
        }

        public Builder googleDomain(String googleDomain) {
            this.googleDomain = blankToNull(googleDomain);
            return this;
        }

        public Builder noCache(boolean noCache) {
            this.noCache = noCache;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /** @throws IllegalArgumentException for an invalid base URL, country, language, domain or timeout */
        public SerpApiSearchOptions build() {
            String url = baseUrl.trim();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                throw new IllegalArgumentException("baseUrl must be an http(s) URL: " + baseUrl);
            }
            if (url.indexOf('?') >= 0 || url.indexOf('#') >= 0) {
                throw new IllegalArgumentException("baseUrl must not contain a query or fragment: " + baseUrl);
            }
            baseUrl = url;
            // Same rules as SearchLocale, so an accepted default can always be applied to a request's locale.
            if (defaultCountry != null) {
                SearchLocale.builder().countryCode(defaultCountry).build();
            }
            if (defaultLanguage != null) {
                SearchLocale.builder().languageCode(defaultLanguage).build();
            }
            if (googleDomain != null && !googleDomain.matches("([a-z0-9-]+\\.)*google\\.[a-z.]{2,10}")) {
                throw new IllegalArgumentException("googleDomain must be a Google domain like google.de: "
                        + googleDomain);
            }
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive: " + timeout);
            }
            return new SerpApiSearchOptions(this);
        }

        private static String blankToNull(String value) {
            return value == null || value.trim().isEmpty() ? null : value.trim();
        }
    }
}
