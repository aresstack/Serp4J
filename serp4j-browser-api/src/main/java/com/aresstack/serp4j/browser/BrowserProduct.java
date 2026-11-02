package com.aresstack.serp4j.browser;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Open value type naming a browser product, independent of the backend that drives it (ADR-020). The constants cover
 * the products Serp4J knows; hosts may define further products with {@link #of(String, EngineFamily)}.
 */
public final class BrowserProduct {

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    /** Google Chrome (installed). */
    public static final BrowserProduct CHROME = new BrowserProduct("chrome", EngineFamily.CHROMIUM);
    /** Microsoft Edge (installed). */
    public static final BrowserProduct EDGE = new BrowserProduct("edge", EngineFamily.CHROMIUM);
    /** Chromium, for example Playwright's build or a portable Chromium. */
    public static final BrowserProduct CHROMIUM = new BrowserProduct("chromium", EngineFamily.CHROMIUM);
    /** Firefox; only Playwright's Firefox build is controllable, a stock Firefox is not (ADR-020). */
    public static final BrowserProduct FIREFOX = new BrowserProduct("firefox", EngineFamily.FIREFOX);
    /** Camoufox, a portable Firefox build with fingerprint configuration (E-5). */
    public static final BrowserProduct CAMOUFOX = new BrowserProduct("camoufox", EngineFamily.FIREFOX);
    /** WebKit (Playwright's build). */
    public static final BrowserProduct WEBKIT = new BrowserProduct("webkit", EngineFamily.WEBKIT);

    private final String id;
    private final EngineFamily engineFamily;

    private BrowserProduct(String id, EngineFamily engineFamily) {
        this.id = id;
        this.engineFamily = engineFamily;
    }

    /**
     * @param id           lower-case product id
     * @param engineFamily engine family of the product
     * @return the product; a known id returns its constant
     * @throws IllegalArgumentException if the id is invalid or a known id is used with another engine family
     */
    public static BrowserProduct of(String id, EngineFamily engineFamily) {
        Args.notNull(id, "id");
        Args.notNull(engineFamily, "engineFamily");
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("product id must match " + ID.pattern() + ": " + id);
        }
        BrowserProduct known = lookup(normalized);
        if (known != null) {
            if (known.engineFamily != engineFamily) {
                throw new IllegalArgumentException("product " + normalized + " has engine family "
                        + known.engineFamily);
            }
            return known;
        }
        return new BrowserProduct(normalized, engineFamily);
    }

    /**
     * @param id product id, case-insensitive
     * @return the known product, empty for ids that are not one of the constants
     */
    public static java.util.Optional<BrowserProduct> known(String id) {
        return java.util.Optional.ofNullable(lookup(Args.notNull(id, "id").trim().toLowerCase(Locale.ROOT)));
    }

    private static BrowserProduct lookup(String id) {
        for (BrowserProduct p : new BrowserProduct[] {CHROME, EDGE, CHROMIUM, FIREFOX, CAMOUFOX, WEBKIT}) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    /**
     * @return the lower-case product id
     */
    public String getId() {
        return id;
    }

    /**
     * @return the engine family
     */
    public EngineFamily getEngineFamily() {
        return engineFamily;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof BrowserProduct && id.equals(((BrowserProduct) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
