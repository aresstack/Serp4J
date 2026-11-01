package com.aresstack.serp4j.browser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A condition for {@link BrowserSession#await(WaitCondition, Duration)} (WP3 §5.1, §7.3). Implementations interpret
 * the condition by its {@link #getKind()}. Immutable.
 *
 * <p>{@link Kind#SELECTOR_COUNT_AT_LEAST}, {@link Kind#DOM_QUIET} with a scope and the children of
 * {@link Kind#ALL_OF} must use CSS selectors, because they are evaluated as one predicate in the page.</p>
 */
public final class WaitCondition {

    /** Kind of condition. */
    public enum Kind {
        /** An element matching the selector exists. */
        SELECTOR_PRESENT,
        /** An element matching the selector exists and is visible. */
        SELECTOR_VISIBLE,
        /** No element matches the selector. */
        SELECTOR_ABSENT,
        /** At least {@link #getCount()} elements match the selector, for example the number of answer turns. */
        SELECTOR_COUNT_AT_LEAST,
        /** The page URL matches the regular expression {@link #getUrlPattern()} (partial match). */
        URL_MATCHES,
        /** No DOM mutation (in the scope element, or the whole document) for {@link #getQuietPeriod()}. */
        DOM_QUIET,
        /** All {@link #getChildren()} hold at the same time. */
        ALL_OF
    }

    private final Kind kind;
    private final ElementSelector selector;
    private final int count;
    private final Pattern urlPattern;
    private final Duration quietPeriod;
    private final List<WaitCondition> children;

    private WaitCondition(Kind kind, ElementSelector selector, int count, Pattern urlPattern, Duration quietPeriod,
                          List<WaitCondition> children) {
        this.kind = kind;
        this.selector = selector;
        this.count = count;
        this.urlPattern = urlPattern;
        this.quietPeriod = quietPeriod;
        this.children = children;
    }

    private static WaitCondition ofSelector(Kind kind, ElementSelector selector, int count) {
        return new WaitCondition(kind, Args.notNull(selector, "selector"), count, null, null,
                Collections.<WaitCondition>emptyList());
    }

    /**
     * @param selector element
     * @return a condition that holds when an element matches
     */
    public static WaitCondition present(ElementSelector selector) {
        return ofSelector(Kind.SELECTOR_PRESENT, selector, 1);
    }

    /**
     * @param selector element
     * @return a condition that holds when a matching element is visible
     */
    public static WaitCondition visible(ElementSelector selector) {
        return ofSelector(Kind.SELECTOR_VISIBLE, selector, 1);
    }

    /**
     * @param selector element
     * @return a condition that holds when no element matches, for example a loading indicator
     */
    public static WaitCondition absent(ElementSelector selector) {
        return ofSelector(Kind.SELECTOR_ABSENT, selector, 0);
    }

    /**
     * @param cssSelector CSS selector
     * @param count       positive minimum number of matches
     * @return a condition that holds when at least {@code count} elements match
     */
    public static WaitCondition countAtLeast(ElementSelector cssSelector, int count) {
        requireCss(cssSelector);
        return ofSelector(Kind.SELECTOR_COUNT_AT_LEAST, cssSelector, Args.positive(count, "count"));
    }

    /**
     * @param regex regular expression, matched partially against the page URL
     * @return a condition on the URL
     */
    public static WaitCondition urlMatches(String regex) {
        return new WaitCondition(Kind.URL_MATCHES, null, 0, Pattern.compile(Args.notBlank(regex, "regex")), null,
                Collections.<WaitCondition>emptyList());
    }

    /**
     * @param quietPeriod positive period without DOM mutations in the whole document
     * @return the condition
     */
    public static WaitCondition domQuiet(Duration quietPeriod) {
        return new WaitCondition(Kind.DOM_QUIET, null, 0, null, Args.positive(quietPeriod, "quietPeriod"),
                Collections.<WaitCondition>emptyList());
    }

    /**
     * @param cssScope    CSS selector of the observed element; the condition fails while it does not exist
     * @param quietPeriod positive period without DOM mutations inside the scope
     * @return the condition
     */
    public static WaitCondition domQuiet(ElementSelector cssScope, Duration quietPeriod) {
        requireCss(cssScope);
        return new WaitCondition(Kind.DOM_QUIET, cssScope, 0, null, Args.positive(quietPeriod, "quietPeriod"),
                Collections.<WaitCondition>emptyList());
    }

    /**
     * @param conditions at least one condition; selectors must be CSS
     * @return a condition that holds when all conditions hold at the same time
     */
    public static WaitCondition allOf(WaitCondition... conditions) {
        Args.notNull(conditions, "conditions");
        if (conditions.length == 0) {
            throw new IllegalArgumentException("allOf needs at least one condition");
        }
        List<WaitCondition> list = new ArrayList<WaitCondition>();
        for (WaitCondition condition : conditions) {
            Args.notNull(condition, "condition");
            if (condition.selector != null) {
                requireCss(condition.selector);
            }
            list.add(condition);
        }
        return new WaitCondition(Kind.ALL_OF, null, 0, null, null, Collections.unmodifiableList(list));
    }

    private static void requireCss(ElementSelector selector) {
        if (!Args.notNull(selector, "selector").isCss()) {
            throw new IllegalArgumentException("a CSS selector is required here: " + selector);
        }
    }

    /**
     * @return the kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * @return the selector of selector conditions and the scope of a scoped {@link Kind#DOM_QUIET}
     */
    public Optional<ElementSelector> getSelector() {
        return Optional.ofNullable(selector);
    }

    /**
     * @return the minimum count of {@link Kind#SELECTOR_COUNT_AT_LEAST}
     */
    public int getCount() {
        return count;
    }

    /**
     * @return the URL pattern of {@link Kind#URL_MATCHES}
     */
    public Optional<Pattern> getUrlPattern() {
        return Optional.ofNullable(urlPattern);
    }

    /**
     * @return the quiet period of {@link Kind#DOM_QUIET}
     */
    public Optional<Duration> getQuietPeriod() {
        return Optional.ofNullable(quietPeriod);
    }

    /**
     * @return the children of {@link Kind#ALL_OF}; empty otherwise
     */
    public List<WaitCondition> getChildren() {
        return children;
    }

    /**
     * @return whether every selector in this condition (including children) is a CSS selector
     */
    public boolean isCssOnly() {
        if (selector != null && !selector.isCss()) {
            return false;
        }
        for (WaitCondition child : children) {
            if (!child.isCssOnly()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WaitCondition)) {
            return false;
        }
        WaitCondition that = (WaitCondition) o;
        return kind == that.kind && count == that.count
                && (selector == null ? that.selector == null : selector.equals(that.selector))
                && (urlPattern == null ? that.urlPattern == null : urlPattern.pattern().equals(that.urlPattern.pattern()))
                && (quietPeriod == null ? that.quietPeriod == null : quietPeriod.equals(that.quietPeriod))
                && children.equals(that.children);
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + (selector == null ? 0 : selector.hashCode());
        result = 31 * result + count;
        result = 31 * result + (urlPattern == null ? 0 : urlPattern.pattern().hashCode());
        result = 31 * result + (quietPeriod == null ? 0 : quietPeriod.hashCode());
        return 31 * result + children.hashCode();
    }

    @Override
    public String toString() {
        switch (kind) {
            case SELECTOR_COUNT_AT_LEAST:
                return "countAtLeast(" + selector + ", " + count + ")";
            case URL_MATCHES:
                return "urlMatches(" + urlPattern.pattern() + ")";
            case DOM_QUIET:
                return "domQuiet(" + (selector == null ? "" : selector + ", ") + quietPeriod + ")";
            case ALL_OF:
                return "allOf" + children;
            default:
                return kind.name().toLowerCase(java.util.Locale.ROOT) + "(" + selector + ")";
        }
    }
}
