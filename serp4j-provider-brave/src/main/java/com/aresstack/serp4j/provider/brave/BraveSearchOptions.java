package com.aresstack.serp4j.provider.brave;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable instance-wide settings of a {@link BraveSearchProvider} (architecture.md §9.3: provider options are
 * validated in {@code build()}).
 *
 * <p>Locale defaults apply only when a request carries no locale of its own. Without them Brave uses its own
 * defaults ({@code country=US}, {@code search_lang=en}, {@code ui_lang=en-US}).
 */
public final class BraveSearchOptions {

    /** Base address of the official API (Brave reference §2.1). */
    public static final String DEFAULT_BASE_URL = "https://api.search.brave.com/res/v1";
    /** Default per-request timeout when the search context has no deadline. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final String apiVersion;
    private final String defaultCountry;
    private final String defaultSearchLanguage;
    private final String defaultUiLanguage;
    private final Duration timeout;

    private BraveSearchOptions(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.apiVersion = builder.apiVersion;
        this.defaultCountry = builder.defaultCountry;
        this.defaultSearchLanguage = builder.defaultSearchLanguage;
        this.defaultUiLanguage = builder.defaultUiLanguage;
        this.timeout = builder.timeout;
    }

    public static BraveSearchOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Base URL without trailing slash, e.g. {@value #DEFAULT_BASE_URL}. */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** Value of the {@code api-version} header; empty = latest version. */
    public Optional<String> getApiVersion() {
        return Optional.ofNullable(apiVersion);
    }

    public Optional<String> getDefaultCountry() {
        return Optional.ofNullable(defaultCountry);
    }

    public Optional<String> getDefaultSearchLanguage() {
        return Optional.ofNullable(defaultSearchLanguage);
    }

    public Optional<String> getDefaultUiLanguage() {
        return Optional.ofNullable(defaultUiLanguage);
    }

    /** Upper bound for one HTTP call; a shorter search deadline wins. */
    public Duration getTimeout() {
        return timeout;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.baseUrl = baseUrl;
        builder.apiVersion = apiVersion;
        builder.defaultCountry = defaultCountry;
        builder.defaultSearchLanguage = defaultSearchLanguage;
        builder.defaultUiLanguage = defaultUiLanguage;
        builder.timeout = timeout;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BraveSearchOptions)) {
            return false;
        }
        BraveSearchOptions that = (BraveSearchOptions) o;
        return baseUrl.equals(that.baseUrl)
                && Objects.equals(apiVersion, that.apiVersion) && Objects.equals(defaultCountry, that.defaultCountry)
                && Objects.equals(defaultSearchLanguage, that.defaultSearchLanguage)
                && Objects.equals(defaultUiLanguage, that.defaultUiLanguage) && timeout.equals(that.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseUrl, apiVersion, defaultCountry, defaultSearchLanguage, defaultUiLanguage, timeout);
    }

    @Override
    public String toString() {
        return "BraveSearchOptions{baseUrl=" + baseUrl + ", apiVersion=" + apiVersion + ", country=" + defaultCountry
                + ", searchLang=" + defaultSearchLanguage + ", uiLang=" + defaultUiLanguage + ", timeout=" + timeout + '}';
    }

    public static final class Builder {
        private String baseUrl = DEFAULT_BASE_URL;
        private String apiVersion;
        private String defaultCountry;
        private String defaultSearchLanguage;
        private String defaultUiLanguage;
        private Duration timeout = DEFAULT_TIMEOUT;

        private Builder() {
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            return this;
        }

        /** {@code api-version} header in the form {@code YYYY-MM-DD}; {@code null} = latest. */
        public Builder apiVersion(String apiVersion) {
            this.apiVersion = blankToNull(apiVersion);
            return this;
        }

        /** Two-letter country code used when a request names none, e.g. {@code DE}. */
        public Builder defaultCountry(String defaultCountry) {
            this.defaultCountry = blankToNull(defaultCountry);
            return this;
        }

        /** {@code search_lang} used when a request names no language, e.g. {@code de}. */
        public Builder defaultSearchLanguage(String defaultSearchLanguage) {
            this.defaultSearchLanguage = blankToNull(defaultSearchLanguage);
            return this;
        }

        /** {@code ui_lang} used when a request names no UI language, e.g. {@code de-DE}. */
        public Builder defaultUiLanguage(String defaultUiLanguage) {
            this.defaultUiLanguage = blankToNull(defaultUiLanguage);
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /** @throws IllegalArgumentException for an invalid base URL, country code, API version or timeout */
        public BraveSearchOptions build() {
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
            if (defaultCountry != null && !defaultCountry.matches("[A-Za-z]{2}")) {
                throw new IllegalArgumentException("defaultCountry must be a two-letter code: " + defaultCountry);
            }
            if (defaultSearchLanguage != null && defaultSearchLanguage.length() < 2) {
                throw new IllegalArgumentException("defaultSearchLanguage needs at least two characters");
            }
            if (apiVersion != null && !apiVersion.matches("\\d{4}-\\d{2}-\\d{2}")) {
                throw new IllegalArgumentException("apiVersion must have the form YYYY-MM-DD: " + apiVersion);
            }
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive: " + timeout);
            }
            return new BraveSearchOptions(this);
        }

        private static String blankToNull(String value) {
            return value == null || value.trim().isEmpty() ? null : value.trim();
        }
    }
}
