package com.aresstack.serp4j.playwright;

import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.port.ProviderDescriptor;
import com.aresstack.serp4j.core.port.SearchContext;
import com.microsoft.playwright.Browser;

/**
 * A browser-based search provider that runs on a Playwright {@link Browser} the host owns (ADR-020, ADR-017). The
 * host passes its current browser with every call; the provider uses it for this one search and returns the
 * results in the Serp4J model. The provider never starts, restarts, replaces or closes the browser, and it keeps no
 * reference to it between calls. How the provider uses the browser (pages, contexts, waits) is its own business.
 *
 * <pre>
 * SearchResponse response = googleProvider.search(request, askAi.searchBrowser());
 * </pre>
 *
 * <p>Rules (ADR-016): a provider never dismisses consent dialogs, solves challenges or otherwise works around an
 * obstacle; it fails with {@link PlaywrightSearchFailures#obstacle} ({@code BLOCKED}). Calls are synchronous and
 * run on the caller's thread; the deadline and cancellation of the {@link SearchContext} bound the search
 * ({@link PlaywrightSearchSupport#timeoutMillis}).</p>
 */
public interface PlaywrightSearchProvider {

    /**
     * @return id, type, kind and capabilities of this provider
     */
    ProviderDescriptor getDescriptor();

    /**
     * Searches with default context (no deadline, no cancellation).
     *
     * @param request the request
     * @param browser the host's current browser
     * @return the response
     * @throws SearchProviderException if the search fails; {@code UNAVAILABLE} when the browser is disconnected
     */
    default SearchResponse search(SearchRequest request, Browser browser) {
        return search(request, SearchContext.defaults(), browser);
    }

    /**
     * @param request the request
     * @param context deadline, cancellation and correlation id of this search
     * @param browser the host's current browser
     * @return the response
     * @throws SearchProviderException if the search fails; {@code UNAVAILABLE} when the browser is disconnected,
     *                                 {@code TIMEOUT} when the deadline passes, {@code CANCELLED} when cancelled
     */
    SearchResponse search(SearchRequest request, SearchContext context, Browser browser);
}
