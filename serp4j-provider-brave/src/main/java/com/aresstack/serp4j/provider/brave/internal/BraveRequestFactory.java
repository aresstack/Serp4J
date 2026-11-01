package com.aresstack.serp4j.provider.brave.internal;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.GeoLocation;
import com.aresstack.serp4j.core.model.Paging;
import com.aresstack.serp4j.core.model.SafeSearch;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.TimeRange;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.UrlQueryBuilder;
import com.aresstack.serp4j.provider.brave.BraveRequestOptions;
import com.aresstack.serp4j.provider.brave.BraveSearchOptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Translates a {@link SearchRequest} into {@code GET <base>/web/search} (Brave reference §3.2–3.3).
 *
 * <p>The subscription token goes only into the sensitive header {@code X-Subscription-Token}; the URL never
 * carries it.
 */
public final class BraveRequestFactory {

    public static final String WEB_SEARCH_PATH = "/web/search";
    public static final String TOKEN_HEADER = "X-Subscription-Token";
    /** Brave limits: {@code count} 1–20, {@code offset} page index 0–9, query ≤ 600 characters and ≤ 75 words. */
    public static final int MAX_COUNT = 20;
    public static final int MAX_PAGE_INDEX = 9;
    public static final int MAX_QUERY_CHARS = 600;
    public static final int MAX_QUERY_WORDS = 75;

    private final ProviderId providerId;
    private final BraveSearchOptions options;
    private final Clock clock;

    public BraveRequestFactory(ProviderId providerId, BraveSearchOptions options, Clock clock) {
        this.providerId = providerId;
        this.options = options;
        this.clock = clock;
    }

    /** Result of the translation: the HTTP request plus what the response mapping needs to know. */
    public static final class Prepared {
        private final HttpRequest httpRequest;
        private final int count;
        private final int pageIndex;
        private final SearchLocale effectiveLocale;
        private final List<SearchWarning> warnings;

        Prepared(HttpRequest httpRequest, int count, int pageIndex, SearchLocale effectiveLocale,
                 List<SearchWarning> warnings) {
            this.httpRequest = httpRequest;
            this.count = count;
            this.pageIndex = pageIndex;
            this.effectiveLocale = effectiveLocale;
            this.warnings = Collections.unmodifiableList(new ArrayList<SearchWarning>(warnings));
        }

        public HttpRequest getHttpRequest() {
            return httpRequest;
        }

        public int getCount() {
            return count;
        }

        /** 0-based page index sent as {@code offset}. */
        public int getPageIndex() {
            return pageIndex;
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
     * @param token   the subscription token, used only for the header
     * @param timeout timeout of this HTTP call
     * @param initialWarnings warnings found before, e.g. ignored optional features
     * @throws SearchProviderException {@code INVALID_REQUEST} for queries or paging Brave cannot express
     */
    public Prepared prepare(SearchRequest request, String token, Duration timeout,
                            List<SearchWarning> initialWarnings) {
        List<SearchWarning> warnings = new ArrayList<SearchWarning>(initialWarnings);
        String query = validateQuery(request.getQuery());

        Paging paging = request.getPaging();
        int count = paging.getLimit();
        if (count > MAX_COUNT) {
            // Only reachable for an optional MAX_RESULTS; the capability check already reported it as a warning.
            count = MAX_COUNT;
        }
        int offset = paging.getOffset();
        if (offset % count != 0) {
            throw invalid("Brave pages by page index: offset " + offset + " must be a multiple of the limit "
                    + count);
        }
        int pageIndex = offset / count;
        if (pageIndex > MAX_PAGE_INDEX) {
            throw invalid("Brave serves at most " + (MAX_PAGE_INDEX + 1) + " pages; offset " + offset
                    + " with limit " + count + " is page " + (pageIndex + 1));
        }

        SearchLocale locale = effectiveLocale(request.getLocale());
        UrlQueryBuilder url = UrlQueryBuilder.of(options.getBaseUrl() + WEB_SEARCH_PATH)
                .param("q", query)
                .paramIfPresent("country", locale.getCountryCode().map(c -> c.toUpperCase(Locale.ROOT)).orElse(null))
                .paramIfPresent("search_lang", locale.getLanguageCode().orElse(null))
                .paramIfPresent("ui_lang", locale.getUiLanguage().orElse(null))
                .param("count", count)
                .param("offset", pageIndex);
        if (request.getSafeSearch().isPresent()) {
            url.param("safesearch", safeSearch(request.getSafeSearch().get()));
        }
        if (request.getTimeRange().isPresent()) {
            url.param("freshness", freshness(request.getTimeRange().get()));
        }
        Optional<BraveRequestOptions> braveOptions = request.getProviderOptions(providerId, BraveRequestOptions.class);
        if (braveOptions.isPresent()) {
            applyOptions(url, braveOptions.get());
        }

        HttpRequest.Builder http = HttpRequest.get(url.build())
                .setSensitiveHeader(TOKEN_HEADER, token)
                .header("Accept", "application/json")
                .timeout(timeout);
        if (options.getApiVersion().isPresent()) {
            http.header("api-version", options.getApiVersion().get());
        }
        if (locale.getLocation().isPresent()) {
            applyLocation(http, locale.getLocation().get());
        }
        return new Prepared(http.build(), count, pageIndex, locale, warnings);
    }

    private String validateQuery(String raw) {
        String query = raw.trim();
        if (query.isEmpty()) {
            throw invalid("query must not be blank");
        }
        if (query.length() > MAX_QUERY_CHARS) {
            throw invalid("query has " + query.length() + " characters; Brave accepts at most " + MAX_QUERY_CHARS);
        }
        int words = query.split("\\s+").length;
        if (words > MAX_QUERY_WORDS) {
            throw invalid("query has " + words + " words; Brave accepts at most " + MAX_QUERY_WORDS);
        }
        return query;
    }

    private SearchLocale effectiveLocale(Optional<SearchLocale> requested) {
        SearchLocale.Builder builder = requested.isPresent() ? requested.get().toBuilder() : SearchLocale.builder();
        SearchLocale base = requested.orElse(null);
        if ((base == null || !base.getCountryCode().isPresent()) && options.getDefaultCountry().isPresent()) {
            builder.countryCode(options.getDefaultCountry().get());
        }
        if ((base == null || !base.getLanguageCode().isPresent()) && options.getDefaultSearchLanguage().isPresent()) {
            builder.languageCode(options.getDefaultSearchLanguage().get());
        }
        if ((base == null || !base.getUiLanguage().isPresent()) && options.getDefaultUiLanguage().isPresent()) {
            builder.uiLanguage(options.getDefaultUiLanguage().get());
        }
        return builder.build();
    }

    private static void applyLocation(HttpRequest.Builder http, GeoLocation location) {
        if (location.getLatitude().isPresent() && location.getLongitude().isPresent()) {
            http.header("X-Loc-Lat", Double.toString(location.getLatitude().getAsDouble()));
            http.header("X-Loc-Long", Double.toString(location.getLongitude().getAsDouble()));
        }
        if (location.getName().isPresent()) {
            String name = location.getName().get();
            // Header values must not carry control characters; HttpHeaders rejects CR/LF anyway.
            if (name.chars().allMatch(c -> c >= 0x20 && c < 0x7f)) {
                http.header("X-Loc-City", name);
            }
        }
    }

    private static void applyOptions(UrlQueryBuilder url, BraveRequestOptions options) {
        if (options.getSpellcheck().isPresent()) {
            url.param("spellcheck", options.getSpellcheck().get());
        }
        if (options.getTextDecorations().isPresent()) {
            url.param("text_decorations", options.getTextDecorations().get());
        }
        if (options.getExtraSnippets().isPresent()) {
            url.param("extra_snippets", options.getExtraSnippets().get());
        }
        if (options.getOperators().isPresent()) {
            url.param("operators", options.getOperators().get());
        }
        if (options.getSummary().isPresent()) {
            url.param("summary", options.getSummary().get());
        }
        if (options.getIncludeFetchMetadata().isPresent()) {
            url.param("include_fetch_metadata", options.getIncludeFetchMetadata().get());
        }
        if (options.getUnits().isPresent()) {
            url.param("units", options.getUnits().get().name().toLowerCase(Locale.ROOT));
        }
        if (!options.getResultFilter().isEmpty()) {
            url.param("result_filter", String.join(",", options.getResultFilter()));
        }
        for (String goggle : options.getGoggles()) {
            url.param("goggles", goggle);
        }
    }

    static String safeSearch(SafeSearch safeSearch) {
        switch (safeSearch) {
            case OFF:
                return "off";
            case STRICT:
                return "strict";
            case MODERATE:
            default:
                return "moderate";
        }
    }

    String freshness(TimeRange range) {
        if (range.getPeriod().isPresent()) {
            switch (range.getPeriod().get()) {
                case DAY:
                    return "pd";
                case WEEK:
                    return "pw";
                case MONTH:
                    return "pm";
                case YEAR:
                default:
                    return "py";
            }
        }
        if (!range.getFrom().isPresent()) {
            throw invalid("Brave freshness needs a start date; open-ended 'until' ranges are not supported");
        }
        Instant to = range.getTo().orElse(clock.instant());
        return date(range.getFrom().get()) + "to" + date(to);
    }

    private static String date(Instant instant) {
        return LocalDate.from(instant.atZone(ZoneOffset.UTC)).toString();
    }

    private SearchProviderException invalid(String message) {
        return new SearchProviderException(providerId, SearchErrorCategory.INVALID_REQUEST, message);
    }
}
