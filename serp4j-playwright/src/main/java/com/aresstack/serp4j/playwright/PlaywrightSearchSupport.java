package com.aresstack.serp4j.playwright;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.ext.RawPayload;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.metadata.ExecutionInfo;
import com.aresstack.serp4j.core.model.metadata.PageInfo;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.capability.CapabilityCheck;
import com.aresstack.serp4j.core.port.capability.CapabilityViolation;
import com.aresstack.serp4j.core.port.capability.SearchCapabilities;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Shared pieces for {@link PlaywrightSearchProvider} implementations: pre-checks, timeouts, translation of Playwright
 * failures and metadata of a browser search. Static, stateless; a provider uses what it needs.
 */
public final class PlaywrightSearchSupport {

    /** Largest HTML kept as raw payload; longer pages are cut and a {@code partial_results} warning added. */
    public static final int MAX_RAW_HTML_CHARS = 8 * 1024 * 1024;

    private PlaywrightSearchSupport() {
    }

    /**
     * Checks everything a search needs before the browser is touched: a connected browser, a request within the
     * capabilities, no cancellation, no passed deadline.
     *
     * @param provider     the provider
     * @param capabilities its capabilities
     * @param request      the request
     * @param context      the context
     * @param browser      the browser the host passed
     * @param now          the current instant
     * @return the capability check, whose optional violations become warnings
     * @throws SearchProviderException {@code UNAVAILABLE}, {@code UNSUPPORTED}, {@code CANCELLED} or {@code TIMEOUT}
     */
    public static CapabilityCheck precheck(ProviderId provider, SearchCapabilities capabilities, SearchRequest request,
                                           SearchContext context, Browser browser, Instant now) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        if (browser == null) {
            throw new SearchProviderException(provider, SearchErrorCategory.UNAVAILABLE,
                    "host passed no browser");
        }
        if (!browser.isConnected()) {
            throw new SearchProviderException(provider, SearchErrorCategory.UNAVAILABLE,
                    "host browser is disconnected; the host restarts it");
        }
        CapabilityCheck check = capabilities.check(request);
        if (!check.getRequiredViolations().isEmpty()) {
            throw new SearchProviderException(provider, SearchErrorCategory.UNSUPPORTED,
                    provider + " cannot serve this request: " + check.getRequiredViolations());
        }
        if (context.getCancellation().isCancelled()) {
            throw new SearchProviderException(provider, SearchErrorCategory.CANCELLED, "search was cancelled");
        }
        if (context.isExpired(now)) {
            throw new SearchProviderException(provider, SearchErrorCategory.TIMEOUT,
                    "deadline passed before the search started");
        }
        return check;
    }

    /**
     * Timeout for page operations: the remaining time of the context, capped at {@code defaultTimeout}; at least
     * one millisecond so an expired deadline fails fast in Playwright instead of hanging.
     *
     * @param context        the context
     * @param defaultTimeout bound without a deadline
     * @param now            the current instant
     * @return milliseconds for {@code Page.setDefaultTimeout} and navigation options
     */
    public static double timeoutMillis(SearchContext context, Duration defaultTimeout, Instant now) {
        Duration remaining = context.getRemainingTime(now).orElse(defaultTimeout);
        if (remaining.isNegative() || remaining.isZero()) {
            return 1;
        }
        return (double) (remaining.compareTo(defaultTimeout) < 0 ? remaining : defaultTimeout).toMillis();
    }

    /**
     * Translates a Playwright failure: a {@link TimeoutError} or passed deadline is {@code TIMEOUT}, a cancelled
     * context is {@code CANCELLED}, a closed browser or page is {@code UNAVAILABLE} (the host restarts the browser),
     * anything else {@code INTERNAL}. The public message carries only the exception type, because Playwright
     * messages can quote page content.
     *
     * @param provider the provider
     * @param failure  the Playwright exception
     * @param context  the context
     * @param now      the current instant
     * @return the translated failure with {@code failure} as cause
     */
    public static SearchProviderException translate(ProviderId provider, PlaywrightException failure,
                                                    SearchContext context, Instant now) {
        String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase(Locale.ROOT);
        SearchErrorCategory category;
        if (context.getCancellation().isCancelled()) {
            category = SearchErrorCategory.CANCELLED;
        } else if (failure instanceof TimeoutError || context.isExpired(now)) {
            category = SearchErrorCategory.TIMEOUT;
        } else if (message.contains("has been closed") || message.contains("target closed")
                || message.contains("connection closed") || message.contains("disconnected")
                || message.contains("browser closed")) {
            category = SearchErrorCategory.UNAVAILABLE;
        } else {
            category = SearchErrorCategory.INTERNAL;
        }
        return SearchProviderException.builder(provider, category,
                "playwright: " + failure.getClass().getSimpleName()).cause(failure).build();
    }

    /**
     * Metadata of a single-provider browser search (ADR-008): provider, engine, vertical, query, execution info,
     * page info, request locale and device, warnings for ignored optional features, and the page HTML as raw payload
     * when the request's policy is {@link RawPayloadPolicy#ALWAYS}.
     *
     * @param provider  the provider
     * @param target    engine and vertical the results come from
     * @param request   the request
     * @param check     the capability check from {@link #precheck}
     * @param startedAt when the search started
     * @param now       the current instant
     * @param finalUrl  URL the page ended on, may be {@code null}
     * @param html      page HTML, may be {@code null}
     * @param page      paging information, {@code null} for offset and limit of the request
     * @param warnings  warnings the provider raised
     * @return the metadata
     */
    public static SearchMetadata metadata(ProviderId provider, SearchTarget target, SearchRequest request,
                                          CapabilityCheck check, Instant startedAt, Instant now, String finalUrl,
                                          String html, PageInfo page, Collection<SearchWarning> warnings) {
        List<SearchWarning> all = new ArrayList<>();
        for (CapabilityViolation violation : check.getOptionalViolations()) {
            all.add(SearchWarning.of(SearchWarning.FEATURE_IGNORED, violation.getMessage(), provider));
        }
        all.addAll(warnings);
        ExecutionInfo.Builder execution = ExecutionInfo.builder().startedAt(startedAt)
                .duration(Duration.between(startedAt, now));
        if (finalUrl != null) {
            execution.sourceUrl(finalUrl);
        }
        SearchMetadata.Builder metadata = SearchMetadata.forProvider(provider, target.getEngine(),
                        target.getVertical(), QueryInfo.of(request.getQuery()))
                .execution(execution.build())
                .page(page != null ? page
                        : PageInfo.builder(request.getPaging().getOffset(), request.getPaging().getLimit()).build());
        request.getLocale().ifPresent(metadata::locale);
        request.getDevice().ifPresent(metadata::device);
        if (request.getRawPayloadPolicy() == RawPayloadPolicy.ALWAYS && html != null) {
            String kept = html;
            if (kept.length() > MAX_RAW_HTML_CHARS) {
                kept = kept.substring(0, MAX_RAW_HTML_CHARS);
                all.add(SearchWarning.of(SearchWarning.PARTIAL_RESULTS,
                        "raw HTML cut at " + MAX_RAW_HTML_CHARS + " characters", provider));
            }
            metadata.rawPayload(RawPayload.ofText("text/html", kept));
        }
        return metadata.warnings(all).build();
    }

    /**
     * The locale string Playwright expects ({@code de-DE}, {@code en}) for the request's locale.
     *
     * @param request the request
     * @return the locale, or {@code null} if the request has none
     */
    public static String playwrightLocale(SearchRequest request) {
        return request.getLocale().flatMap(l -> l.getLanguageCode().map(language ->
                l.getCountryCode().map(country -> language + "-" + country).orElse(language))).orElse(null);
    }
}
