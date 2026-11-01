package com.aresstack.serp4j.provider.brightdata;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.json.JsonTrees;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Reads a Bright Data {@code /request} response in two steps (reference §3.7, §8.11): the wrapper
 * ({@code status_code} of the target page, its {@code headers}, {@code body}) and then the SERP JSON that
 * {@code body} carries as a <em>string</em>. The wrapper is never mixed into the SERP model.
 */
final class BrightDataResponses {

    private BrightDataResponses() {
    }

    /** The unwrapped SERP plus the target status the wrapper reported. */
    static final class Unwrapped {
        final StructuredValue serp;
        final OptionalInt targetStatus;

        Unwrapped(StructuredValue serp, OptionalInt targetStatus) {
            this.serp = serp;
            this.targetStatus = targetStatus;
        }
    }

    static SearchProviderException httpFailure(ProviderId provider, HttpResponse response) {
        int status = response.getStatus();
        SearchErrorCategory category;
        if (status == 400 || status == 404 || status == 422) {
            category = SearchErrorCategory.INVALID_REQUEST;
        } else if (status == 401 || status == 403) {
            category = SearchErrorCategory.AUTHENTICATION;
        } else if (status == 402) {
            category = SearchErrorCategory.QUOTA_EXHAUSTED;
        } else if (status == 429) {
            category = SearchErrorCategory.RATE_LIMITED;
        } else if (status == 408 || status == 504) {
            category = SearchErrorCategory.TIMEOUT;
        } else {
            category = SearchErrorCategory.UNAVAILABLE;
        }
        // The body is deliberately not copied into the message: its schema is undocumented (reference §9.2).
        SearchProviderException.Builder failure = SearchProviderException.builder(provider, category,
                "Bright Data answered HTTP " + status).providerStatusCode(status);
        Optional<Duration> retryAfter = retryAfter(response);
        if (retryAfter.isPresent()) {
            failure.retryAfter(retryAfter.get());
        }
        return failure.build();
    }

    static Unwrapped unwrap(ProviderId provider, String text) {
        StructuredValue root = read(provider, text, "response");
        if (!root.isObject()) {
            throw invalid(provider, "response is not a JSON object");
        }
        Optional<StructuredValue> body = root.get("body");
        if (!body.isPresent()) {
            // Tolerated: the parsed SERP without wrapper.
            return new Unwrapped(root, OptionalInt.empty());
        }
        OptionalInt targetStatus = OptionalInt.empty();
        Optional<StructuredValue> statusCode = root.get("status_code");
        if (statusCode.isPresent() && statusCode.get().asLong().isPresent()) {
            targetStatus = OptionalInt.of((int) statusCode.get().asLong().getAsLong());
        }
        if (targetStatus.isPresent() && (targetStatus.getAsInt() < 200 || targetStatus.getAsInt() > 299)) {
            int status = targetStatus.getAsInt();
            SearchErrorCategory category = status == 403 || status == 429
                    ? SearchErrorCategory.BLOCKED : SearchErrorCategory.UNAVAILABLE;
            throw SearchProviderException.builder(provider, category,
                    "the search engine answered HTTP " + status + " through Bright Data")
                    .detail("targetStatus", Integer.toString(status))
                    .build();
        }
        StructuredValue serp;
        if (body.get().isString()) {
            serp = read(provider, body.get().asText().get(), "body");
        } else {
            serp = body.get();
        }
        if (!serp.isObject()) {
            throw invalid(provider, "body is not a parsed SERP object; was data_format=parsed sent?");
        }
        return new Unwrapped(serp, targetStatus);
    }

    private static StructuredValue read(ProviderId provider, String text, String what) {
        try {
            return JsonTrees.read(text);
        } catch (RuntimeException e) {
            throw SearchProviderException.builder(provider, SearchErrorCategory.RESPONSE_INVALID,
                    "Bright Data " + what + " is not valid JSON (" + e.getClass().getSimpleName() + ")").build();
        }
    }

    private static SearchProviderException invalid(ProviderId provider, String message) {
        return SearchProviderException.builder(provider, SearchErrorCategory.RESPONSE_INVALID, message).build();
    }

    private static Optional<Duration> retryAfter(HttpResponse response) {
        Optional<String> value = response.getHeaders().getFirst("Retry-After");
        if (!value.isPresent()) {
            return Optional.empty();
        }
        try {
            long seconds = Long.parseLong(value.get().trim());
            return seconds >= 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.<Duration>empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
