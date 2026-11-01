package com.aresstack.serp4j.provider.brightdata;

import com.aresstack.serp4j.core.model.DeviceType;
import com.aresstack.serp4j.core.model.GeoLocation;
import com.aresstack.serp4j.core.model.SafeSearch;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.TimeRange;
import com.aresstack.serp4j.http.UrlQueryBuilder;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Translates a provider-neutral request into the Google search URL that Bright Data fetches (reference §6). Never
 * contains credentials: the API key belongs to the request to Bright Data, not to the target URL.
 */
final class GoogleSearchUrlBuilder {

    static final String DEFAULT_BASE = "https://www.google.com/search";

    private static final DateTimeFormatter GOOGLE_DATE = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final String base;

    GoogleSearchUrlBuilder(String base) {
        this.base = base;
    }

    String build(SearchRequest request, Optional<BrightDataOptions> options) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("q", request.getQuery());
        int offset = request.getPaging().getOffset();
        if (offset > 0) {
            // num is deprecated by Google since 2025-09-11 (reference §6.2); paging only via start.
            params.put("start", Integer.toString(offset));
        }
        if (request.getLocale().isPresent()) {
            SearchLocale locale = request.getLocale().get();
            Optional<String> hl = locale.getUiLanguage().isPresent() ? locale.getUiLanguage() : locale.getLanguageCode();
            if (hl.isPresent()) {
                params.put("hl", hl.get());
            }
            if (locale.getCountryCode().isPresent()) {
                params.put("gl", locale.getCountryCode().get().toLowerCase(Locale.ROOT));
            }
            Optional<GeoLocation> location = locale.getLocation();
            if (location.isPresent() && location.get().getName().isPresent()) {
                params.put("uule", location.get().getName().get());
            }
        }
        if (request.getSafeSearch().isPresent()) {
            params.put("safe", request.getSafeSearch().get() == SafeSearch.OFF ? "off" : "active");
        }
        if (request.getTimeRange().isPresent()) {
            params.put("tbs", tbs(request.getTimeRange().get()));
        }
        if (request.getDevice().isPresent()) {
            params.put("brd_mobile", mobile(request.getDevice().get()));
        }
        if (options.isPresent()) {
            BrightDataOptions o = options.get();
            putIfPresent(params, "uule", o.getUule());
            putIfPresent(params, "brd_mobile", o.getMobileProfile());
            putIfPresent(params, "brd_browser", o.getBrowser());
            putIfPresent(params, "brd_json", o.getBrdJson());
            params.putAll(o.getExtraUrlParameters());
        }
        UrlQueryBuilder url = UrlQueryBuilder.of(base);
        for (Map.Entry<String, String> param : params.entrySet()) {
            url.param(param.getKey(), param.getValue());
        }
        return url.build();
    }

    private static void putIfPresent(Map<String, String> params, String name, Optional<String> value) {
        if (value.isPresent()) {
            params.put(name, value.get());
        }
    }

    static String mobile(DeviceType device) {
        switch (device) {
            case MOBILE:
                return "1";
            case TABLET:
                return "ipad";
            default:
                return "0";
        }
    }

    static String tbs(TimeRange range) {
        if (range.getPeriod().isPresent()) {
            switch (range.getPeriod().get()) {
                case DAY:
                    return "qdr:d";
                case WEEK:
                    return "qdr:w";
                case MONTH:
                    return "qdr:m";
                default:
                    return "qdr:y";
            }
        }
        StringBuilder tbs = new StringBuilder("cdr:1");
        if (range.getFrom().isPresent()) {
            tbs.append(",cd_min:").append(GOOGLE_DATE.format(range.getFrom().get()));
        }
        if (range.getTo().isPresent()) {
            tbs.append(",cd_max:").append(GOOGLE_DATE.format(range.getTo().get()));
        }
        return tbs.toString();
    }
}
