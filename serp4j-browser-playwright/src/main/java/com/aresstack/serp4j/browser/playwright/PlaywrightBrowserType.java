package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.EngineFamily;

/** Browser engine that Playwright launches or connects to. */
public enum PlaywrightBrowserType {
    /** Chromium, or Chrome / Edge with a channel. Default. */
    CHROMIUM(EngineFamily.CHROMIUM),
    /** Playwright's Firefox build. Camoufox gets its own module later (E-5). */
    FIREFOX(EngineFamily.FIREFOX),
    /** WebKit. */
    WEBKIT(EngineFamily.WEBKIT);

    private final EngineFamily engineFamily;

    PlaywrightBrowserType(EngineFamily engineFamily) {
        this.engineFamily = engineFamily;
    }

    /**
     * @return the engine family
     */
    public EngineFamily getEngineFamily() {
        return engineFamily;
    }
}
