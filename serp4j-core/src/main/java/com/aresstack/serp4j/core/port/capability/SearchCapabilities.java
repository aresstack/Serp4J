package com.aresstack.serp4j.core.port.capability;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.Paging;
import com.aresstack.serp4j.core.model.RequirementLevel;
import com.aresstack.serp4j.core.model.SearchFeature;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * What a provider instance can do (WP2 §6): the engine/vertical targets it serves, the request features it
 * honours, what its responses contain, its limits and – for conversational providers – the session limits.
 * Capabilities are static per instance; a configuration change means a new instance.
 */
public final class SearchCapabilities {

    private final Set<SearchTarget> targets;
    private final Set<SearchFeature> features;
    private final Set<ResponseFeature> responseFeatures;
    private final Integer maxResultsPerRequest;
    private final Integer maxOffset;
    private final ConversationalCapabilities conversational;

    private SearchCapabilities(Builder builder) {
        this.targets = Preconditions.immutableSet(builder.targets, "targets");
        this.features = Collections.unmodifiableSet(EnumSet.copyOf(builder.features));
        this.responseFeatures = builder.responseFeatures.isEmpty()
                ? Collections.<ResponseFeature>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.responseFeatures));
        this.maxResultsPerRequest = builder.maxResultsPerRequest;
        this.maxOffset = builder.maxOffset;
        this.conversational = builder.conversational;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Engine/vertical combinations the provider serves; never empty. */
    public Set<SearchTarget> getTargets() {
        return targets;
    }

    /** Supported request features; always contains {@link SearchFeature#QUERY_TEXT}. */
    public Set<SearchFeature> getFeatures() {
        return features;
    }

    public Set<ResponseFeature> getResponseFeatures() {
        return responseFeatures;
    }

    /** Empty = no known limit. */
    public OptionalInt getMaxResultsPerRequest() {
        return maxResultsPerRequest == null ? OptionalInt.empty() : OptionalInt.of(maxResultsPerRequest);
    }

    /** Empty = no known limit. */
    public OptionalInt getMaxOffset() {
        return maxOffset == null ? OptionalInt.empty() : OptionalInt.of(maxOffset);
    }

    /** Present if and only if the provider implements {@code ConversationalSearchProvider}. */
    public Optional<ConversationalCapabilities> getConversational() {
        return Optional.ofNullable(conversational);
    }

    public boolean supports(SearchTarget target) {
        return targets.contains(Preconditions.requireNonNull(target, "target"));
    }

    /** Whether any target of the given vertical is served (engine left to the provider). */
    public boolean supports(SearchVertical vertical) {
        Preconditions.requireNonNull(vertical, "vertical");
        for (SearchTarget target : targets) {
            if (target.getVertical().equals(vertical)) {
                return true;
            }
        }
        return false;
    }

    public boolean supports(SearchFeature feature) {
        return features.contains(Preconditions.requireNonNull(feature, "feature"));
    }

    public boolean delivers(ResponseFeature feature) {
        return responseFeatures.contains(Preconditions.requireNonNull(feature, "feature"));
    }

    /**
     * Checks a request against these capabilities and lists every unsupported aspect: the target (always
     * required), each feature the request uses or explicitly requires with the request's requirement level, and
     * paging beyond the limits. A feature marked with {@link SearchRequest.Builder#require} is checked even when
     * the request sets no corresponding option, so {@code require} never passes silently.
     */
    public CapabilityCheck check(SearchRequest request) {
        Preconditions.requireNonNull(request, "request");
        List<CapabilityViolation> violations = new ArrayList<CapabilityViolation>();
        Optional<SearchTarget> target = request.getTarget();
        if (target.isPresent()) {
            if (!supports(target.get())) {
                violations.add(CapabilityViolation.ofTarget(target.get(),
                        "target " + target.get() + " is not served; supported: " + targets));
            }
        } else if (!supports(request.getVertical())) {
            violations.add(CapabilityViolation.ofVertical(request.getVertical(),
                    "vertical " + request.getVertical() + " is not served; supported: " + targets));
        }
        EnumSet<SearchFeature> checked = EnumSet.copyOf(request.getUsedFeatures());
        checked.addAll(request.getRequiredFeatures());
        for (SearchFeature feature : checked) {
            if (!features.contains(feature)) {
                violations.add(CapabilityViolation.ofFeature(feature, request.getRequirementLevel(feature),
                        "feature " + feature + " is not supported"));
            }
        }
        Paging paging = request.getPaging();
        if (maxResultsPerRequest != null && paging.getLimit() > maxResultsPerRequest) {
            violations.add(CapabilityViolation.ofFeature(SearchFeature.MAX_RESULTS,
                    request.getRequirementLevel(SearchFeature.MAX_RESULTS),
                    "limit " + paging.getLimit() + " exceeds maximum of " + maxResultsPerRequest));
        }
        if (maxOffset != null && paging.getOffset() > maxOffset) {
            violations.add(CapabilityViolation.ofFeature(SearchFeature.PAGINATION_OFFSET,
                    request.getRequirementLevel(SearchFeature.PAGINATION_OFFSET),
                    "offset " + paging.getOffset() + " exceeds maximum of " + maxOffset));
        }
        return CapabilityCheck.of(violations);
    }

    /** Shortcut: {@code check(request).isSupported()}. */
    public boolean supports(SearchRequest request) {
        return check(request).isSupported();
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.targets.addAll(targets);
        builder.features.addAll(features);
        builder.responseFeatures.addAll(responseFeatures);
        builder.maxResultsPerRequest = maxResultsPerRequest;
        builder.maxOffset = maxOffset;
        builder.conversational = conversational;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchCapabilities)) {
            return false;
        }
        SearchCapabilities that = (SearchCapabilities) o;
        return targets.equals(that.targets)
                && features.equals(that.features)
                && responseFeatures.equals(that.responseFeatures)
                && Objects.equals(maxResultsPerRequest, that.maxResultsPerRequest)
                && Objects.equals(maxOffset, that.maxOffset)
                && Objects.equals(conversational, that.conversational);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targets, features, responseFeatures, maxResultsPerRequest, maxOffset, conversational);
    }

    @Override
    public String toString() {
        return "SearchCapabilities{targets=" + targets + ", features=" + features
                + ", responseFeatures=" + responseFeatures + ", maxResultsPerRequest=" + maxResultsPerRequest
                + ", maxOffset=" + maxOffset + ", conversational=" + (conversational != null) + '}';
    }

    /** Builder for {@link SearchCapabilities}; {@link SearchFeature#QUERY_TEXT} is always included. */
    public static final class Builder {
        private final Set<SearchTarget> targets = new LinkedHashSet<SearchTarget>();
        private final Set<SearchFeature> features = EnumSet.of(SearchFeature.QUERY_TEXT);
        private final Set<ResponseFeature> responseFeatures = EnumSet.noneOf(ResponseFeature.class);
        private Integer maxResultsPerRequest;
        private Integer maxOffset;
        private ConversationalCapabilities conversational;

        private Builder() {
        }

        public Builder target(SearchTarget target) {
            targets.add(Preconditions.requireNonNull(target, "target"));
            return this;
        }

        public Builder target(SearchEngineId engine, SearchVertical vertical) {
            return target(SearchTarget.of(engine, vertical));
        }

        public Builder targets(Collection<SearchTarget> targets) {
            for (SearchTarget target : Preconditions.requireNonNull(targets, "targets")) {
                target(target);
            }
            return this;
        }

        public Builder feature(SearchFeature feature) {
            features.add(Preconditions.requireNonNull(feature, "feature"));
            return this;
        }

        public Builder features(SearchFeature... features) {
            for (SearchFeature feature : Preconditions.requireNonNull(features, "features")) {
                feature(feature);
            }
            return this;
        }

        public Builder features(Collection<SearchFeature> features) {
            for (SearchFeature feature : Preconditions.requireNonNull(features, "features")) {
                feature(feature);
            }
            return this;
        }

        public Builder responseFeature(ResponseFeature feature) {
            responseFeatures.add(Preconditions.requireNonNull(feature, "feature"));
            return this;
        }

        public Builder responseFeatures(ResponseFeature... features) {
            for (ResponseFeature feature : Preconditions.requireNonNull(features, "features")) {
                responseFeature(feature);
            }
            return this;
        }

        public Builder responseFeatures(Collection<ResponseFeature> features) {
            for (ResponseFeature feature : Preconditions.requireNonNull(features, "features")) {
                responseFeature(feature);
            }
            return this;
        }

        public Builder maxResultsPerRequest(int maxResultsPerRequest) {
            this.maxResultsPerRequest = Preconditions.requirePositive(maxResultsPerRequest, "maxResultsPerRequest");
            return this;
        }

        public Builder maxOffset(int maxOffset) {
            this.maxOffset = Preconditions.requireNonNegative(maxOffset, "maxOffset");
            return this;
        }

        /** Set only for providers that implement {@code ConversationalSearchProvider}. */
        public Builder conversational(ConversationalCapabilities conversational) {
            this.conversational = conversational;
            return this;
        }

        /** @throws IllegalStateException if no target was declared */
        public SearchCapabilities build() {
            if (targets.isEmpty()) {
                throw new IllegalStateException("capabilities need at least one target");
            }
            return new SearchCapabilities(this);
        }
    }
}
