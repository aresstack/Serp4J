package com.aresstack.serp4j.provider.serpapi.internal;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.GeoLocation;
import com.aresstack.serp4j.core.model.Paging;
import com.aresstack.serp4j.core.model.ProviderRequestOptions;
import com.aresstack.serp4j.core.model.RequirementLevel;
import com.aresstack.serp4j.core.model.SafeSearch;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.TimeRange;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.RequestAuthentication;
import com.aresstack.serp4j.http.UrlQueryBuilder;
import com.aresstack.serp4j.provider.serpapi.SerpApiRequestOptions;
import com.aresstack.serp4j.provider.serpapi.SerpApiSearchOptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Translates a {@link SearchRequest} into {@code GET <base>/search.json} for the engines {@code google} and
 * {@code bing} (SerpApi Google Search API and Bing Search API).
 *
 * <p>The key is added last through {@link RequestAuthentication#queryParameter}, which marks {@code api_key} as a
 * sensitive query parameter of the built request.
 */
public final class SerpApiRequestFactory {

    public static final String SEARCH_PATH = "/search.json";
    public static final String API_KEY_PARAMETER = "api_key";
    /** Google accepts {@code num} up to 100, Bing {@code count} up to 50. */
    public static final int MAX_GOOGLE_NUM = 100;
    public static final int MAX_BING_COUNT = 50;
    /** Google cuts queries at 2048 characters; longer ones are rejected before spending a search. */
    public static final int MAX_QUERY_CHARS = 2048;

    private final ProviderId providerId;
    private final SerpApiSearchOptions options;
    private final Clock clock;

    public SerpApiRequestFactory(ProviderId providerId, SerpApiSearchOptions options, Clock clock) {
        this.providerId = providerId;
        this.options = options;
        this.clock = clock;
    }

    /** Result of the translation: the HTTP request plus what the response mapping needs to know. */
    public static final class Prepared {
        private final HttpRequest httpRequest;
        private final SearchEngineId engine;
        private final int count;
        private final SearchLocale effectiveLocale;
        private final List<SearchWarning> warnings;

        Prepared(HttpRequest httpRequest, SearchEngineId engine, int count, SearchLocale effectiveLocale,
                 List<SearchWarning> warnings) {
            this.httpRequest = httpRequest;
            this.engine = engine;
            this.count = count;
            this.effectiveLocale = effectiveLocale;
            this.warnings = Collections.unmodifiableList(new ArrayList<SearchWarning>(warnings));
        }

        public HttpRequest getHttpRequest() {
            return httpRequest;
        }

        public SearchEngineId getEngine() {
            return engine;
        }

        /** Number of results requested ({@code num} / {@code count}). */
        public int getCount() {
            return count;
        }

        /** Locale after applying the instance defaults; may be empty. */
        public SearchLocale getEffectiveLocale() {
            return effectiveLocale;
        }

        public List<SearchWarning> getWarnings() {
            return warnings;
        }
    }

    /**
     * Validates the request without a key, so invalid requests fail before the credential is resolved.
     *
     * @throws SearchProviderException {@code INVALID_REQUEST} for queries, options or ranges SerpApi cannot express
     */
    public void validate(SearchRequest request, SearchEngineId engine) {
        validateQuery(request.getQuery());
        ownOptions(request);
        if (request.getTimeRange().isPresent() && engine.equals(SearchEngineId.BING)) {
            bingFreshness(request.getTimeRange().get());
        }
    }

    /**
     * @param apiKey          the key; copied into the URL by {@link RequestAuthentication#queryParameter}, the
     *                        caller clears its array afterwards
     * @param timeout         timeout of this HTTP call
     * @param initialWarnings warnings found before, e.g. ignored optional features
     */
    public Prepared prepare(SearchRequest request, SearchEngineId engine, final char[] apiKey, Duration timeout,
                            List<SearchWarning> initialWarnings) {
        List<SearchWarning> warnings = new ArrayList<SearchWarning>(initialWarnings);
        String query = validateQuery(request.getQuery());
        SerpApiRequestOptions own = ownOptions(request);
        boolean google = engine.equals(SearchEngineId.GOOGLE);

        Paging paging = request.getPaging();
        int max = google ? MAX_GOOGLE_NUM : MAX_BING_COUNT;
        int count = paging.getLimit();
        if (count > max) {
            String message = "SerpApi " + engine + " returns at most " + max + " results per request; asked for "
                    + count;
            if (request.getRequirementLevel(SearchFeature.MAX_RESULTS) == RequirementLevel.REQUIRED) {
                throw new SearchProviderException(providerId, SearchErrorCategory.UNSUPPORTED, message);
            }
            // For Google the capability check already reported it; Bing's lower limit is reported here.
            if (!google) {
                warnings.add(SearchWarning.of(SearchWarning.FEATURE_IGNORED, message + "; capped", providerId));
            }
            count = max;
        }
        int offset = paging.getOffset();

        SearchLocale locale = effectiveLocale(request.getLocale());
        UrlQueryBuilder url = UrlQueryBuilder.of(options.getBaseUrl() + SEARCH_PATH)
                .param("engine", engine.getCode())
                .param("q", query);

        String location = own != null && own.getLocation().isPresent() ? own.getLocation().get() : null;
        if (location == null && locale.getLocation().isPresent()) {
            GeoLocation geo = locale.getLocation().get();
            if (geo.getName().isPresent()) {
                location = geo.getName().get();
            } else {
                String message = "location " + geo + " has no name; SerpApi's location parameter needs a place name"
                        + " (e.g. \"Berlin,Germany\")";
                if (request.getRequirementLevel(SearchFeature.GEO_LOCATION) == RequirementLevel.REQUIRED) {
                    throw new SearchProviderException(providerId, SearchErrorCategory.UNSUPPORTED, message);
                }
                warnings.add(SearchWarning.of(SearchWarning.FEATURE_IGNORED, message + "; searched without it",
                        providerId));
                locale = locale.toBuilder().location(null).build();
            }
        }
        if (location == null) {
            location = options.getDefaultLocation().orElse(null);
        }
        url.paramIfPresent("location", location);

        String country = locale.getCountryCode().orElse(null);
        String language = locale.getLanguageCode().orElse(null);
        if (google) {
            String domain = own != null && own.getGoogleDomain().isPresent() ? own.getGoogleDomain().get()
                    : options.getGoogleDomain().orElse(null);
            url.paramIfPresent("google_domain", domain)
                    .paramIfPresent("gl", country == null ? null : country.toLowerCase(Locale.ROOT))
                    .paramIfPresent("hl", language)
                    .param("num", count);
            if (offset > 0) {
                url.param("start", offset);
            }
            if (request.getSafeSearch().isPresent() && request.getSafeSearch().get() != SafeSearch.MODERATE) {
                url.param("safe", request.getSafeSearch().get() == SafeSearch.STRICT ? "active" : "off");
            }
            if (request.getTimeRange().isPresent()) {
                url.param("tbs", googleTbs(request.getTimeRange().get()));
            }
        } else {
            url.paramIfPresent("cc", country == null ? null : country.toUpperCase(Locale.ROOT));
            if (language != null && country != null) {
                url.param("mkt", language.toLowerCase(Locale.ROOT) + "-" + country.toUpperCase(Locale.ROOT));
            }
            url.param("count", count);
            if (offset > 0) {
                url.param("first", offset + 1);
            }
            if (request.getSafeSearch().isPresent()) {
                url.param("safeSearch", bingSafeSearch(request.getSafeSearch().get()));
            }
            if (request.getTimeRange().isPresent()) {
                url.param("filters", bingFreshness(request.getTimeRange().get()));
            }
        }

        boolean noCache = own != null && own.getNoCache().isPresent() ? own.getNoCache().get() : options.isNoCache();
        if (noCache) {
            url.param("no_cache", true);
        }
        if (own != null) {
            if (own.getDevice().isPresent()) {
                url.param("device", own.getDevice().get().name().toLowerCase(Locale.ROOT));
            }
            for (Map.Entry<String, String> parameter : own.getParameters().entrySet()) {
                url.param(parameter.getKey(), parameter.getValue());
            }
        }

        HttpRequest.Builder http = HttpRequest.get(url.build())
                .header("Accept", "application/json")
                .timeout(timeout);
        RequestAuthentication.queryParameter(API_KEY_PARAMETER, apiKey::clone).apply(http);
        return new Prepared(http.build(), engine, count, locale, warnings);
    }

    private SerpApiRequestOptions ownOptions(SearchRequest request) {
        ProviderRequestOptions own = request.getProviderOptions().get(providerId);
        if (own == null) {
            return null;
        }
        if (!(own instanceof SerpApiRequestOptions)) {
            // Never search silently with defaults when the caller meant to configure this instance.
            throw invalid("provider options for " + providerId + " must be SerpApiRequestOptions, got "
                    + own.getClass().getName());
        }
        return (SerpApiRequestOptions) own;
    }

    private String validateQuery(String raw) {
        String query = raw.trim();
        if (query.isEmpty()) {
            throw invalid("query must not be blank");
        }
        if (query.length() > MAX_QUERY_CHARS) {
            throw invalid("query has " + query.length() + " characters; at most " + MAX_QUERY_CHARS + " are sent");
        }
        return query;
    }

    private SearchLocale effectiveLocale(Optional<SearchLocale> requested) {
        SearchLocale.Builder builder = requested.isPresent() ? requested.get().toBuilder() : SearchLocale.builder();
        SearchLocale base = requested.orElse(null);
        if ((base == null || !base.getCountryCode().isPresent()) && options.getDefaultCountry().isPresent()) {
            builder.countryCode(options.getDefaultCountry().get());
        }
        if ((base == null || !base.getLanguageCode().isPresent()) && options.getDefaultLanguage().isPresent()) {
            builder.languageCode(options.getDefaultLanguage().get());
        }
        return builder.build();
    }

    static String bingSafeSearch(SafeSearch safeSearch) {
        switch (safeSearch) {
            case OFF:
                return "Off";
            case STRICT:
                return "Strict";
            case MODERATE:
            default:
                return "Moderate";
        }
    }

    /** Google {@code tbs}: {@code qdr:d|w|m|y} or {@code cdr:1,cd_min:M/D/YYYY,cd_max:M/D/YYYY}. */
    String googleTbs(TimeRange range) {
        if (range.getPeriod().isPresent()) {
            switch (range.getPeriod().get()) {
                case DAY:
                    return "qdr:d";
                case WEEK:
                    return "qdr:w";
                case MONTH:
                    return "qdr:m";
                case YEAR:
                default:
                    return "qdr:y";
            }
        }
        StringBuilder tbs = new StringBuilder("cdr:1");
        if (range.getFrom().isPresent()) {
            tbs.append(",cd_min:").append(usDate(range.getFrom().get()));
        }
        tbs.append(",cd_max:").append(usDate(range.getTo().orElse(clock.instant())));
        return tbs.toString();
    }

    /**
     * Bing {@code filters}: {@code ex1:"ez1"} (day), {@code "ez2"} (week), {@code "ez3"} (month), otherwise a custom
     * range {@code ex1:"ez5_<from>_<to>"} in days since 1970-01-01.
     */
    String bingFreshness(TimeRange range) {
        if (range.getPeriod().isPresent()) {
            switch (range.getPeriod().get()) {
                case DAY:
                    return "ex1:\"ez1\"";
                case WEEK:
                    return "ex1:\"ez2\"";
                case MONTH:
                    return "ex1:\"ez3\"";
                case YEAR:
                default:
                    Instant now = clock.instant();
                    return customBing(now.minus(Duration.ofDays(365)), now);
            }
        }
        if (!range.getFrom().isPresent()) {
            throw invalid("Bing freshness needs a start date; open-ended 'until' ranges are not supported");
        }
        return customBing(range.getFrom().get(), range.getTo().orElse(clock.instant()));
    }

    private static String customBing(Instant from, Instant to) {
        return "ex1:\"ez5_" + epochDay(from) + "_" + epochDay(to) + "\"";
    }

    private static long epochDay(Instant instant) {
        return LocalDate.from(instant.atZone(ZoneOffset.UTC)).toEpochDay();
    }

    private static String usDate(Instant instant) {
        LocalDate date = LocalDate.from(instant.atZone(ZoneOffset.UTC));
        return date.getMonthValue() + "/" + date.getDayOfMonth() + "/" + date.getYear();
    }

    private SearchProviderException invalid(String message) {
        return new SearchProviderException(providerId, SearchErrorCategory.INVALID_REQUEST, message);
    }
}
