package com.aresstack.serp4j.browser.testkit;

import com.aresstack.serp4j.browser.ElementSelector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A scripted page of the {@link FakeWeb}: status, title, HTML, the elements that exist (the fake does not parse
 * HTML), interactions, script results and background exchanges. Immutable.
 */
public final class FakePage {

    /** An element the page declares. */
    static final class Element {
        final int count;
        final boolean visible;

        Element(int count, boolean visible) {
            this.count = count;
            this.visible = visible;
        }
    }

    private final int status;
    private final String title;
    private final String html;
    private final String text;
    private final String redirectTo;
    private final Duration delay;
    private final Map<String, String> setCookies;
    private final Map<ElementSelector, Element> elements;
    private final Map<ElementSelector, String> clickTargets;
    private final Map<ElementSelector, String> enterTargets;
    private final Map<String, FakeScript> scripts;
    private final List<FakeExchange> exchanges;

    private FakePage(Builder builder) {
        this.status = builder.status;
        this.title = builder.title;
        this.html = builder.html;
        this.text = builder.text;
        this.redirectTo = builder.redirectTo;
        this.delay = builder.delay;
        this.setCookies = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.setCookies));
        this.elements = Collections.unmodifiableMap(new LinkedHashMap<ElementSelector, Element>(builder.elements));
        this.clickTargets = Collections.unmodifiableMap(new LinkedHashMap<ElementSelector, String>(builder.clickTargets));
        this.enterTargets = Collections.unmodifiableMap(new LinkedHashMap<ElementSelector, String>(builder.enterTargets));
        this.scripts = Collections.unmodifiableMap(new LinkedHashMap<String, FakeScript>(builder.scripts));
        this.exchanges = Collections.unmodifiableList(new ArrayList<FakeExchange>(builder.exchanges));
    }

    /**
     * @return a builder for a page with status 200
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @param target absolute or relative redirect target
     * @return a page that redirects with status 302
     */
    public static FakePage redirect(String target) {
        return builder().status(302).redirectTo(target).build();
    }

    int getStatus() {
        return status;
    }

    String getTitle() {
        return title;
    }

    String getHtml() {
        return html;
    }

    String getText() {
        if (text != null) {
            return text;
        }
        return html.replaceAll("(?s)<head>.*?</head>", " ").replaceAll("(?s)<script.*?</script>", " ")
                .replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    String getRedirectTo() {
        return redirectTo;
    }

    Duration getDelay() {
        return delay;
    }

    Map<String, String> getSetCookies() {
        return setCookies;
    }

    Map<ElementSelector, Element> getElements() {
        return elements;
    }

    Map<ElementSelector, String> getClickTargets() {
        return clickTargets;
    }

    Map<ElementSelector, String> getEnterTargets() {
        return enterTargets;
    }

    Map<String, FakeScript> getScripts() {
        return scripts;
    }

    List<FakeExchange> getExchanges() {
        return exchanges;
    }

    /** Builder for {@link FakePage}. */
    public static final class Builder {
        private int status = 200;
        private String title = "";
        private String html = "<html><head></head><body></body></html>";
        private String text;
        private String redirectTo;
        private Duration delay = Duration.ZERO;
        private final Map<String, String> setCookies = new LinkedHashMap<String, String>();
        private final Map<ElementSelector, Element> elements = new LinkedHashMap<ElementSelector, Element>();
        private final Map<ElementSelector, String> clickTargets = new LinkedHashMap<ElementSelector, String>();
        private final Map<ElementSelector, String> enterTargets = new LinkedHashMap<ElementSelector, String>();
        private final Map<String, FakeScript> scripts = new LinkedHashMap<String, FakeScript>();
        private final List<FakeExchange> exchanges = new ArrayList<FakeExchange>();

        private Builder() {
        }

        /**
         * @param status HTTP status, default 200
         * @return this builder
         */
        public Builder status(int status) {
            this.status = status;
            return this;
        }

        /**
         * @param title document title
         * @return this builder
         */
        public Builder title(String title) {
            this.title = title == null ? "" : title;
            return this;
        }

        /**
         * @param html serialized DOM returned by snapshots
         * @return this builder
         */
        public Builder html(String html) {
            this.html = html == null ? "" : html;
            return this;
        }

        /**
         * @param text visible text; default is the HTML without tags
         * @return this builder
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * @param target absolute or relative redirect target
         * @return this builder
         */
        public Builder redirectTo(String target) {
            this.redirectTo = target;
            return this;
        }

        /**
         * @param delay time until the page has loaded; a navigation with a shorter timeout times out
         * @return this builder
         */
        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /**
         * @param name  cookie name set by the response
         * @param value cookie value
         * @return this builder
         */
        public Builder setCookie(String name, String value) {
            setCookies.put(name, value);
            return this;
        }

        /**
         * Declares a visible element that exists once.
         *
         * @param selector element
         * @return this builder
         */
        public Builder element(ElementSelector selector) {
            return element(selector, 1, true);
        }

        /**
         * @param selector element
         * @param count    number of matches
         * @param visible  whether the matches are visible
         * @return this builder
         */
        public Builder element(ElementSelector selector, int count, boolean visible) {
            elements.put(selector, new Element(count, visible));
            return this;
        }

        /**
         * Declares a visible element whose click navigates.
         *
         * @param selector element
         * @param target   absolute or relative target URL
         * @return this builder
         */
        public Builder onClick(ElementSelector selector, String target) {
            element(selector);
            clickTargets.put(selector, target);
            return this;
        }

        /**
         * Declares an input whose {@code Enter} key navigates; {@code {value}} in the target is replaced by the
         * URL-encoded typed value.
         *
         * @param selector       input element
         * @param targetTemplate absolute or relative target URL template
         * @return this builder
         */
        public Builder onEnter(ElementSelector selector, String targetTemplate) {
            element(selector);
            enterTargets.put(selector, targetTemplate);
            return this;
        }

        /**
         * @param scriptId {@link com.aresstack.serp4j.browser.PageScript#getId()}
         * @param script   result of the script on this page
         * @return this builder
         */
        public Builder script(String scriptId, FakeScript script) {
            scripts.put(scriptId, script);
            return this;
        }

        /**
         * @param exchange background exchange triggered by the page
         * @return this builder
         */
        public Builder exchange(FakeExchange exchange) {
            exchanges.add(exchange);
            return this;
        }

        /**
         * @return the page
         */
        public FakePage build() {
            return new FakePage(this);
        }
    }
}
