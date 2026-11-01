package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.DeviceType;
import com.aresstack.serp4j.core.model.GeoLocation;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.json.JsonTrees;

import java.util.Locale;
import java.util.Optional;

/** Builds path and JSON body of a live request (API reference §4, §6). Contains no I/O. */
final class DataForSeoRequestMapper {

    /** Live depth maximum (§4.6). */
    static final int LIVE_MAX_DEPTH = 200;
    /** Keyword maximum (§4.2). */
    static final int MAX_KEYWORD_LENGTH = 700;

    private final ProviderId providerId;
    private final DataForSeoFunction defaultFunction;
    private final int defaultLocationCode;
    private final String defaultLanguageCode;

    DataForSeoRequestMapper(ProviderId providerId, DataForSeoFunction defaultFunction, int defaultLocationCode,
                            String defaultLanguageCode) {
        this.providerId = providerId;
        this.defaultFunction = defaultFunction;
        this.defaultLocationCode = defaultLocationCode;
        this.defaultLanguageCode = defaultLanguageCode;
    }

    DataForSeoFunction function(SearchRequest request) {
        Optional<DataForSeoOptions> options = options(request);
        return options.isPresent() && options.get().getFunction().isPresent()
                ? options.get().getFunction().get() : defaultFunction;
    }

    /** {@code /v3/serp/{engine}/organic/live/{function}}. */
    String path(SearchEngineId engine, DataForSeoFunction function) {
        return "/v3/serp/" + engine.getCode() + "/organic/live/" + function.getPathSegment();
    }

    /** The JSON array with exactly one task object. */
    String body(SearchRequest request, SearchEngineId engine) {
        String keyword = request.getQuery();
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            throw invalid("query exceeds " + MAX_KEYWORD_LENGTH + " characters");
        }
        int depth = request.getPaging().getOffset() + request.getPaging().getLimit();
        if (depth > LIVE_MAX_DEPTH) {
            throw invalid("offset + limit must not exceed " + LIVE_MAX_DEPTH + " for live requests but was " + depth);
        }
        Optional<DataForSeoOptions> options = options(request);
        Optional<SearchLocale> locale = request.getLocale();

        StructuredValue.ObjectBuilder task = StructuredValue.objectBuilder();
        // A literal '%' or '+' would otherwise be URL-decoded by DataForSEO (§4.2).
        task.put("keyword", keyword.replace("%", "%25").replace("+", "%2B"));
        putLocation(task, options, locale, engine);
        putLanguage(task, options, locale);
        if (request.getDevice().isPresent()) {
            task.put("device", request.getDevice().get() == DeviceType.DESKTOP ? "desktop" : "mobile");
        }
        if (options.isPresent()) {
            DataForSeoOptions o = options.get();
            if (o.getOs().isPresent()) {
                task.put("os", o.getOs().get());
            }
            if (o.getSeDomain().isPresent()) {
                if (!SearchEngineId.GOOGLE.equals(engine)) {
                    throw invalid("se_domain is only supported for Google");
                }
                task.put("se_domain", o.getSeDomain().get());
            }
            if (o.getTag().isPresent()) {
                task.put("tag", o.getTag().get());
            }
        }
        task.put("depth", depth);
        return JsonTrees.write(StructuredValue.arrayBuilder().add(task.build()).build());
    }

    private void putLocation(StructuredValue.ObjectBuilder task, Optional<DataForSeoOptions> options,
                             Optional<SearchLocale> locale, SearchEngineId engine) {
        if (options.isPresent() && options.get().getLocationCode().isPresent()) {
            task.put("location_code", options.get().getLocationCode().getAsInt());
            return;
        }
        if (options.isPresent() && options.get().getLocationName().isPresent()) {
            task.put("location_name", options.get().getLocationName().get());
            return;
        }
        if (locale.isPresent() && locale.get().getLocation().isPresent()) {
            GeoLocation geo = locale.get().getLocation().get();
            if (geo.hasCoordinates()) {
                // Google: latitude,longitude,radius (mm); Bing: latitude,longitude (§4.3).
                String coordinate = String.format(Locale.ROOT, "%.7f,%.7f", geo.getLatitude().getAsDouble(),
                        geo.getLongitude().getAsDouble());
                if (SearchEngineId.GOOGLE.equals(engine)) {
                    coordinate += ",199";
                }
                task.put("location_coordinate", coordinate);
                return;
            }
            if (geo.getName().isPresent()) {
                task.put("location_name", geo.getName().get());
                return;
            }
        }
        if (locale.isPresent() && locale.get().getCountryCode().isPresent()) {
            // DataForSEO country locations carry the English country name, e.g. "Germany", "United States".
            String name = new Locale("", locale.get().getCountryCode().get()).getDisplayCountry(Locale.ENGLISH);
            if (!name.isEmpty()) {
                task.put("location_name", name);
                return;
            }
        }
        task.put("location_code", defaultLocationCode);
    }

    private void putLanguage(StructuredValue.ObjectBuilder task, Optional<DataForSeoOptions> options,
                             Optional<SearchLocale> locale) {
        if (locale.isPresent() && locale.get().getLanguageCode().isPresent()) {
            task.put("language_code", locale.get().getLanguageCode().get());
        } else if (options.isPresent() && options.get().getLanguageName().isPresent()) {
            task.put("language_name", options.get().getLanguageName().get());
        } else {
            task.put("language_code", defaultLanguageCode);
        }
    }

    private Optional<DataForSeoOptions> options(SearchRequest request) {
        return request.getProviderOptions(providerId, DataForSeoOptions.class);
    }

    private SearchProviderException invalid(String message) {
        return new SearchProviderException(providerId, SearchErrorCategory.INVALID_REQUEST, message);
    }
}
