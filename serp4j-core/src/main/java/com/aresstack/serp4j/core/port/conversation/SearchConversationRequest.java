package com.aresstack.serp4j.core.port.conversation;

import com.aresstack.serp4j.core.annotation.Experimental;
import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.ProviderRequestOptions;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.port.capability.ResponseFeature;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * One turn of a conversational search (WP2 §11.3): the question or follow-up, an optional expected turn index for
 * optimistic concurrency control, requested response features and provider options.
 */
@Experimental("conversational search stabilises with the first Google AI Mode adapter (Paket G)")
public final class SearchConversationRequest {

    private final String message;
    private final Integer expectedTurnIndex;
    private final Set<ResponseFeature> requestedFeatures;
    private final Map<ProviderId, ProviderRequestOptions> providerOptions;

    private SearchConversationRequest(Builder builder) {
        this.message = builder.message;
        this.expectedTurnIndex = builder.expectedTurnIndex;
        this.requestedFeatures = builder.requestedFeatures.isEmpty()
                ? Collections.<ResponseFeature>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.requestedFeatures));
        this.providerOptions = Preconditions.immutableMap(builder.providerOptions, "providerOptions");
    }

    public static Builder builder(String message) {
        return new Builder(message);
    }

    public static SearchConversationRequest of(String message) {
        return builder(message).build();
    }

    /** The question (initial turn) or follow-up. */
    public String getMessage() {
        return message;
    }

    /** 0 = initial turn, 1 = first follow-up …; when set and not matching, the turn fails with INVALID_REQUEST. */
    public OptionalInt getExpectedTurnIndex() {
        return expectedTurnIndex == null ? OptionalInt.empty() : OptionalInt.of(expectedTurnIndex);
    }

    public Set<ResponseFeature> getRequestedFeatures() {
        return requestedFeatures;
    }

    public <T extends ProviderRequestOptions> Optional<T> getProviderOptions(ProviderId providerId, Class<T> type) {
        Preconditions.requireNonNull(type, "type");
        ProviderRequestOptions options = providerOptions.get(Preconditions.requireNonNull(providerId, "providerId"));
        return type.isInstance(options) ? Optional.of(type.cast(options)) : Optional.<T>empty();
    }

    public Map<ProviderId, ProviderRequestOptions> getProviderOptions() {
        return providerOptions;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(message);
        builder.expectedTurnIndex = expectedTurnIndex;
        builder.requestedFeatures.addAll(requestedFeatures);
        builder.providerOptions.putAll(providerOptions);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchConversationRequest)) {
            return false;
        }
        SearchConversationRequest that = (SearchConversationRequest) o;
        return message.equals(that.message)
                && Objects.equals(expectedTurnIndex, that.expectedTurnIndex)
                && requestedFeatures.equals(that.requestedFeatures)
                && providerOptions.equals(that.providerOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, expectedTurnIndex, requestedFeatures, providerOptions);
    }

    @Override
    public String toString() {
        return "SearchConversationRequest{message='" + message + "', expectedTurnIndex=" + expectedTurnIndex
                + ", requestedFeatures=" + requestedFeatures + '}';
    }

    /** Builder for {@link SearchConversationRequest}. */
    public static final class Builder {
        private final String message;
        private Integer expectedTurnIndex;
        private final Set<ResponseFeature> requestedFeatures = EnumSet.noneOf(ResponseFeature.class);
        private final Map<ProviderId, ProviderRequestOptions> providerOptions =
                new LinkedHashMap<ProviderId, ProviderRequestOptions>();

        private Builder(String message) {
            this.message = Preconditions.requireText(message, "message");
        }

        public Builder expectedTurnIndex(int expectedTurnIndex) {
            this.expectedTurnIndex = Preconditions.requireNonNegative(expectedTurnIndex, "expectedTurnIndex");
            return this;
        }

        public Builder requestedFeature(ResponseFeature feature) {
            requestedFeatures.add(Preconditions.requireNonNull(feature, "feature"));
            return this;
        }

        public Builder requestedFeatures(Collection<ResponseFeature> features) {
            for (ResponseFeature feature : Preconditions.requireNonNull(features, "features")) {
                requestedFeature(feature);
            }
            return this;
        }

        public Builder providerOptions(ProviderRequestOptions options) {
            Preconditions.requireNonNull(options, "options");
            providerOptions.put(Preconditions.requireNonNull(options.getProviderId(), "options.providerId"), options);
            return this;
        }

        public SearchConversationRequest build() {
            return new SearchConversationRequest(this);
        }
    }
}
