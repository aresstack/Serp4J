package com.aresstack.serp4j.browser.testkit;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** A request to the {@link FakeWeb}: URL, query parameters and the cookies of the session. Immutable. */
public final class FakeRequest {

    private final String url;
    private final Map<String, String> queryParameters;
    private final Map<String, String> cookies;

    FakeRequest(String url, Map<String, String> cookies) {
        this.url = url;
        this.queryParameters = Collections.unmodifiableMap(parseQuery(url));
        this.cookies = Collections.unmodifiableMap(new LinkedHashMap<String, String>(cookies));
    }

    private static Map<String, String> parseQuery(String url) {
        Map<String, String> parameters = new LinkedHashMap<String, String>();
        String query;
        try {
            query = new URI(url).getRawQuery();
        } catch (Exception e) {
            return parameters;
        }
        if (query == null || query.isEmpty()) {
            return parameters;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            parameters.put(decode(name), decode(value));
        }
        return parameters;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return value;
        }
    }

    /**
     * @return the full URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * @param name parameter name
     * @return the decoded query parameter
     */
    public Optional<String> getQueryParameter(String name) {
        return Optional.ofNullable(queryParameters.get(name));
    }

    /**
     * @return cookies of the session for this request (unmodifiable)
     */
    public Map<String, String> getCookies() {
        return cookies;
    }
}
