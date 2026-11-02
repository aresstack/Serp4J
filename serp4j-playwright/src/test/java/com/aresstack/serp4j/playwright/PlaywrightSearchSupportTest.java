package com.aresstack.serp4j.playwright;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.port.CancellationSource;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.capability.CapabilityCheck;
import com.aresstack.serp4j.core.port.capability.SearchCapabilities;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaywrightSearchSupportTest {

    private static final ProviderId ID = ProviderId.of("test-playwright");
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final SearchCapabilities CAPABILITIES = SearchCapabilities.builder()
            .target(SearchEngineId.of("local"), SearchVertical.WEB)
            .features(SearchFeature.QUERY_TEXT, SearchFeature.MAX_RESULTS)
            .build();
    private static final SearchRequest REQUEST = SearchRequest.builder("serp4j").build();

    /** A {@link Browser} that only answers {@code isConnected()}; everything else is not called by the support. */
    static Browser browser(boolean connected) {
        return (Browser) Proxy.newProxyInstance(Browser.class.getClassLoader(), new Class<?>[] {Browser.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isConnected":
                            return connected;
                        case "toString":
                            return "FakeBrowser(connected=" + connected + ")";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    @Test
    void precheckRejectsMissingAndDisconnectedBrowser() {
        assertThatThrownBy(() -> PlaywrightSearchSupport.precheck(ID, CAPABILITIES, REQUEST,
                SearchContext.defaults(), null, NOW))
                .isInstanceOf(SearchProviderException.class)
                .extracting(e -> ((SearchProviderException) e).getCategory())
                .isEqualTo(SearchErrorCategory.UNAVAILABLE);
        assertThatThrownBy(() -> PlaywrightSearchSupport.precheck(ID, CAPABILITIES, REQUEST,
                SearchContext.defaults(), browser(false), NOW))
                .isInstanceOf(SearchProviderException.class)
                .hasMessageContaining("disconnected")
                .extracting(e -> ((SearchProviderException) e).getCategory())
                .isEqualTo(SearchErrorCategory.UNAVAILABLE);
    }

    @Test
    void precheckAppliesCapabilitiesCancellationAndDeadline() {
        SearchRequest unsupported = SearchRequest.builder("x").require(SearchFeature.COUNTRY).build();
        assertThatThrownBy(() -> PlaywrightSearchSupport.precheck(ID, CAPABILITIES, unsupported,
                SearchContext.defaults(), browser(true), NOW))
                .extracting(e -> ((SearchProviderException) e).getCategory())
                .isEqualTo(SearchErrorCategory.UNSUPPORTED);

        CancellationSource cancelled = new CancellationSource();
        cancelled.cancel();
        assertThatThrownBy(() -> PlaywrightSearchSupport.precheck(ID, CAPABILITIES, REQUEST,
                SearchContext.builder().cancellation(cancelled.getSignal()).build(), browser(true), NOW))
                .extracting(e -> ((SearchProviderException) e).getCategory())
                .isEqualTo(SearchErrorCategory.CANCELLED);

        assertThatThrownBy(() -> PlaywrightSearchSupport.precheck(ID, CAPABILITIES, REQUEST,
                SearchContext.builder().deadline(NOW.minusSeconds(1)).build(), browser(true), NOW))
                .extracting(e -> ((SearchProviderException) e).getCategory())
                .isEqualTo(SearchErrorCategory.TIMEOUT);

        CapabilityCheck ok = PlaywrightSearchSupport.precheck(ID, CAPABILITIES, REQUEST, SearchContext.defaults(),
                browser(true), NOW);
        assertThat(ok.getRequiredViolations()).isEmpty();
    }

    @Test
    void timeoutIsRemainingTimeCappedAtDefault() {
        Duration defaults = Duration.ofSeconds(30);
        assertThat(PlaywrightSearchSupport.timeoutMillis(SearchContext.defaults(), defaults, NOW)).isEqualTo(30_000);
        assertThat(PlaywrightSearchSupport.timeoutMillis(SearchContext.builder().deadline(NOW.plusSeconds(5))
                .build(), defaults, NOW)).isEqualTo(5_000);
        assertThat(PlaywrightSearchSupport.timeoutMillis(SearchContext.builder().deadline(NOW.plusSeconds(90))
                .build(), defaults, NOW)).isEqualTo(30_000);
        assertThat(PlaywrightSearchSupport.timeoutMillis(SearchContext.builder().deadline(NOW.minusSeconds(1))
                .build(), defaults, NOW)).isEqualTo(1);
    }

    @Test
    void translatesPlaywrightFailuresWithoutQuotingTheirMessage() {
        SearchContext context = SearchContext.defaults();
        SearchProviderException timeout = PlaywrightSearchSupport.translate(ID,
                new TimeoutError("Timeout 30000ms exceeded. <html>secret page text</html>"), context, NOW);
        assertThat(timeout.getCategory()).isEqualTo(SearchErrorCategory.TIMEOUT);
        assertThat(timeout.getMessage()).doesNotContain("secret page text").contains("TimeoutError");
        assertThat(timeout.getCause()).isInstanceOf(TimeoutError.class);

        SearchProviderException gone = PlaywrightSearchSupport.translate(ID,
                new PlaywrightException("Target page, context or browser has been closed"), context, NOW);
        assertThat(gone.getCategory()).isEqualTo(SearchErrorCategory.UNAVAILABLE);

        SearchProviderException expired = PlaywrightSearchSupport.translate(ID,
                new PlaywrightException("net::ERR_ABORTED"),
                SearchContext.builder().deadline(NOW.minusSeconds(1)).build(), NOW);
        assertThat(expired.getCategory()).isEqualTo(SearchErrorCategory.TIMEOUT);

        CancellationSource cancelled = new CancellationSource();
        cancelled.cancel();
        SearchProviderException byCancel = PlaywrightSearchSupport.translate(ID,
                new PlaywrightException("Target page, context or browser has been closed"),
                SearchContext.builder().cancellation(cancelled.getSignal()).build(), NOW);
        assertThat(byCancel.getCategory()).isEqualTo(SearchErrorCategory.CANCELLED);

        assertThat(PlaywrightSearchSupport.translate(ID, new PlaywrightException("Protocol error"), context, NOW)
                .getCategory()).isEqualTo(SearchErrorCategory.INTERNAL);
    }

    @Test
    void metadataFollowsAdr008AndRawPayloadPolicy() {
        SearchRequest request = SearchRequest.builder("serp4j").limit(5).offset(10)
                .locale(SearchLocale.of("DE", "de"))
                .rawPayloadPolicy(RawPayloadPolicy.ALWAYS)
                .build();
        CapabilityCheck check = CAPABILITIES.check(request);
        SearchTarget target = SearchTarget.web(SearchEngineId.of("local"));
        SearchMetadata metadata = PlaywrightSearchSupport.metadata(ID, target, request, check, NOW,
                NOW.plusMillis(250), "http://localhost/search?q=serp4j", "<html>page</html>", null,
                Collections.singletonList(SearchWarning.of("custom", "from provider", ID)));

        assertThat(metadata.getProvider()).contains(ID);
        assertThat(metadata.getContributingProviders()).containsExactly(ID);
        assertThat(metadata.getEngine()).contains(SearchEngineId.of("local"));
        assertThat(metadata.getVertical()).contains(SearchVertical.WEB);
        assertThat(metadata.getQuery().getOriginalQuery()).isEqualTo("serp4j");
        assertThat(metadata.getLocale()).contains(SearchLocale.of("DE", "de"));
        assertThat(metadata.getPage()).isPresent();
        assertThat(metadata.getPage().get().getOffset()).isEqualTo(10);
        assertThat(metadata.getPage().get().getRequestedCount()).isEqualTo(5);
        assertThat(metadata.getExecution()).isPresent();
        assertThat(metadata.getExecution().get().getDuration()).contains(Duration.ofMillis(250));
        assertThat(metadata.getExecution().get().getSourceUrl()).contains("http://localhost/search?q=serp4j");
        assertThat(metadata.getRawPayload()).isPresent();
        assertThat(metadata.getRawPayload().get().getBodyAsText()).isEqualTo("<html>page</html>");
        assertThat(metadata.getWarnings()).extracting(SearchWarning::getCode).contains("custom");
        // locale is used but not a declared feature: ignored optional feature becomes a warning, no failure
        assertThat(metadata.getWarnings()).extracting(SearchWarning::getCode)
                .contains(SearchWarning.FEATURE_IGNORED);

        SearchMetadata noRaw = PlaywrightSearchSupport.metadata(ID, target,
                request.toBuilder().rawPayloadPolicy(RawPayloadPolicy.NONE).build(), check, NOW, NOW, null,
                "<html/>", null, Collections.emptyList());
        assertThat(noRaw.getRawPayload()).isEmpty();
        assertThat(noRaw.getExecution().get().getSourceUrl()).isEmpty();
    }

    @Test
    void playwrightLocaleFromRequest() {
        assertThat(PlaywrightSearchSupport.playwrightLocale(REQUEST)).isNull();
        assertThat(PlaywrightSearchSupport.playwrightLocale(REQUEST.toBuilder()
                .locale(SearchLocale.of("DE", "de")).build())).isEqualTo("de-DE");
        assertThat(PlaywrightSearchSupport.playwrightLocale(REQUEST.toBuilder()
                .locale(SearchLocale.builder().languageCode("en").build()).build())).isEqualTo("en");
    }

    @Test
    void failuresCarryStableDetails() {
        SearchProviderException blocked = PlaywrightSearchFailures.obstacle(ID, "captcha",
                "https://www.example.invalid/sorry/index");
        assertThat(blocked.getCategory()).isEqualTo(SearchErrorCategory.BLOCKED);
        assertThat(blocked.getDetails()).containsEntry(PlaywrightSearchFailures.DETAIL_OBSTACLE, "captcha")
                .containsEntry(PlaywrightSearchFailures.DETAIL_FINAL_URL, "https://www.example.invalid/sorry/index");
        assertThat(PlaywrightSearchFailures.unrecognized(ID, "no result list", null).getCategory())
                .isEqualTo(SearchErrorCategory.RESPONSE_INVALID);
    }
}
