package com.aresstack.serp4j.testkit.http;

import com.aresstack.serp4j.http.HttpMethod;
import com.aresstack.serp4j.http.HttpRequest;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Selects the requests a {@link FakeHttpTransport} stub answers. Immutable; carries a description for failure
 * messages. URL comparisons use {@link HttpRequest#getUrl()}, i.e. including credential query parameters.
 */
public final class RequestMatcher {

    private static final Pattern QUERY_PARAMETER = Pattern.compile("([?&])([^=&#?]+)=([^&#]*)");

    private final Predicate<HttpRequest> predicate;
    private final String description;

    private RequestMatcher(Predicate<HttpRequest> predicate, String description) {
        this.predicate = predicate;
        this.description = description;
    }

    /** Matches with an arbitrary predicate. */
    public static RequestMatcher of(Predicate<HttpRequest> predicate, String description) {
        if (predicate == null) {
            throw new NullPointerException("predicate must not be null");
        }
        if (description == null) {
            throw new NullPointerException("description must not be null");
        }
        return new RequestMatcher(predicate, description);
    }

    /** Matches every request. */
    public static RequestMatcher any() {
        return of(request -> true, "any request");
    }

    public static RequestMatcher method(final HttpMethod method) {
        if (method == null) {
            throw new NullPointerException("method must not be null");
        }
        return of(request -> request.getMethod() == method, "method " + method);
    }

    /** URL without query string equals {@code url} (query parameters are ignored). */
    public static RequestMatcher path(final String url) {
        requireText(url, "url");
        return of(request -> withoutQuery(request.getUrl()).equals(withoutQuery(url)), "url path " + redact(url));
    }

    public static RequestMatcher urlEquals(final String url) {
        requireText(url, "url");
        return of(request -> request.getUrl().equals(url), "url = " + redact(url));
    }

    public static RequestMatcher urlStartsWith(final String prefix) {
        requireText(prefix, "prefix");
        return of(request -> request.getUrl().startsWith(prefix), "url starts with " + redact(prefix));
    }

    public static RequestMatcher urlContains(final String part) {
        requireText(part, "part");
        return of(request -> request.getUrl().contains(part), "url contains " + redact(part));
    }

    /** GET request whose URL starts with {@code urlPrefix}. */
    public static RequestMatcher get(String urlPrefix) {
        return method(HttpMethod.GET).and(urlStartsWith(urlPrefix));
    }

    /** POST request whose URL starts with {@code urlPrefix}. */
    public static RequestMatcher post(String urlPrefix) {
        return method(HttpMethod.POST).and(urlStartsWith(urlPrefix));
    }

    /** Header {@code name} (case-insensitive) has the value {@code value}. */
    public static RequestMatcher header(final String name, final String value) {
        requireText(name, "name");
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        return of(request -> request.getHeaders().getAll(name).contains(value), "header " + name + " = ****");
    }

    /** UTF-8 body contains {@code part}. */
    public static RequestMatcher bodyContains(final String part) {
        requireText(part, "part");
        return of(request -> request.hasBody() && new String(request.getBody(),
                java.nio.charset.StandardCharsets.UTF_8).contains(part), "body contains " + part);
    }

    public RequestMatcher and(final RequestMatcher other) {
        if (other == null) {
            throw new NullPointerException("other must not be null");
        }
        return of(request -> predicate.test(request) && other.predicate.test(request),
                description + " and " + other.description);
    }

    public boolean matches(HttpRequest request) {
        return predicate.test(request);
    }

    @Override
    public String toString() {
        return description;
    }

    /**
     * Masks the values of credential query parameters ({@link HttpRequest#isSensitiveQueryParameter}) so that matcher
     * descriptions in failure messages never expose a key that is part of a matched URL.
     */
    static String redact(String url) {
        Matcher matcher = QUERY_PARAMETER.matcher(url);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = HttpRequest.isSensitiveQueryParameter(matcher.group(2))
                    ? matcher.group(1) + matcher.group(2) + "=****" : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String withoutQuery(String url) {
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
    }

    private static void requireText(String value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }
}
