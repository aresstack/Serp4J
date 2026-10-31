package com.aresstack.serp4j.core.model;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A stateless search request (WP1 §4.3, ADR-007). Immutable; create with {@link #builder(String)}.
 *
 * <p>Only the query is mandatory. The vertical defaults to {@link SearchVertical#WEB}; without an engine the
 * provider uses its default engine for the vertical. Aspects that the caller insists on are marked with
 * {@link Builder#require(SearchFeature...)}; everything else is {@link RequirementLevel#OPTIONAL} and may be
 * ignored by a provider that lacks the capability (reported as a warning).</p>
 */
public final class SearchRequest {

    private final String query;
    private final SearchVertical vertical;
    private final SearchEngineId engine;
    private final SearchLocale locale;
    private final DeviceType device;
    private final Paging paging;
    private final SafeSearch safeSearch;
    private final TimeRange timeRange;
    private final RawPayloadPolicy rawPayloadPolicy;
    private final Set<SearchFeature> requiredFeatures;
    private final Map<ProviderId, ProviderRequestOptions> providerOptions;

    private SearchRequest(Builder builder) {
        this.query = builder.query;
        this.vertical = builder.vertical;
        this.engine = builder.engine;
        this.locale = builder.locale;
        this.device = builder.device;
        this.paging = builder.paging;
        this.safeSearch = builder.safeSearch;
        this.timeRange = builder.timeRange;
        this.rawPayloadPolicy = builder.rawPayloadPolicy;
        EnumSet<SearchFeature> required = EnumSet.of(SearchFeature.QUERY_TEXT);
        required.addAll(builder.requiredFeatures);
        this.requiredFeatures = Collections.unmodifiableSet(required);
        this.providerOptions = Collections.unmodifiableMap(
                new LinkedHashMap<ProviderId, ProviderRequestOptions>(builder.providerOptions));
    }

    /** Starts a request; {@code query} is trimmed and must not be blank. */
    public static Builder builder(String query) {
        return new Builder(query);
    }

    public String getQuery() {
        return query;
    }

    public SearchVertical getVertical() {
        return vertical;
    }

    /** Preferred engine; empty means the provider's default engine. */
    public Optional<SearchEngineId> getEngine() {
        return Optional.ofNullable(engine);
    }

    /** Engine and vertical as a target, present only when an engine was requested. */
    public Optional<SearchTarget> getTarget() {
        return engine == null ? Optional.<SearchTarget>empty() : Optional.of(SearchTarget.of(engine, vertical));
    }

    public Optional<SearchLocale> getLocale() {
        return Optional.ofNullable(locale);
    }

    public Optional<DeviceType> getDevice() {
        return Optional.ofNullable(device);
    }

    public Paging getPaging() {
        return paging;
    }

    public Optional<SafeSearch> getSafeSearch() {
        return Optional.ofNullable(safeSearch);
    }

    public Optional<TimeRange> getTimeRange() {
        return Optional.ofNullable(timeRange);
    }

    public RawPayloadPolicy getRawPayloadPolicy() {
        return rawPayloadPolicy;
    }

    /** Features the caller insists on; always contains {@link SearchFeature#QUERY_TEXT}. */
    public Set<SearchFeature> getRequiredFeatures() {
        return requiredFeatures;
    }

    public RequirementLevel getRequirementLevel(SearchFeature feature) {
        return requiredFeatures.contains(Preconditions.requireNonNull(feature, "feature"))
                ? RequirementLevel.REQUIRED : RequirementLevel.OPTIONAL;
    }

    /**
     * The features this request actually makes use of, derived from its content: {@link SearchFeature#QUERY_TEXT}
     * always; {@link SearchFeature#PAGINATION_OFFSET} for an offset greater than zero; {@link SearchFeature#MAX_RESULTS}
     * for a limit other than {@link Paging#DEFAULT_LIMIT}; {@link SearchFeature#COUNTRY}, {@link SearchFeature#LANGUAGE}
     * and {@link SearchFeature#GEO_LOCATION} according to the locale; {@link SearchFeature#FRESHNESS},
     * {@link SearchFeature#SAFE_SEARCH} and {@link SearchFeature#DEVICE} when set; {@link SearchFeature#RAW_RESPONSE}
     * for a raw payload policy other than {@link RawPayloadPolicy#NONE}.
     */
    public Set<SearchFeature> getUsedFeatures() {
        EnumSet<SearchFeature> used = EnumSet.of(SearchFeature.QUERY_TEXT);
        if (paging.getOffset() > 0) {
            used.add(SearchFeature.PAGINATION_OFFSET);
        }
        if (paging.getLimit() != Paging.DEFAULT_LIMIT) {
            used.add(SearchFeature.MAX_RESULTS);
        }
        if (locale != null) {
            if (locale.getCountryCode().isPresent()) {
                used.add(SearchFeature.COUNTRY);
            }
            if (locale.getLanguageCode().isPresent() || locale.getUiLanguage().isPresent()) {
                used.add(SearchFeature.LANGUAGE);
            }
            if (locale.getLocation().isPresent()) {
                used.add(SearchFeature.GEO_LOCATION);
            }
        }
        if (timeRange != null) {
            used.add(SearchFeature.FRESHNESS);
        }
        if (safeSearch != null) {
            used.add(SearchFeature.SAFE_SEARCH);
        }
        if (device != null) {
            used.add(SearchFeature.DEVICE);
        }
        if (rawPayloadPolicy != RawPayloadPolicy.NONE) {
            used.add(SearchFeature.RAW_RESPONSE);
        }
        return Collections.unmodifiableSet(used);
    }

    /** Options addressed to {@code providerId}, if present and of the expected type. */
    public <T extends ProviderRequestOptions> Optional<T> getProviderOptions(ProviderId providerId, Class<T> type) {
        ProviderRequestOptions options = providerOptions.get(Preconditions.requireNonNull(providerId, "providerId"));
        Preconditions.requireNonNull(type, "type");
        return type.isInstance(options) ? Optional.of(type.cast(options)) : Optional.<T>empty();
    }

    /** Whether options for {@code providerId} are present (of whatever type). */
    public boolean hasProviderOptions(ProviderId providerId) {
        return providerOptions.containsKey(Preconditions.requireNonNull(providerId, "providerId"));
    }

    /** All provider options keyed by provider. */
    public Map<ProviderId, ProviderRequestOptions> getProviderOptions() {
        return providerOptions;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(query);
        builder.vertical = vertical;
        builder.engine = engine;
        builder.locale = locale;
        builder.device = device;
        builder.paging = paging;
        builder.safeSearch = safeSearch;
        builder.timeRange = timeRange;
        builder.rawPayloadPolicy = rawPayloadPolicy;
        builder.requiredFeatures.addAll(requiredFeatures);
        builder.providerOptions.putAll(providerOptions);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchRequest)) {
            return false;
        }
        SearchRequest that = (SearchRequest) o;
        return query.equals(that.query)
                && vertical.equals(that.vertical)
                && Objects.equals(engine, that.engine)
                && Objects.equals(locale, that.locale)
                && device == that.device
                && paging.equals(that.paging)
                && safeSearch == that.safeSearch
                && Objects.equals(timeRange, that.timeRange)
                && rawPayloadPolicy == that.rawPayloadPolicy
                && requiredFeatures.equals(that.requiredFeatures)
                && providerOptions.equals(that.providerOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, vertical, engine, locale, device, paging, safeSearch, timeRange,
                rawPayloadPolicy, requiredFeatures, providerOptions);
    }

    @Override
    public String toString() {
        return "SearchRequest{query='" + query + "', vertical=" + vertical + ", engine=" + engine
                + ", locale=" + locale + ", device=" + device + ", paging=" + paging
                + ", safeSearch=" + safeSearch + ", timeRange=" + timeRange
                + ", rawPayloadPolicy=" + rawPayloadPolicy + ", required=" + requiredFeatures
                + ", providerOptions=" + providerOptions.keySet() + '}';
    }

    /** Builder for {@link SearchRequest}. */
    public static final class Builder {
        private final String query;
        private SearchVertical vertical = SearchVertical.WEB;
        private SearchEngineId engine;
        private SearchLocale locale;
        private DeviceType device;
        private Paging paging = Paging.DEFAULT;
        private SafeSearch safeSearch;
        private TimeRange timeRange;
        private RawPayloadPolicy rawPayloadPolicy = RawPayloadPolicy.NONE;
        private final EnumSet<SearchFeature> requiredFeatures = EnumSet.noneOf(SearchFeature.class);
        private final Map<ProviderId, ProviderRequestOptions> providerOptions =
                new LinkedHashMap<ProviderId, ProviderRequestOptions>();

        private Builder(String query) {
            this.query = Preconditions.requireText(query, "query");
        }

        public Builder vertical(SearchVertical vertical) {
            this.vertical = Preconditions.requireNonNull(vertical, "vertical");
            return this;
        }

        /** Preferred engine; {@code null} means provider default. */
        public Builder engine(SearchEngineId engine) {
            this.engine = engine;
            return this;
        }

        /** Sets engine and vertical at once. */
        public Builder target(SearchTarget target) {
            Preconditions.requireNonNull(target, "target");
            this.engine = target.getEngine();
            this.vertical = target.getVertical();
            return this;
        }

        public Builder locale(SearchLocale locale) {
            this.locale = locale == null || locale.isEmpty() ? null : locale;
            return this;
        }

        public Builder device(DeviceType device) {
            this.device = device;
            return this;
        }

        public Builder paging(Paging paging) {
            this.paging = Preconditions.requireNonNull(paging, "paging");
            return this;
        }

        public Builder offset(int offset) {
            this.paging = paging.withOffset(offset);
            return this;
        }

        public Builder limit(int limit) {
            this.paging = paging.withLimit(limit);
            return this;
        }

        public Builder safeSearch(SafeSearch safeSearch) {
            this.safeSearch = safeSearch;
            return this;
        }

        public Builder timeRange(TimeRange timeRange) {
            this.timeRange = timeRange;
            return this;
        }

        public Builder rawPayloadPolicy(RawPayloadPolicy policy) {
            this.rawPayloadPolicy = Preconditions.requireNonNull(policy, "rawPayloadPolicy");
            return this;
        }

        /**
         * Marks features the provider must honour (otherwise {@code UNSUPPORTED}). A required feature is checked
         * against the provider's capabilities even when the request sets no corresponding option.
         */
        public Builder require(SearchFeature... features) {
            for (SearchFeature feature : Preconditions.requireNonNull(features, "features")) {
                requiredFeatures.add(Preconditions.requireNonNull(feature, "feature"));
            }
            return this;
        }

        public Builder require(Collection<SearchFeature> features) {
            for (SearchFeature feature : Preconditions.requireNonNull(features, "features")) {
                requiredFeatures.add(Preconditions.requireNonNull(feature, "feature"));
            }
            return this;
        }

        /** Adds options for their provider, replacing options previously set for the same provider. */
        public Builder providerOptions(ProviderRequestOptions options) {
            Preconditions.requireNonNull(options, "options");
            providerOptions.put(Preconditions.requireNonNull(options.getProviderId(), "options.providerId"), options);
            return this;
        }

        public SearchRequest build() {
            return new SearchRequest(this);
        }
    }
}
