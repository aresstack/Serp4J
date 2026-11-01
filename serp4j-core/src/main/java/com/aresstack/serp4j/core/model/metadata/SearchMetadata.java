package com.aresstack.serp4j.core.model.metadata;

import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.DeviceType;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.ext.Extensions;
import com.aresstack.serp4j.core.model.ext.RawPayload;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Metadata of a search response (ADR-003): who answered, what was asked, the locale and device that were
 * effectively used, paging, execution, usage, warnings and the optional raw payload.
 *
 * <p><strong>Effective locale and device (WP1 §4.8).</strong> {@link #getLocale()} and {@link #getDevice()} hold
 * what the provider actually searched with: the provider's echo when it reports one, otherwise the values of the
 * request. Both are optional; a merged response (ADR-008) carries them only when every contribution agrees, the
 * merger sets them and leaves them empty when the contributions differ.</p>
 *
 * <p><strong>Provider contract (ADR-008, core contract B).</strong> {@link #getProvider()}, {@link #getEngine()}
 * and {@link #getVertical()} are optional because a merged response has no single source. The invariants are
 * enforced by {@link Builder#build()}:</p>
 * <ul>
 * <li>A <em>single-provider response</em> has a provider; its {@link #getContributingProviders()} is exactly that
 * provider. Every provider adapter must set the provider.</li>
 * <li>A <em>merged response</em> has no provider but a non-empty, duplicate-free list of contributing providers
 * in the merger's order.</li>
 * <li>{@link #getContributingProviders()} is therefore never empty, and {@link #getQuery()} is always present.</li>
 * </ul>
 */
public final class SearchMetadata {

    private final ProviderId provider;
    private final SearchEngineId engine;
    private final SearchVertical vertical;
    private final List<ProviderId> contributingProviders;
    private final QueryInfo query;
    private final SearchLocale locale;
    private final DeviceType device;
    private final PageInfo page;
    private final ExecutionInfo execution;
    private final ProviderUsage usage;
    private final boolean complete;
    private final List<SearchWarning> warnings;
    private final RawPayload rawPayload;
    private final ConversationContext conversation;
    private final Extensions extensions;

    private SearchMetadata(Builder builder, List<ProviderId> contributingProviders) {
        this.provider = builder.provider;
        this.engine = builder.engine;
        this.vertical = builder.vertical;
        this.contributingProviders = contributingProviders;
        this.query = builder.query;
        this.locale = builder.locale;
        this.device = builder.device;
        this.page = builder.page;
        this.execution = builder.execution;
        this.usage = builder.usage;
        this.complete = builder.complete;
        this.warnings = Preconditions.immutableList(builder.warnings, "warnings");
        this.rawPayload = builder.rawPayload;
        this.conversation = builder.conversation;
        this.extensions = builder.extensions;
    }

    public static Builder builder(QueryInfo query) {
        return new Builder(query);
    }

    /** Builder for a single-provider response with the engine and vertical that were actually searched. */
    public static Builder forProvider(ProviderId provider, SearchEngineId engine, SearchVertical vertical,
                                      QueryInfo query) {
        return builder(query).provider(provider).engine(engine).vertical(vertical);
    }

    /**
     * Builder for a merged response (ADR-008); {@code contributingProviders} must not be empty. Nothing is derived
     * from the contributions here: the merger sets {@link Builder#locale(SearchLocale)} and
     * {@link Builder#device(DeviceType)} only when all contributions agree and leaves them empty otherwise.
     */
    public static Builder forMerged(Collection<ProviderId> contributingProviders, QueryInfo query) {
        return builder(query).contributingProviders(contributingProviders);
    }

    /** The provider that produced this response; empty for merged responses. */
    public Optional<ProviderId> getProvider() {
        return Optional.ofNullable(provider);
    }

    /** The engine that was actually searched; empty when unknown or mixed. */
    public Optional<SearchEngineId> getEngine() {
        return Optional.ofNullable(engine);
    }

    /** The vertical that was actually searched; empty when unknown or mixed. */
    public Optional<SearchVertical> getVertical() {
        return Optional.ofNullable(vertical);
    }

    /** Engine and vertical as one target, when both are known. */
    public Optional<SearchTarget> getTarget() {
        return engine == null || vertical == null
                ? Optional.<SearchTarget>empty()
                : Optional.of(SearchTarget.of(engine, vertical));
    }

    /** All providers that contributed; never empty. Exactly {@link #getProvider()} for single-provider responses. */
    public List<ProviderId> getContributingProviders() {
        return contributingProviders;
    }

    public boolean isSingleProviderResponse() {
        return provider != null;
    }

    public boolean isMergedResponse() {
        return provider == null;
    }

    public QueryInfo getQuery() {
        return query;
    }

    /**
     * The locale that was effectively used: the provider's echo, otherwise the request's locale; empty when
     * unknown or, in a merged response, when the contributions differ.
     */
    public Optional<SearchLocale> getLocale() {
        return Optional.ofNullable(locale);
    }

    /**
     * The device that was effectively used: the provider's echo, otherwise the request's device; empty when
     * unknown or, in a merged response, when the contributions differ.
     */
    public Optional<DeviceType> getDevice() {
        return Optional.ofNullable(device);
    }

    public Optional<PageInfo> getPage() {
        return Optional.ofNullable(page);
    }

    public Optional<ExecutionInfo> getExecution() {
        return Optional.ofNullable(execution);
    }

    public Optional<ProviderUsage> getUsage() {
        return Optional.ofNullable(usage);
    }

    /** {@code false} when the response is known to be partial; details are in {@link #getWarnings()}. */
    public boolean isComplete() {
        return complete;
    }

    public List<SearchWarning> getWarnings() {
        return warnings;
    }

    public Optional<RawPayload> getRawPayload() {
        return Optional.ofNullable(rawPayload);
    }

    public Optional<ConversationContext> getConversation() {
        return Optional.ofNullable(conversation);
    }

    public Extensions getExtensions() {
        return extensions;
    }

    /** Copy without the raw payload; returns {@code this} when there is none. */
    public SearchMetadata withoutRawPayload() {
        return rawPayload == null ? this : toBuilder().rawPayload(null).build();
    }

    public Builder toBuilder() {
        Builder builder = new Builder(query);
        builder.provider = provider;
        builder.engine = engine;
        builder.vertical = vertical;
        builder.contributingProviders.addAll(contributingProviders);
        builder.locale = locale;
        builder.device = device;
        builder.page = page;
        builder.execution = execution;
        builder.usage = usage;
        builder.complete = complete;
        builder.warnings.addAll(warnings);
        builder.rawPayload = rawPayload;
        builder.conversation = conversation;
        builder.extensions = extensions;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchMetadata)) {
            return false;
        }
        SearchMetadata that = (SearchMetadata) o;
        return Objects.equals(provider, that.provider)
                && Objects.equals(engine, that.engine)
                && Objects.equals(vertical, that.vertical)
                && contributingProviders.equals(that.contributingProviders)
                && query.equals(that.query)
                && Objects.equals(locale, that.locale)
                && device == that.device
                && Objects.equals(page, that.page)
                && Objects.equals(execution, that.execution)
                && Objects.equals(usage, that.usage)
                && complete == that.complete
                && warnings.equals(that.warnings)
                && Objects.equals(rawPayload, that.rawPayload)
                && Objects.equals(conversation, that.conversation)
                && extensions.equals(that.extensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, engine, vertical, contributingProviders, query, locale, device, page,
                execution, usage, complete, warnings, rawPayload, conversation, extensions);
    }

    @Override
    public String toString() {
        return "SearchMetadata{provider=" + provider + ", engine=" + engine + ", vertical=" + vertical
                + ", contributingProviders=" + contributingProviders + ", query=" + query
                + ", locale=" + locale + ", device=" + device
                + ", complete=" + complete + ", warnings=" + warnings.size()
                + ", rawPayload=" + (rawPayload != null) + '}';
    }

    /** Builder for {@link SearchMetadata}; {@link #build()} enforces the provider contract. */
    public static final class Builder {
        private ProviderId provider;
        private SearchEngineId engine;
        private SearchVertical vertical;
        private final List<ProviderId> contributingProviders = new ArrayList<ProviderId>();
        private final QueryInfo query;
        private SearchLocale locale;
        private DeviceType device;
        private PageInfo page;
        private ExecutionInfo execution;
        private ProviderUsage usage;
        private boolean complete = true;
        private final List<SearchWarning> warnings = new ArrayList<SearchWarning>();
        private RawPayload rawPayload;
        private ConversationContext conversation;
        private Extensions extensions = Extensions.empty();

        private Builder(QueryInfo query) {
            this.query = Preconditions.requireNonNull(query, "query");
        }

        /** Marks the response as single-provider response of {@code provider}. */
        public Builder provider(ProviderId provider) {
            this.provider = provider;
            return this;
        }

        public Builder engine(SearchEngineId engine) {
            this.engine = engine;
            return this;
        }

        public Builder vertical(SearchVertical vertical) {
            this.vertical = vertical;
            return this;
        }

        public Builder target(SearchTarget target) {
            Preconditions.requireNonNull(target, "target");
            this.engine = target.getEngine();
            this.vertical = target.getVertical();
            return this;
        }

        /** Replaces the contributing providers; for merged responses in the merger's order. */
        public Builder contributingProviders(Collection<ProviderId> providers) {
            Preconditions.requireNonNull(providers, "providers");
            contributingProviders.clear();
            for (ProviderId id : providers) {
                contributingProviders.add(Preconditions.requireNonNull(id, "provider"));
            }
            return this;
        }

        public Builder contributingProvider(ProviderId provider) {
            contributingProviders.add(Preconditions.requireNonNull(provider, "provider"));
            return this;
        }

        /** The effectively used locale (provider echo, otherwise the request's); {@code null} when unknown. */
        public Builder locale(SearchLocale locale) {
            this.locale = locale;
            return this;
        }

        /** The effectively used device (provider echo, otherwise the request's); {@code null} when unknown. */
        public Builder device(DeviceType device) {
            this.device = device;
            return this;
        }

        public Builder page(PageInfo page) {
            this.page = page;
            return this;
        }

        public Builder execution(ExecutionInfo execution) {
            this.execution = execution;
            return this;
        }

        public Builder usage(ProviderUsage usage) {
            this.usage = usage;
            return this;
        }

        public Builder complete(boolean complete) {
            this.complete = complete;
            return this;
        }

        public Builder warning(SearchWarning warning) {
            warnings.add(Preconditions.requireNonNull(warning, "warning"));
            return this;
        }

        public Builder warnings(Collection<SearchWarning> warnings) {
            for (SearchWarning warning : Preconditions.requireNonNull(warnings, "warnings")) {
                warning(warning);
            }
            return this;
        }

        public Builder rawPayload(RawPayload rawPayload) {
            this.rawPayload = rawPayload;
            return this;
        }

        public Builder conversation(ConversationContext conversation) {
            this.conversation = conversation;
            return this;
        }

        public Builder extensions(Extensions extensions) {
            this.extensions = Preconditions.requireNonNull(extensions, "extensions");
            return this;
        }

        /**
         * @throws IllegalStateException if the provider contract is violated: a single-provider response whose
         *                               contributing providers are not exactly its provider, a merged response
         *                               without contributing providers, or duplicate contributing providers
         */
        public SearchMetadata build() {
            List<ProviderId> contributors;
            if (provider != null) {
                if (contributingProviders.isEmpty()) {
                    contributors = Collections.singletonList(provider);
                } else if (contributingProviders.size() == 1 && contributingProviders.get(0).equals(provider)) {
                    contributors = Collections.singletonList(provider);
                } else {
                    throw new IllegalStateException("a single-provider response of '" + provider
                            + "' must list exactly this provider as contributing provider, but had "
                            + contributingProviders + "; omit the provider for a merged response");
                }
            } else {
                if (contributingProviders.isEmpty()) {
                    throw new IllegalStateException(
                            "a response needs a provider or, when merged, at least one contributing provider");
                }
                if (new LinkedHashSet<ProviderId>(contributingProviders).size() != contributingProviders.size()) {
                    throw new IllegalStateException("contributing providers must be unique but were "
                            + contributingProviders);
                }
                contributors = Collections.unmodifiableList(new ArrayList<ProviderId>(contributingProviders));
            }
            return new SearchMetadata(this, contributors);
        }
    }
}
