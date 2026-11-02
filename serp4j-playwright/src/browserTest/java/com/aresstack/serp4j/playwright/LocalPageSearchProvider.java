package com.aresstack.serp4j.playwright;

import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.ResultProvenance;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.data.SerpPosition;
import com.aresstack.serp4j.core.model.id.ElementId;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.port.ProviderDescriptor;
import com.aresstack.serp4j.core.port.ProviderKind;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.capability.CapabilityCheck;
import com.aresstack.serp4j.core.port.capability.ResponseFeature;
import com.aresstack.serp4j.core.port.capability.SearchCapabilities;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The smallest useful {@link PlaywrightSearchProvider}: searches a local test site the way a host's engine module
 * would search a real engine. Shows the intended shape: precheck, one page on the passed browser, timeouts from the
 * context, obstacle detection (ADR-016), extraction, metadata via {@link PlaywrightSearchSupport}, page closed in
 * {@code finally}, browser untouched.
 */
final class LocalPageSearchProvider implements PlaywrightSearchProvider {

    static final SearchEngineId ENGINE = SearchEngineId.of("local-test");
    static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final ProviderDescriptor descriptor;
    private final String baseUrl;

    LocalPageSearchProvider(ProviderId id, String baseUrl) {
        this.descriptor = ProviderDescriptor.builder(id, "local-test-playwright", ProviderKind.BROWSER,
                        SearchCapabilities.builder()
                                .target(ENGINE, SearchVertical.WEB)
                                .features(SearchFeature.QUERY_TEXT, SearchFeature.MAX_RESULTS,
                                        SearchFeature.RAW_RESPONSE)
                                .responseFeatures(ResponseFeature.ORGANIC_RESULTS, ResponseFeature.RAW_PAYLOAD)
                                .build())
                .displayName("Local test site (Playwright)")
                .build();
        this.baseUrl = baseUrl;
    }

    @Override
    public ProviderDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public SearchResponse search(SearchRequest request, SearchContext context, Browser browser) {
        ProviderId id = descriptor.getId();
        Instant startedAt = Instant.now();
        CapabilityCheck check = PlaywrightSearchSupport.precheck(id, descriptor.getCapabilities(), request, context,
                browser, startedAt);
        Page page = null;
        try {
            page = browser.newPage();
            page.setDefaultTimeout(PlaywrightSearchSupport.timeoutMillis(context, TIMEOUT, Instant.now()));
            page.navigate(baseUrl + "/search?q=" + URLEncoder.encode(request.getQuery(), StandardCharsets.UTF_8));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            String finalUrl = page.url();
            if (page.locator("#consent").count() > 0) {
                throw PlaywrightSearchFailures.obstacle(id, "consent", finalUrl);
            }
            Locator results = page.locator("li.result");
            if (results.count() == 0 && page.locator("#results").count() == 0) {
                throw PlaywrightSearchFailures.unrecognized(id, "no #results list", finalUrl);
            }
            List<SearchResultItem> items = new ArrayList<>();
            int limit = Math.min(request.getPaging().getLimit(), results.count());
            for (int i = 0; i < limit; i++) {
                Locator link = results.nth(i).locator("a");
                items.add(SearchResultItem.builder(ElementId.of("local-" + (i + 1)), ResultType.ORGANIC,
                                link.getAttribute("href"))
                        .title(link.textContent().trim())
                        .snippet(results.nth(i).locator("p").textContent().trim())
                        .position(SerpPosition.ofRankInGroup(i + 1))
                        .provenance(ResultProvenance.builder(id, ENGINE).build())
                        .build());
            }
            SearchData data = SearchData.builder().results(items).build();
            return SearchResponse.of(data, PlaywrightSearchSupport.metadata(id,
                    SearchTarget.web(ENGINE), request, check, startedAt, Instant.now(), finalUrl, page.content(),
                    null, Collections.emptyList()));
        } catch (SearchProviderException e) {
            throw e;
        } catch (PlaywrightException e) {
            throw PlaywrightSearchSupport.translate(id, e, context, Instant.now());
        } finally {
            if (page != null) {
                try {
                    page.close();
                } catch (PlaywrightException ignored) {
                    // the browser may be gone; it belongs to the host
                }
            }
        }
    }
}
