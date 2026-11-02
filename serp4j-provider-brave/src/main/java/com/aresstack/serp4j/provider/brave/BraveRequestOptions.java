package com.aresstack.serp4j.provider.brave;

import com.aresstack.serp4j.core.model.ProviderRequestOptions;
import com.aresstack.serp4j.core.model.id.ProviderId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Brave-specific options of one request (ADR-006), stored in {@code SearchRequest} under the provider instance id:
 * {@code SearchRequest.builder(q).providerOptions(BraveRequestOptions.builder(id).spellcheck(false).build())}.
 *
 * <p>Unset values are not sent, so Brave's own defaults apply.
 */
public final class BraveRequestOptions implements ProviderRequestOptions {

    /** Values of {@code result_filter} (Brave reference §3.3). */
    public static final Set<String> RESULT_FILTERS = Collections.unmodifiableSet(new LinkedHashSet<String>(
            Arrays.asList("discussions", "faq", "infobox", "news", "query", "summarizer", "videos", "web",
                    "locations")));

    /** Value of {@code units}. */
    public enum Units {
        METRIC, IMPERIAL;

        String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final ProviderId providerId;
    private final Boolean spellcheck;
    private final Boolean textDecorations;
    private final Boolean extraSnippets;
    private final Boolean operators;
    private final Boolean summary;
    private final Boolean includeFetchMetadata;
    private final Units units;
    private final List<String> resultFilter;
    private final List<String> goggles;

    private BraveRequestOptions(Builder builder) {
        this.providerId = builder.providerId;
        this.spellcheck = builder.spellcheck;
        this.textDecorations = builder.textDecorations;
        this.extraSnippets = builder.extraSnippets;
        this.operators = builder.operators;
        this.summary = builder.summary;
        this.includeFetchMetadata = builder.includeFetchMetadata;
        this.units = builder.units;
        this.resultFilter = Collections.unmodifiableList(new ArrayList<String>(builder.resultFilter));
        this.goggles = Collections.unmodifiableList(new ArrayList<String>(builder.goggles));
    }

    public static Builder builder(ProviderId providerId) {
        return new Builder(Objects.requireNonNull(providerId, "providerId"));
    }

    @Override
    public ProviderId getProviderId() {
        return providerId;
    }

    public Optional<Boolean> getSpellcheck() {
        return Optional.ofNullable(spellcheck);
    }

    public Optional<Boolean> getTextDecorations() {
        return Optional.ofNullable(textDecorations);
    }

    public Optional<Boolean> getExtraSnippets() {
        return Optional.ofNullable(extraSnippets);
    }

    public Optional<Boolean> getOperators() {
        return Optional.ofNullable(operators);
    }

    /** {@code summary=true} asks Brave for a summarizer key ({@link BraveExtensions#SUMMARIZER_KEY}); not automatic. */
    public Optional<Boolean> getSummary() {
        return Optional.ofNullable(summary);
    }

    public Optional<Boolean> getIncludeFetchMetadata() {
        return Optional.ofNullable(includeFetchMetadata);
    }

    public Optional<Units> getUnits() {
        return Optional.ofNullable(units);
    }

    /** Result types to include; empty = all types available in the plan. */
    public List<String> getResultFilter() {
        return resultFilter;
    }

    /** Up to three Goggles (ids or definitions). */
    public List<String> getGoggles() {
        return goggles;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(providerId);
        builder.spellcheck = spellcheck;
        builder.textDecorations = textDecorations;
        builder.extraSnippets = extraSnippets;
        builder.operators = operators;
        builder.summary = summary;
        builder.includeFetchMetadata = includeFetchMetadata;
        builder.units = units;
        builder.resultFilter.addAll(resultFilter);
        builder.goggles.addAll(goggles);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BraveRequestOptions)) {
            return false;
        }
        BraveRequestOptions that = (BraveRequestOptions) o;
        return providerId.equals(that.providerId) && Objects.equals(spellcheck, that.spellcheck)
                && Objects.equals(textDecorations, that.textDecorations)
                && Objects.equals(extraSnippets, that.extraSnippets) && Objects.equals(operators, that.operators)
                && Objects.equals(summary, that.summary)
                && Objects.equals(includeFetchMetadata, that.includeFetchMetadata) && units == that.units
                && resultFilter.equals(that.resultFilter) && goggles.equals(that.goggles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, spellcheck, textDecorations, extraSnippets, operators, summary,
                includeFetchMetadata, units, resultFilter, goggles);
    }

    @Override
    public String toString() {
        return "BraveRequestOptions{providerId=" + providerId + ", spellcheck=" + spellcheck + ", textDecorations="
                + textDecorations + ", extraSnippets=" + extraSnippets + ", operators=" + operators + ", summary="
                + summary + ", includeFetchMetadata=" + includeFetchMetadata + ", units=" + units
                + ", resultFilter=" + resultFilter + ", goggles=" + goggles.size() + '}';
    }

    public static final class Builder {
        private final ProviderId providerId;
        private Boolean spellcheck;
        private Boolean textDecorations;
        private Boolean extraSnippets;
        private Boolean operators;
        private Boolean summary;
        private Boolean includeFetchMetadata;
        private Units units;
        private final Set<String> resultFilter = new LinkedHashSet<String>();
        private final List<String> goggles = new ArrayList<String>();

        private Builder(ProviderId providerId) {
            this.providerId = providerId;
        }

        public Builder spellcheck(boolean spellcheck) {
            this.spellcheck = spellcheck;
            return this;
        }

        public Builder textDecorations(boolean textDecorations) {
            this.textDecorations = textDecorations;
            return this;
        }

        public Builder extraSnippets(boolean extraSnippets) {
            this.extraSnippets = extraSnippets;
            return this;
        }

        public Builder operators(boolean operators) {
            this.operators = operators;
            return this;
        }

        public Builder summary(boolean summary) {
            this.summary = summary;
            return this;
        }

        public Builder includeFetchMetadata(boolean includeFetchMetadata) {
            this.includeFetchMetadata = includeFetchMetadata;
            return this;
        }

        public Builder units(Units units) {
            this.units = units;
            return this;
        }

        /** @throws IllegalArgumentException for a value outside {@link #RESULT_FILTERS} */
        public Builder resultFilter(String... types) {
            return resultFilter(Arrays.asList(types));
        }

        public Builder resultFilter(Collection<String> types) {
            for (String type : types) {
                String code = Objects.requireNonNull(type, "type").trim().toLowerCase(Locale.ROOT);
                if (!RESULT_FILTERS.contains(code)) {
                    throw new IllegalArgumentException("unknown result_filter value '" + type + "'; allowed: "
                            + RESULT_FILTERS);
                }
                resultFilter.add(code);
            }
            return this;
        }

        public Builder goggle(String goggle) {
            String value = Objects.requireNonNull(goggle, "goggle").trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("goggle must not be blank");
            }
            goggles.add(value);
            return this;
        }

        /** @throws IllegalArgumentException for more than three Goggles */
        public BraveRequestOptions build() {
            if (goggles.size() > 3) {
                throw new IllegalArgumentException("Brave accepts at most 3 goggles, got " + goggles.size());
            }
            return new BraveRequestOptions(this);
        }
    }
}
