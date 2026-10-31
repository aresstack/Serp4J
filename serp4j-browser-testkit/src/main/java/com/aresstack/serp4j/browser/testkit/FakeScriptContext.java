package com.aresstack.serp4j.browser.testkit;

import com.aresstack.serp4j.browser.BrowserValue;
import com.aresstack.serp4j.browser.ElementSelector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** What a {@link FakeScript} can see. Immutable. */
public final class FakeScriptContext {

    private final String url;
    private final String title;
    private final BrowserValue argument;
    private final Locale locale;
    private final Map<ElementSelector, String> inputValues;

    FakeScriptContext(String url, String title, BrowserValue argument, Locale locale,
                      Map<ElementSelector, String> inputValues) {
        this.url = url;
        this.title = title;
        this.argument = argument;
        this.locale = locale;
        this.inputValues = Collections.unmodifiableMap(new LinkedHashMap<ElementSelector, String>(inputValues));
    }

    /**
     * @return current page URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return current page title
     */
    public String getTitle() {
        return title;
    }

    /**
     * @return the script argument
     */
    public BrowserValue getArgument() {
        return argument;
    }

    /**
     * @return the session locale, if set
     */
    public Optional<Locale> getLocale() {
        return Optional.ofNullable(locale);
    }

    /**
     * @param selector input element
     * @return the value typed into the element on the current page
     */
    public Optional<String> getInputValue(ElementSelector selector) {
        return Optional.ofNullable(inputValues.get(selector));
    }
}
