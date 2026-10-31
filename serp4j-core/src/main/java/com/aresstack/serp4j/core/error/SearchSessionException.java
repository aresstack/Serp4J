package com.aresstack.serp4j.core.error;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchSessionId;

import java.time.Duration;

/** Failure of a conversational session (ADR-007, ADR-009): a {@link SearchProviderException} plus the session id. */
@SuppressWarnings("serial")
public final class SearchSessionException extends SearchProviderException {

    private static final long serialVersionUID = 1L;

    private final SearchSessionId sessionId;

    public SearchSessionException(SearchSessionId sessionId, ProviderId providerId, SearchErrorCategory category,
                                  String message) {
        this(builder(sessionId, providerId, category, message));
    }

    public SearchSessionException(SearchSessionId sessionId, ProviderId providerId, SearchErrorCategory category,
                                  String message, Throwable cause) {
        this(builder(sessionId, providerId, category, message).cause(cause));
    }

    private SearchSessionException(Builder builder) {
        super(builder.delegate);
        this.sessionId = builder.sessionId;
    }

    public static Builder builder(SearchSessionId sessionId, ProviderId providerId, SearchErrorCategory category,
                                  String message) {
        return new Builder(sessionId, providerId, category, message);
    }

    public SearchSessionId getSessionId() {
        return sessionId;
    }

    @Override
    public String toString() {
        return getClass().getName() + "{session=" + sessionId + ", provider=" + getProviderId()
                + ", category=" + getCategory() + ", retryable=" + isRetryable() + ", message=" + getMessage() + '}';
    }

    /** Builder for {@link SearchSessionException}. */
    public static final class Builder {
        private final SearchSessionId sessionId;
        private final SearchProviderException.Builder delegate;

        private Builder(SearchSessionId sessionId, ProviderId providerId, SearchErrorCategory category,
                        String message) {
            this.sessionId = Preconditions.requireNonNull(sessionId, "sessionId");
            this.delegate = SearchProviderException.builder(providerId, category, message);
        }

        public Builder cause(Throwable cause) {
            delegate.cause(cause);
            return this;
        }

        public Builder retryable(boolean retryable) {
            delegate.retryable(retryable);
            return this;
        }

        public Builder retryAfter(Duration retryAfter) {
            delegate.retryAfter(retryAfter);
            return this;
        }

        public Builder providerStatusCode(int providerStatusCode) {
            delegate.providerStatusCode(providerStatusCode);
            return this;
        }

        public Builder detail(String key, String value) {
            delegate.detail(key, value);
            return this;
        }

        public SearchSessionException build() {
            return new SearchSessionException(this);
        }
    }
}
