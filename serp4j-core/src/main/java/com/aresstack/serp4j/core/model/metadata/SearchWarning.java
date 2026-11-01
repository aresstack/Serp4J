package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.internal.Codes;
import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.id.ProviderId;

import java.util.Objects;
import java.util.Optional;

/**
 * A non-fatal problem recorded in {@link SearchMetadata}: partial results, a feature the provider ignored, a
 * failed source of a merged response, an unknown element that was kept generically … The code is an open value
 * type; constants cover the cases the core knows. Details must never contain secrets.
 */
public final class SearchWarning {

    public static final String PARTIAL_RESULTS = "partial_results";
    public static final String FEATURE_IGNORED = "feature_ignored";
    public static final String PROVIDER_FAILED = "provider_failed";
    public static final String UNKNOWN_ELEMENT = "unknown_element";
    public static final String MAPPING_INCOMPLETE = "mapping_incomplete";
    public static final String DEPRECATED_PARAMETER = "deprecated_parameter";

    private final String code;
    private final String message;
    private final ProviderId provider;
    private final StructuredValue details;

    private SearchWarning(Builder builder) {
        this.code = builder.code;
        this.message = builder.message;
        this.provider = builder.provider;
        this.details = builder.details;
    }

    public static Builder builder(String code, String message) {
        return new Builder(code, message);
    }

    public static SearchWarning of(String code, String message) {
        return builder(code, message).build();
    }

    public static SearchWarning of(String code, String message, ProviderId provider) {
        return builder(code, message).provider(provider).build();
    }

    /** Returns a builder pre-filled with this instance's values. */
    public Builder toBuilder() {
        Builder builder = new Builder(code, message);
        builder.provider = provider;
        builder.details = details;
        return builder;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /** The provider the warning originates from; empty when it concerns the whole (merged) response. */
    public Optional<ProviderId> getProvider() {
        return Optional.ofNullable(provider);
    }

    public Optional<StructuredValue> getDetails() {
        return Optional.ofNullable(details);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchWarning)) {
            return false;
        }
        SearchWarning that = (SearchWarning) o;
        return code.equals(that.code)
                && message.equals(that.message)
                && Objects.equals(provider, that.provider)
                && Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, provider, details);
    }

    @Override
    public String toString() {
        return "SearchWarning{" + code + (provider == null ? "" : "@" + provider) + ": " + message + '}';
    }

    /** Builder for {@link SearchWarning}. */
    public static final class Builder {
        private final String code;
        private final String message;
        private ProviderId provider;
        private StructuredValue details;

        private Builder(String code, String message) {
            this.code = Codes.normalize(code, "warning code");
            this.message = Preconditions.requireText(message, "message");
        }

        public Builder provider(ProviderId provider) {
            this.provider = provider;
            return this;
        }

        public Builder details(StructuredValue details) {
            this.details = details;
            return this;
        }

        public SearchWarning build() {
            return new SearchWarning(this);
        }
    }
}
