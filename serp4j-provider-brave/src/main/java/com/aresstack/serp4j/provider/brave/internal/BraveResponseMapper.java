package com.aresstack.serp4j.provider.brave.internal;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.ext.RawPayload;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.model.metadata.ExecutionInfo;
import com.aresstack.serp4j.core.model.metadata.PageInfo;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.model.metadata.SpellCorrection;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.MappedMetadata;
import com.aresstack.serp4j.mapping.MappingDefinition;
import com.aresstack.serp4j.mapping.MappingDefinitions;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.mapping.MappingResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a successful {@code web/search} body into a {@link SearchResponse} via the declarative mapping definition
 * {@code mapping/brave-web-v1.json} (ADR-019). The definition is loaded once per JVM; the engine is immutable and
 * thread-safe.
 */
public final class BraveResponseMapper {

    public static final String DEFINITION_RESOURCE = "/com/aresstack/serp4j/provider/brave/mapping/brave-web-v1.json";
    private static final MappingDefinition WEB_DEFINITION = MappingDefinitions.from(
            JsonTrees.readResource(BraveResponseMapper.class, DEFINITION_RESOURCE));

    private final ProviderId providerId;
    private final MappingEngine engine;

    public BraveResponseMapper(ProviderId providerId) {
        this.providerId = providerId;
        this.engine = MappingEngine.builder(WEB_DEFINITION).provider(providerId).build();
    }

    public static MappingDefinition webDefinition() {
        return WEB_DEFINITION;
    }

    public SearchResponse map(SearchRequest request, BraveRequestFactory.Prepared prepared, HttpResponse response,
                              Instant startedAt, Instant finishedAt) {
        StructuredValue tree;
        try {
            tree = JsonTrees.read(response.getBodyAsText());
        } catch (RuntimeException e) {
            throw SearchProviderException.builder(providerId, SearchErrorCategory.RESPONSE_INVALID,
                    "Brave returned a body that is not valid JSON").cause(e)
                    .providerStatusCode(response.getStatus()).build();
        }
        if (!tree.isObject()) {
            throw SearchProviderException.builder(providerId, SearchErrorCategory.RESPONSE_INVALID,
                    "Brave returned JSON that is not an object").providerStatusCode(response.getStatus()).build();
        }
        Optional<String> type = tree.get("type").flatMap(StructuredValue::asText);
        if (type.isPresent() && type.get().equals("ErrorResponse")) {
            throw BraveErrors.fromResponse(providerId, response);
        }

        MappingResult result = engine.map(tree);
        MappedMetadata mapped = result.getMetadata();
        SearchData data = result.getData();

        SearchMetadata.Builder metadata = result.applyTo(SearchMetadata.forProvider(providerId,
                SearchEngineId.BRAVE, SearchVertical.WEB, queryInfo(request.getQuery(), mapped)));
        PageInfo.Builder page = PageInfo.builder(request.getPaging().getOffset(), prepared.getCount())
                .pageNumber(prepared.getPageIndex() + 1);
        if (mapped.getHasMore().isPresent()) {
            page.hasMore(mapped.getHasMore().get());
        }
        metadata.page(page.build())
                .execution(ExecutionInfo.builder()
                        .startedAt(startedAt)
                        .duration(finishedAt.isBefore(startedAt) ? Duration.ZERO
                                : Duration.between(startedAt, finishedAt))
                        .sourceUrl(prepared.getHttpRequest().getRedactedUrl())
                        .attempts(1)
                        .build());
        if (!prepared.getEffectiveLocale().isEmpty()) {
            metadata.locale(prepared.getEffectiveLocale());
        }
        for (SearchWarning warning : prepared.getWarnings()) {
            metadata.warning(warning);
        }
        RawPayloadPolicy policy = request.getRawPayloadPolicy();
        if (policy == RawPayloadPolicy.ALWAYS
                || (policy == RawPayloadPolicy.ON_ERROR && !result.getWarnings().isEmpty())) {
            metadata.rawPayload(rawPayload(response));
        }
        return SearchResponse.of(data, metadata.build());
    }

    static QueryInfo queryInfo(String requestQuery, MappedMetadata mapped) {
        Optional<String> altered = mapped.getEffectiveQuery();
        if (altered.isPresent() && !altered.get().equals(requestQuery)
                && !altered.get().equals(mapped.getOriginalQuery().orElse(null))) {
            return QueryInfo.of(requestQuery, altered.get(), SpellCorrection.of(altered.get(),
                    SpellCorrection.Type.SHOWING_RESULTS_FOR, "brave"));
        }
        return QueryInfo.of(requestQuery);
    }

    static RawPayload rawPayload(HttpResponse response) {
        RawPayload.Builder raw = RawPayload.builder(response.getContentType().orElse("application/json"),
                response.getBody()).httpStatus(response.getStatus());
        for (Map.Entry<String, List<String>> header : response.getHeaders().asMap().entrySet()) {
            if (response.getHeaders().isSensitiveHeader(header.getKey())) {
                raw.sensitiveHeader(header.getKey());
            }
            for (String value : header.getValue()) {
                raw.header(header.getKey(), value);
            }
        }
        return raw.build();
    }

}
