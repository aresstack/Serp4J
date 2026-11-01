package com.aresstack.serp4j.provider.brave.internal;

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

/**
 * Maps Brave error responses (Brave reference §3.5: {@code type}, {@code error.id/status/detail/code/meta},
 * {@code time}) and transport failures onto {@link SearchErrorCategory} (architecture.md §5.2). Messages carry the
 * status, Brave's error code and detail, never request headers or the full body.
 */
public final class BraveErrors {

    private static final int MAX_DETAIL = 300;

    private BraveErrors() {
    }

    public static SearchProviderException fromResponse(ProviderId providerId, HttpResponse response) {
        int status = response.getStatus();
        Optional<StructuredValue> error = errorObject(response);
        String code = error.flatMap(e -> text(e, "code")).orElse(null);
        String detail = error.flatMap(e -> text(e, "detail")).orElse(null);
        String errorId = error.flatMap(e -> text(e, "id")).orElse(null);

        SearchErrorCategory category = categorize(status, code);
        StringBuilder message = new StringBuilder("Brave Search API answered HTTP ").append(status);
        if (code != null) {
            message.append(" (").append(code).append(')');
        }
        if (detail != null) {
            message.append(": ").append(truncate(detail));
        }
        SearchProviderException.Builder builder = SearchProviderException
                .builder(providerId, category, message.toString())
                .providerStatusCode(status);
        if (code != null) {
            builder.detail("errorCode", code);
        }
        if (errorId != null) {
            builder.detail("errorId", errorId);
        }
        Optional<Duration> retryAfter = retryAfter(response);
        if (retryAfter.isPresent()) {
            builder.retryAfter(retryAfter.get());
        }
        return builder.build();
    }

    static SearchErrorCategory categorize(int status, String code) {
        String normalized = code == null ? "" : code.toUpperCase(Locale.ROOT);
        if (normalized.equals("QUOTA_LIMITED")) {
            return SearchErrorCategory.QUOTA_EXHAUSTED;
        }
        if (normalized.equals("RATE_LIMITED")) {
            return SearchErrorCategory.RATE_LIMITED;
        }
        if (normalized.contains("TOKEN") || normalized.contains("SUBSCRIPTION") || normalized.contains("AUTH")) {
            return SearchErrorCategory.AUTHENTICATION;
        }
        switch (status) {
            case 400:
            case 422:
                return SearchErrorCategory.INVALID_REQUEST;
            case 401:
            case 403:
                return SearchErrorCategory.AUTHENTICATION;
            case 402:
                return SearchErrorCategory.QUOTA_EXHAUSTED;
            case 404:
                return SearchErrorCategory.CONFIGURATION;
            case 408:
                return SearchErrorCategory.TIMEOUT;
            case 429:
                return SearchErrorCategory.RATE_LIMITED;
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
        return SearchProviderException.builder(providerId, category,
                "HTTP transport failed (" + failure.getKind() + "): " + failure.getMessage()).cause(failure).build();
    }

    private static Optional<StructuredValue> errorObject(HttpResponse response) {
        try {
            StructuredValue root = JsonTrees.read(response.getBodyAsText());
            if (!root.isObject()) {
                return Optional.empty();
            }
            Optional<StructuredValue> error = root.get("error");
            return error.isPresent() && error.get().isObject() ? error : Optional.<StructuredValue>empty();
        } catch (RuntimeException notJson) {
            return Optional.empty();
        }
    }

    private static Optional<String> text(StructuredValue object, String name) {
        Optional<StructuredValue> value = object.get(name);
        if (!value.isPresent() || value.get().isNull()) {
            return Optional.empty();
        }
        String text = value.get().asText().orElse(value.get().toString());
        return text.trim().isEmpty() ? Optional.<String>empty() : Optional.of(text.trim());
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
