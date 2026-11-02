package com.aresstack.serp4j.playwright;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.id.ProviderId;

/** Failures a {@link PlaywrightSearchProvider} reports; detail keys are stable for hosts. */
public final class PlaywrightSearchFailures {

    /** Detail key naming the obstacle kind, for example {@code consent}, {@code captcha} or {@code js_required}. */
    public static final String DETAIL_OBSTACLE = "obstacle";
    /** Detail key with the URL the page ended on. */
    public static final String DETAIL_FINAL_URL = "final_url";

    private PlaywrightSearchFailures() {
    }

    /**
     * The engine reached a consent dialog, a challenge or another page that must not be worked around (ADR-016).
     *
     * @param provider the provider
     * @param obstacle short obstacle kind
     * @param finalUrl URL the page ended on, may be {@code null}
     * @return a {@code BLOCKED} failure
     */
    public static SearchProviderException obstacle(ProviderId provider, String obstacle, String finalUrl) {
        SearchProviderException.Builder b = SearchProviderException.builder(provider, SearchErrorCategory.BLOCKED,
                "search engine presented an obstacle: " + obstacle).detail(DETAIL_OBSTACLE, obstacle);
        if (finalUrl != null) {
            b.detail(DETAIL_FINAL_URL, finalUrl);
        }
        return b.build();
    }

    /**
     * The engine did not recognise the page structure (markup changed, error page).
     *
     * @param provider the provider
     * @param reason   what was missing
     * @param finalUrl URL the page ended on, may be {@code null}
     * @return a {@code RESPONSE_INVALID} failure
     */
    public static SearchProviderException unrecognized(ProviderId provider, String reason, String finalUrl) {
        SearchProviderException.Builder b = SearchProviderException.builder(provider,
                SearchErrorCategory.RESPONSE_INVALID, "result page not recognised: " + reason);
        if (finalUrl != null) {
            b.detail(DETAIL_FINAL_URL, finalUrl);
        }
        return b.build();
    }
}
