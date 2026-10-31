package com.aresstack.serp4j.browser.testkit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The scripted web of a {@link FakeBrowserRuntime}: pages by URL without query string and fragment, plus scripts
 * that apply to every page. URLs that are not registered answer 404. Immutable.
 */
public final class FakeWeb {

    private final Map<String, FakeResponder> routes;
    private final Map<String, FakeScript> scripts;

    private FakeWeb(Builder builder) {
        this.routes = Collections.unmodifiableMap(new LinkedHashMap<String, FakeResponder>(builder.routes));
        this.scripts = Collections.unmodifiableMap(new LinkedHashMap<String, FakeScript>(builder.scripts));
    }

    /**
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return an empty web
     */
    public static FakeWeb empty() {
        return builder().build();
    }

    FakePage respond(FakeRequest request) {
        FakeResponder responder = routes.get(key(request.getUrl()));
        if (responder == null) {
            return FakePage.builder().status(404).title("404 Not Found")
                    .html("<html><head><title>404 Not Found</title></head><body>not found</body></html>").build();
        }
        return responder.respond(request);
    }

    FakeScript globalScript(String id) {
        return scripts.get(id);
    }

    static String key(String url) {
        String key = url;
        int fragment = key.indexOf('#');
        if (fragment >= 0) {
            key = key.substring(0, fragment);
        }
        int query = key.indexOf('?');
        if (query >= 0) {
            key = key.substring(0, query);
        }
        return key;
    }

    /** Builder for {@link FakeWeb}. */
    public static final class Builder {
        private final Map<String, FakeResponder> routes = new LinkedHashMap<String, FakeResponder>();
        private final Map<String, FakeScript> scripts = new LinkedHashMap<String, FakeScript>();

        private Builder() {
        }

        /**
         * @param url  absolute URL; query string and fragment are ignored
         * @param page the page
         * @return this builder
         */
        public Builder page(String url, final FakePage page) {
            return route(url, new FakeResponder() {
                @Override
                public FakePage respond(FakeRequest request) {
                    return page;
                }
            });
        }

        /**
         * @param url       absolute URL; query string and fragment are ignored
         * @param responder produces the page per request
         * @return this builder
         */
        public Builder route(String url, FakeResponder responder) {
            if (url == null || responder == null) {
                throw new NullPointerException("url and responder must not be null");
            }
            routes.put(key(url), responder);
            return this;
        }

        /**
         * @param scriptId script id
         * @param script   result of the script on every page that does not register its own
         * @return this builder
         */
        public Builder script(String scriptId, FakeScript script) {
            scripts.put(scriptId, script);
            return this;
        }

        /**
         * @return the web
         */
        public FakeWeb build() {
            return new FakeWeb(this);
        }
    }
}
