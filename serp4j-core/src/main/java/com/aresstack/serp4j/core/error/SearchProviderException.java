package com.aresstack.serp4j.core.error;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ProviderId;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * The one exception a provider port fails with (ADR-007). Classified by {@link SearchErrorCategory}; carries the
 * provider, an optional retry hint, the provider's own status code and free-form details.
 *
 * <p>Message and details must never contain secrets or complete response bodies. Adapters map every transport,
 * protocol and parsing failure onto this type; infrastructure exceptions appear at most as {@link #getCause()}.</p>
 *
 * <p>Like all Serp4J types the exception is not meant to be Java-serialised (ADR-004); the fields are therefore
 * not serialisable.</p>
 */
@SuppressWarnings("serial")
public class SearchProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ProviderId providerId;
    private final SearchErrorCategory category;
    private final boolean retryable;
    private final Duration retryAfter;
    private final Integer providerStatusCode;
    private final Map<String, String> details;

    public SearchProviderException(ProviderId providerId, SearchErrorCategory category, String message) {
        this(builder(providerId, category, message));
    }

    public SearchProviderException(ProviderId providerId, SearchErrorCategory category, String message,
                                   Throwable cause) {
        this(builder(providerId, category, message).cause(cause));
    }

    protected SearchProviderException(Builder builder) {
        super(builder.message, builder.cause);
        this.providerId = builder.providerId;
        this.category = builder.category;
        this.retryable = builder.retryable != null ? builder.retryable : builder.category.isRetryable();
        this.retryAfter = builder.retryAfter;
        this.providerStatusCode = builder.providerStatusCode;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.details));
    }

    public static Builder builder(ProviderId providerId, SearchErrorCategory category, String message) {
        return new Builder(providerId, category, message);
    }

    /**
     * Finds the {@link SearchProviderException} behind a future failure, unwrapping {@link CompletionException}
     * and {@link ExecutionException}.
     */
    public static Optional<SearchProviderException> unwrap(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SearchProviderException) {
                return Optional.of((SearchProviderException) current);
            }
            if (current instanceof CompletionException || current instanceof ExecutionException) {
                current = current.getCause();
            } else {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public ProviderId getProviderId() {
        return providerId;
    }

    public SearchErrorCategory getCategory() {
        return category;
    }

    /** Whether the same provider may be retried; defaults to the category's value. */
    public boolean isRetryable() {
        return retryable;
    }

    /** Provider hint when to retry, e.g. from an HTTP {@code Retry-After} header. */
    public Optional<Duration> getRetryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    /** The provider's own status code, e.g. HTTP status or an API status code. */
    public OptionalInt getProviderStatusCode() {
        return providerStatusCode == null ? OptionalInt.empty() : OptionalInt.of(providerStatusCode);
    }

    /** Additional context without secrets or response bodies; unmodifiable. */
    public Map<String, String> getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return getClass().getName() + "{provider=" + providerId + ", category=" + category
                + ", retryable=" + retryable + ", statusCode=" + providerStatusCode + ", message=" + getMessage() + '}';
    }

    /** Builder for {@link SearchProviderException}. */
    public static final class Builder {
        private final ProviderId providerId;
        private final SearchErrorCategory category;
        private final String message;
        private Throwable cause;
        private Boolean retryable;
        private Duration retryAfter;
        private Integer providerStatusCode;
        private final Map<String, String> details = new LinkedHashMap<String, String>();

        private Builder(ProviderId providerId, SearchErrorCategory category, String message) {
            this.providerId = Preconditions.requireNonNull(providerId, "providerId");
            this.category = Preconditions.requireNonNull(category, "category");
            this.message = Preconditions.requireText(message, "message");
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        /** Overrides the category default. */
        public Builder retryable(boolean retryable) {
            this.retryable = retryable;
            return this;
        }

        public Builder retryAfter(Duration retryAfter) {
            if (retryAfter != null && retryAfter.isNegative()) {
                throw new IllegalArgumentException("retryAfter must not be negative but was " + retryAfter);
            }
            this.retryAfter = retryAfter;
            return this;
        }

        public Builder providerStatusCode(int providerStatusCode) {
            this.providerStatusCode = providerStatusCode;
            return this;
        }

        /** Adds a detail; the value must not be a secret or a response body. */
        public Builder detail(String key, String value) {
            details.put(Preconditions.requireText(key, "key"), Preconditions.requireNonNull(value, "value"));
            return this;
        }

        public SearchProviderException build() {
            return new SearchProviderException(this);
        }
    }
}
