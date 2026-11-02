package com.aresstack.serp4j.provider.serpapi.internal;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.json.JsonTrees;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Maps SerpApi error answers ({@code {"error": "..."}} with HTTP 400/401/403/404/410/429/5xx, SerpApi "Status and
 * Error Codes") and transport failures onto {@link SearchErrorCategory}. Messages carry the status and SerpApi's
 * error text with any {@code api_key=...} value masked; never request URLs or the full body.
 */
public final class SerpApiErrors {

    private static final int MAX_DETAIL = 300;
    private static final Pattern KEY_IN_TEXT = Pattern.compile("(?i)(api_key=)[^&\\s\"']+");
    /** SerpApi's answer when the engine had no results; a successful, empty search. */
    private static final String NO_RESULTS_MARKER = "hasn't returned any results";

    private SerpApiErrors() {
    }

    /** Whether a 200 error text only says the engine found nothing. */
    public static boolean isEmptyResult(String error) {
        return error != null && error.toLowerCase(Locale.ROOT).contains(NO_RESULTS_MARKER);
    }

    public static SearchProviderException fromResponse(ProviderId providerId, HttpResponse response) {
        int status = response.getStatus();
        String error = errorText(response).orElse(null);
        SearchErrorCategory category = categorize(status, error);
        StringBuilder message = new StringBuilder("SerpApi answered HTTP ").append(status);
        if (error != null) {
            message.append(": ").append(truncate(redact(error)));
        }
        SearchProviderException.Builder builder = SearchProviderException
                .builder(providerId, category, message.toString())
                .providerStatusCode(status);
        Optional<Duration> retryAfter = retryAfter(response);
        if (retryAfter.isPresent()) {
            builder.retryAfter(retryAfter.get());
        }
        return builder.build();
    }

    static SearchErrorCategory categorize(int status, String error) {
        String text = error == null ? "" : error.toLowerCase(Locale.ROOT);
        if (text.contains("run out of searches") || text.contains("out of searches")
                || text.contains("plan") && text.contains("limit")) {
            return SearchErrorCategory.QUOTA_EXHAUSTED;
        }
        if (text.contains("invalid api key") || text.contains("api key")) {
            return SearchErrorCategory.AUTHENTICATION;
        }
        switch (status) {
            case 400:
                return SearchErrorCategory.INVALID_REQUEST;
            case 401:
            case 403:
                return SearchErrorCategory.AUTHENTICATION;
            case 404:
            case 410:
                return SearchErrorCategory.CONFIGURATION;
            case 408:
                return SearchErrorCategory.TIMEOUT;
            case 429:
                return SearchErrorCategory.RATE_LIMITED;
            case 200:
                // search_metadata.status "Error" without an HTTP error: SerpApi could not fetch or parse the SERP.
                return SearchErrorCategory.UNAVAILABLE;
            default:
                return status >= 500 ? SearchErrorCategory.UNAVAILABLE : SearchErrorCategory.INTERNAL;
        }
    }

    public static SearchProviderException fromTransport(ProviderId providerId, HttpTransportException failure) {
        SearchErrorCategory category;
        switch (failure.getKind()) {
            case TIMEOUT:
                category = SearchErrorCategory.TIMEOUT;
                break;
            case CANCELLED:
                category = SearchErrorCategory.CANCELLED;
                break;
            case PROTOCOL:
                category = SearchErrorCategory.RESPONSE_INVALID;
                break;
            default:
                category = SearchErrorCategory.UNAVAILABLE;
                break;
        }
        // The cause is not attached: a transport may have put the full URL (with the key) into its message.
        return new SearchProviderException(providerId, category,
                "HTTP transport failed (" + failure.getKind() + "): " + truncate(redact(String.valueOf(
                        failure.getMessage()))));
    }

    /** Masks {@code api_key=<value>} in free text. */
    public static String redact(String text) {
        return text == null ? null : KEY_IN_TEXT.matcher(text).replaceAll("$1***");
    }

    static Optional<String> errorText(HttpResponse response) {
        try {
            StructuredValue root = JsonTrees.read(response.getBodyAsText());
            return errorText(root);
        } catch (RuntimeException notJson) {
            return Optional.empty();
        }
    }

    static Optional<String> errorText(StructuredValue root) {
        if (!root.isObject()) {
            return Optional.empty();
        }
        Optional<StructuredValue> error = root.get("error");
        if (!error.isPresent() || error.get().isNull()) {
            return Optional.empty();
        }
        String text = error.get().asText().orElse(error.get().toString()).trim();
        return text.isEmpty() ? Optional.<String>empty() : Optional.of(text);
    }

    private static Optional<Duration> retryAfter(HttpResponse response) {
        Optional<String> header = response.getHeaders().getFirst("Retry-After");
        if (header.isPresent()) {
            try {
                long seconds = Long.parseLong(header.get().trim());
                if (seconds >= 0) {
                    return Optional.of(Duration.ofSeconds(seconds));
                }
            } catch (NumberFormatException httpDate) {
                // HTTP-date form is not interpreted.
            }
        }
        return Optional.empty();
    }

    private static String truncate(String text) {
        return text.length() <= MAX_DETAIL ? text : text.substring(0, MAX_DETAIL) + "…";
    }
}
