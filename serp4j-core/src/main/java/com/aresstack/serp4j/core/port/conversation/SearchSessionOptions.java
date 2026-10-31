package com.aresstack.serp4j.core.port.conversation;

import com.aresstack.serp4j.core.annotation.Experimental;
import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.ProviderRequestOptions;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchTarget;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Options for opening a {@link SearchSession} (WP2 §11.3): target, locale, limits and provider options. */
@Experimental("conversational search stabilises with the first Google AI Mode adapter (Paket G)")
public final class SearchSessionOptions {

    private final SearchTarget target;
    private final SearchLocale locale;
    private final Duration idleTimeout;
    private final Integer maxTurns;
    private final RawPayloadPolicy rawPayloadPolicy;
    private final Map<ProviderId, ProviderRequestOptions> providerOptions;

    private SearchSessionOptions(Builder builder) {
        this.target = builder.target;
        this.locale = builder.locale;
        this.idleTimeout = builder.idleTimeout;
        this.maxTurns = builder.maxTurns;
        this.rawPayloadPolicy = builder.rawPayloadPolicy;
        this.providerOptions = Preconditions.immutableMap(builder.providerOptions, "providerOptions");
    }

    /** @param target e.g. {@code SearchTarget.of(SearchEngineId.GOOGLE, SearchVertical.AI_MODE)} */
    public static Builder builder(SearchTarget target) {
        return new Builder(target);
    }

    public SearchTarget getTarget() {
        return target;
    }

    public Optional<SearchLocale> getLocale() {
        return Optional.ofNullable(locale);
    }

    /** Requested idle timeout; the provider caps it at its capability value. */
    public Optional<Duration> getIdleTimeout() {
        return Optional.ofNullable(idleTimeout);
    }

    /** Requested turn limit; the provider caps it at its capability value. */
    public OptionalInt getMaxTurns() {
        return maxTurns == null ? OptionalInt.empty() : OptionalInt.of(maxTurns);
    }

    public RawPayloadPolicy getRawPayloadPolicy() {
        return rawPayloadPolicy;
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
        Builder builder = new Builder(target);
        builder.locale = locale;
        builder.idleTimeout = idleTimeout;
        builder.maxTurns = maxTurns;
        builder.rawPayloadPolicy = rawPayloadPolicy;
        builder.providerOptions.putAll(providerOptions);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchSessionOptions)) {
            return false;
        }
        SearchSessionOptions that = (SearchSessionOptions) o;
        return target.equals(that.target)
                && Objects.equals(locale, that.locale)
                && Objects.equals(idleTimeout, that.idleTimeout)
                && Objects.equals(maxTurns, that.maxTurns)
                && rawPayloadPolicy == that.rawPayloadPolicy
                && providerOptions.equals(that.providerOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, locale, idleTimeout, maxTurns, rawPayloadPolicy, providerOptions);
    }

    @Override
    public String toString() {
        return "SearchSessionOptions{target=" + target + ", locale=" + locale + ", idleTimeout=" + idleTimeout
                + ", maxTurns=" + maxTurns + ", rawPayloadPolicy=" + rawPayloadPolicy + '}';
    }

    /** Builder for {@link SearchSessionOptions}. */
    public static final class Builder {
        private final SearchTarget target;
        private SearchLocale locale;
        private Duration idleTimeout;
        private Integer maxTurns;
        private RawPayloadPolicy rawPayloadPolicy = RawPayloadPolicy.NONE;
        private final Map<ProviderId, ProviderRequestOptions> providerOptions =
                new LinkedHashMap<ProviderId, ProviderRequestOptions>();

        private Builder(SearchTarget target) {
            this.target = Preconditions.requireNonNull(target, "target");
        }

        public Builder locale(SearchLocale locale) {
            this.locale = locale == null || locale.isEmpty() ? null : locale;
            return this;
        }

        public Builder idleTimeout(Duration idleTimeout) {
            if (idleTimeout != null && (idleTimeout.isNegative() || idleTimeout.isZero())) {
                throw new IllegalArgumentException("idleTimeout must be positive but was " + idleTimeout);
            }
            this.idleTimeout = idleTimeout;
            return this;
        }

        public Builder maxTurns(int maxTurns) {
            this.maxTurns = Preconditions.requirePositive(maxTurns, "maxTurns");
            return this;
        }

        public Builder rawPayloadPolicy(RawPayloadPolicy policy) {
            this.rawPayloadPolicy = Preconditions.requireNonNull(policy, "policy");
            return this;
        }

        /** Adds or replaces the options for the provider they belong to. */
        public Builder providerOptions(ProviderRequestOptions options) {
            Preconditions.requireNonNull(options, "options");
            providerOptions.put(Preconditions.requireNonNull(options.getProviderId(), "options.providerId"), options);
            return this;
        }

        public SearchSessionOptions build() {
            return new SearchSessionOptions(this);
        }
    }
}
