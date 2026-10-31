package com.aresstack.serp4j.core.model;

import com.aresstack.serp4j.core.internal.Preconditions;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Country, language and location of a search. Appears on the {@link SearchRequest} (what the caller asked for)
 * and in {@code SearchMetadata} (what the provider actually used).
 *
 * <p>Codes are normalised: country codes upper-case (ISO 3166-1 alpha-2), language codes lower-case
 * (ISO 639-1/639-2 or the language part of a BCP 47 tag), UI language as given (BCP 47, for example
 * {@code de-DE}).</p>
 */
public final class SearchLocale {

    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,8}");
    private static final Pattern UI_LANGUAGE = Pattern.compile("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*");

    private final String countryCode;
    private final String languageCode;
    private final String uiLanguage;
    private final GeoLocation location;

    private SearchLocale(Builder builder) {
        this.countryCode = builder.countryCode;
        this.languageCode = builder.languageCode;
        this.uiLanguage = builder.uiLanguage;
        this.location = builder.location;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Country and/or language; either may be {@code null}. */
    public static SearchLocale of(String countryCode, String languageCode) {
        return builder().countryCode(countryCode).languageCode(languageCode).build();
    }

    public Optional<String> getCountryCode() {
        return Optional.ofNullable(countryCode);
    }

    public Optional<String> getLanguageCode() {
        return Optional.ofNullable(languageCode);
    }

    public Optional<String> getUiLanguage() {
        return Optional.ofNullable(uiLanguage);
    }

    public Optional<GeoLocation> getLocation() {
        return Optional.ofNullable(location);
    }

    public boolean isEmpty() {
        return countryCode == null && languageCode == null && uiLanguage == null && location == null;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.countryCode = countryCode;
        builder.languageCode = languageCode;
        builder.uiLanguage = uiLanguage;
        builder.location = location;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchLocale)) {
            return false;
        }
        SearchLocale that = (SearchLocale) o;
        return Objects.equals(countryCode, that.countryCode)
                && Objects.equals(languageCode, that.languageCode)
                && Objects.equals(uiLanguage, that.uiLanguage)
                && Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(countryCode, languageCode, uiLanguage, location);
    }

    @Override
    public String toString() {
        return "SearchLocale{country=" + countryCode + ", language=" + languageCode
                + ", uiLanguage=" + uiLanguage + ", location=" + location + '}';
    }

    /** Builder; blank values clear the field. */
    public static final class Builder {
        private String countryCode;
        private String languageCode;
        private String uiLanguage;
        private GeoLocation location;

        private Builder() {
        }

        /** ISO 3166-1 alpha-2, normalised to upper case. */
        public Builder countryCode(String countryCode) {
            String value = Preconditions.trimToNull(countryCode);
            if (value != null) {
                value = value.toUpperCase(Locale.ROOT);
                if (!COUNTRY.matcher(value).matches()) {
                    throw new IllegalArgumentException("countryCode must be ISO 3166-1 alpha-2 but was '" + countryCode + "'");
                }
            }
            this.countryCode = value;
            return this;
        }

        /** ISO 639 language code or the language part of a BCP 47 tag, normalised to lower case. */
        public Builder languageCode(String languageCode) {
            String value = Preconditions.trimToNull(languageCode);
            if (value != null) {
                value = value.toLowerCase(Locale.ROOT);
                if (!LANGUAGE.matcher(value).matches()) {
                    throw new IllegalArgumentException("languageCode must be an ISO 639 code but was '" + languageCode + "'");
                }
            }
            this.languageCode = value;
            return this;
        }

        /** BCP 47 language tag of the user interface, for example {@code de-DE}. */
        public Builder uiLanguage(String uiLanguage) {
            String value = Preconditions.trimToNull(uiLanguage);
            if (value != null && !UI_LANGUAGE.matcher(value).matches()) {
                throw new IllegalArgumentException("uiLanguage must be a BCP 47 tag but was '" + uiLanguage + "'");
            }
            this.uiLanguage = value;
            return this;
        }

        public Builder location(GeoLocation location) {
            this.location = location;
            return this;
        }

        public SearchLocale build() {
            return new SearchLocale(this);
        }
    }
}
