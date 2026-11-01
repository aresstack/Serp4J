package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.metadata.ExecutionInfo;
import com.aresstack.serp4j.core.model.metadata.ProviderUsage;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of mapping one provider response: the {@link SearchData}, the {@link MappedMetadata} fragments and the
 * warnings the mapping produced (unknown element types, skipped elements, values that could not be converted).
 * Immutable.
 */
public final class MappingResult {

    private final String definitionId;
    private final int definitionVersion;
    private final SearchData data;
    private final MappedMetadata metadata;
    private final List<SearchWarning> warnings;

    MappingResult(MappingDefinition definition, SearchData data, MappedMetadata metadata, List<SearchWarning> warnings) {
        this.definitionId = definition.getId();
        this.definitionVersion = definition.getVersion();
        this.data = data;
        this.metadata = metadata;
        this.warnings = warnings;
    }

    /** Id of the definition that produced this result. */
    public String getDefinitionId() {
        return definitionId;
    }

    public int getDefinitionVersion() {
        return definitionVersion;
    }

    public SearchData getData() {
        return data;
    }

    public MappedMetadata getMetadata() {
        return metadata;
    }

    public List<SearchWarning> getWarnings() {
        return warnings;
    }

    /**
     * Transfers what the response carried onto a metadata builder the provider created (with provider, engine,
     * vertical and query): execution info, usage, warnings and extensions (the builder's extensions are
     * replaced; merge beforehand with {@code getMetadata().getExtensions().merge(…)} if needed). Paging is left to
     * the provider, see {@link MappedMetadata#applyTo(com.aresstack.serp4j.core.model.metadata.PageInfo.Builder)}.
     */
    public SearchMetadata.Builder applyTo(SearchMetadata.Builder builder) {
        Objects.requireNonNull(builder, "builder");
        Optional<ExecutionInfo> execution = metadata.toExecutionInfo();
        if (execution.isPresent()) {
            builder.execution(execution.get());
        }
        Optional<ProviderUsage> usage = metadata.toUsage();
        if (usage.isPresent()) {
            builder.usage(usage.get());
        }
        builder.warnings(warnings);
        if (!metadata.getExtensions().isEmpty()) {
            builder.extensions(metadata.getExtensions());
        }
        return builder;
    }

    @Override
    public String toString() {
        return "MappingResult{" + definitionId + " v" + definitionVersion + ", results=" + data.getResults().size()
                + ", features=" + data.getFeatures().size() + ", warnings=" + warnings.size() + '}';
    }
}
