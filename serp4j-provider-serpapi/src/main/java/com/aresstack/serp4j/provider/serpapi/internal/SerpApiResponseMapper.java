package com.aresstack.serp4j.provider.serpapi.internal;

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
 * Turns a successful {@code search.json} body into a {@link SearchResponse} via the declarative mapping definitions
 * {@code mapping/serpapi-google-v1.json} and {@code mapping/serpapi-bing-v1.json} (ADR-019: one engine per
 * definition, never shared). Definitions are loaded once per JVM; the engines are immutable and thread-safe.
 */
public final class SerpApiResponseMapper {

    public static final String GOOGLE_DEFINITION_RESOURCE =
            "/com/aresstack/serp4j/provider/serpapi/mapping/serpapi-google-v1.json";
    public static final String BING_DEFINITION_RESOURCE =
            "/com/aresstack/serp4j/provider/serpapi/mapping/serpapi-bing-v1.json";
    private static final MappingDefinition GOOGLE_DEFINITION = MappingDefinitions.from(
            JsonTrees.readResource(SerpApiResponseMapper.class, GOOGLE_DEFINITION_RESOURCE));
    private static final MappingDefinition BING_DEFINITION = MappingDefinitions.from(
            JsonTrees.readResource(SerpApiResponseMapper.class, BING_DEFINITION_RESOURCE));

    private final ProviderId providerId;
    private final MappingEngine googleEngine;
    private final MappingEngine bingEngine;

    public SerpApiResponseMapper(ProviderId providerId) {
        this.providerId = providerId;
        this.googleEngine = MappingEngine.builder(GOOGLE_DEFINITION).provider(providerId).build();
        this.bingEngine = MappingEngine.builder(BING_DEFINITION).provider(providerId).build();
    }

    public static MappingDefinition googleDefinition() {
        return GOOGLE_DEFINITION;
    }

    public static MappingDefinition bingDefinition() {
        return BING_DEFINITION;
    }

    public SearchResponse map(SearchRequest request, SerpApiRequestFactory.Prepared prepared, HttpResponse response,
                              Instant startedAt, Instant finishedAt) {
        StructuredValue tree;
        try {
            tree = JsonTrees.read(response.getBodyAsText());
        } catch (RuntimeException e) {
            throw SearchProviderException.builder(providerId, SearchErrorCategory.RESPONSE_INVALID,
                    "SerpApi returned a body that is not valid JSON").cause(e)
                    .providerStatusCode(response.getStatus()).build();
        }
        if (!tree.isObject()) {
            throw SearchProviderException.builder(providerId, SearchErrorCategory.RESPONSE_INVALID,
                    "SerpApi returned JSON that is not an object").providerStatusCode(response.getStatus()).build();
        }
        Optional<String> error = SerpApiErrors.errorText(tree);
        if (error.isPresent() && !SerpApiErrors.isEmptyResult(error.get())) {
            throw SerpApiErrors.fromResponse(providerId, response);
        }

        SearchEngineId engine = prepared.getEngine();
        MappingResult result = (engine.equals(SearchEngineId.BING) ? bingEngine : googleEngine).map(tree);
        MappedMetadata mapped = result.getMetadata();
        SearchData data = result.getData();

        SearchMetadata.Builder metadata = result.applyTo(SearchMetadata.forProvider(providerId, engine,
                SearchVertical.WEB, queryInfo(request.getQuery(), mapped, engine)));
        PageInfo.Builder page = mapped.applyTo(PageInfo.builder(request.getPaging().getOffset(),
                prepared.getCount()));
        page.hasMore(hasNextPage(tree));
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

    private static boolean hasNextPage(StructuredValue tree) {
        for (String block : new String[] {"serpapi_pagination", "pagination"}) {
            Optional<StructuredValue> pagination = tree.get(block);
            if (pagination.isPresent() && pagination.get().isObject()) {
                Optional<StructuredValue> next = pagination.get().get("next");
                if (next.isPresent() && !next.get().isNull()) {
                    return true;
                }
            }
        }
        return false;
    }

    static QueryInfo queryInfo(String requestQuery, MappedMetadata mapped, SearchEngineId engine) {
        Optional<String> altered = mapped.getEffectiveQuery();
        if (altered.isPresent() && !altered.get().equals(requestQuery)
                && !altered.get().equals(mapped.getOriginalQuery().orElse(null))) {
            return QueryInfo.of(requestQuery, altered.get(), SpellCorrection.of(altered.get(),
                    SpellCorrection.Type.SHOWING_RESULTS_FOR, "serpapi"));
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
