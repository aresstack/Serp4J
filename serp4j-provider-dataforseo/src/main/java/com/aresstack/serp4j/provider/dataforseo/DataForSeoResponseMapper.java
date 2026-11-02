package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.DeviceType;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.data.ResultProvenance;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.data.SerpArea;
import com.aresstack.serp4j.core.model.data.SerpPosition;
import com.aresstack.serp4j.core.model.ext.RawPayload;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.Fact;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.QuestionAnswer;
import com.aresstack.serp4j.core.model.feature.QuestionAnswerFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.core.model.feature.SourceReference;
import com.aresstack.serp4j.core.model.metadata.ExecutionInfo;
import com.aresstack.serp4j.core.model.metadata.PageInfo;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.MappingDefinition;
import com.aresstack.serp4j.mapping.MappingDefinitions;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.mapping.MappingResult;
import com.aresstack.serp4j.mapping.SourceElement;
import com.aresstack.serp4j.mapping.convert.ValueConverters;
import com.aresstack.serp4j.mapping.custom.CustomMapperRegistry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks DataForSEO status codes and maps the response envelope to a {@link SearchResponse} (API reference §8, §10). */
final class DataForSeoResponseMapper {

    /** Classpath resource of the mapping definition (ADR-019). */
    static final String DEFINITION_RESOURCE = "dataforseo-serp-organic.json";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX", Locale.ROOT);
    private static final Pattern SECONDS = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)");

    private final ProviderId providerId;
    private final MappingEngine engine;

    DataForSeoResponseMapper(ProviderId providerId) {
        this.providerId = providerId;
        this.engine = createEngine(providerId);
    }

    /** The mapping definition shipped with this module. */
    static MappingDefinition definition() {
        return MappingDefinitions.from(JsonTrees.readResource(DataForSeoResponseMapper.class, DEFINITION_RESOURCE));
    }

    /** Engine with the module's definition, its converter and custom mappers; package-visible for contract tests. */
    static MappingEngine createEngine(ProviderId providerId) {
        MappingDefinition definition = definition();
        return MappingEngine.builder(definition)
                .converters(ValueConverters.standard().toBuilder()
                        .register("dataforseoTimestamp", v -> parseTimestamp(v.asText().orElse(null)))
                        .build())
                .customMappers(customMappers(definition))
                .provider(providerId)
                .build();
    }

    SearchResponse map(HttpResponse http, SearchRequest request, SearchEngineId searchEngine, Instant startedAt,
                       Instant finishedAt, List<SearchWarning> requestWarnings) {
        StructuredValue root = parse(http);
        int status = statusCode(root).orElse(-1);
        if (status < 0) {
            if (!http.isSuccessful()) {
                throw httpFailure(http.getStatus());
            }
            throw failure(SearchErrorCategory.RESPONSE_INVALID, "response has no status_code", -1);
        }
        if (!DataForSeoStatus.isSuccess(status)) {
            throw failure(DataForSeoStatus.categoryOf(status), message(root, status), status);
        }
        Optional<StructuredValue> task = root.path("tasks").flatMap(t -> t.get(0));
        if (!task.isPresent()) {
            throw failure(SearchErrorCategory.RESPONSE_INVALID, "response contains no task", status);
        }
        int taskStatus = statusCode(task.get()).orElse(status);
        if (!DataForSeoStatus.isSuccess(taskStatus)) {
            throw failure(DataForSeoStatus.categoryOf(taskStatus), message(task.get(), taskStatus), taskStatus);
        }

        MappingResult mapped = engine.map(root);
        SearchData data = postProcess(mapped.getData(), request, searchEngine);

        String effective = task.get().path("result").flatMap(r -> r.get(0)).flatMap(r -> r.path("spell", "keyword"))
                .flatMap(StructuredValue::asText).orElse(null);
        SearchMetadata.Builder metadata = SearchMetadata.forProvider(providerId, searchEngine, SearchVertical.WEB,
                        effective == null ? QueryInfo.of(request.getQuery()) : QueryInfo.of(request.getQuery(), effective))
                .target(SearchTarget.web(searchEngine));
        mapped.applyTo(metadata);
        metadata.page(mapped.getMetadata().applyTo(
                PageInfo.builder(request.getPaging().getOffset(), request.getPaging().getLimit())).build());
        if (request.getLocale().isPresent()) {
            metadata.locale(request.getLocale().get());
        }
        if (request.getDevice().isPresent()) {
            // DataForSEO knows desktop and mobile only; a tablet request is searched (and labelled) as mobile.
            metadata.device(request.getDevice().get() == DeviceType.DESKTOP ? DeviceType.DESKTOP : DeviceType.MOBILE);
        }
        metadata.warnings(requestWarnings);
        ExecutionInfo.Builder execution = mapped.getMetadata().toExecutionInfo()
                .map(ExecutionInfo::toBuilder).orElse(ExecutionInfo.builder());
        execution.startedAt(startedAt).duration(Duration.between(startedAt, finishedAt)).attempts(1);
        Optional<Duration> reported = task.get().get("time").flatMap(StructuredValue::asText)
                .flatMap(DataForSeoResponseMapper::parseSeconds);
        if (reported.isPresent()) {
            execution.providerReportedDuration(reported.get());
        }
        Optional<String> datetime = task.get().path("result").flatMap(r -> r.get(0))
                .flatMap(r -> r.get("datetime")).flatMap(StructuredValue::asText);
        if (datetime.isPresent()) {
            Instant at = parseTimestamp(datetime.get());
            if (at != null) {
                execution.providerTimestamp(at);
            }
        }
        Optional<String> checkUrl = task.get().path("result").flatMap(r -> r.get(0))
                .flatMap(r -> r.get("check_url")).flatMap(StructuredValue::asText);
        if (checkUrl.isPresent()) {
            execution.sourceUrl(checkUrl.get());
        }
        metadata.execution(execution.build());

        if (taskStatus == DataForSeoStatus.PARTIAL_RESULTS) {
            metadata.complete(false).warning(SearchWarning.of(SearchWarning.PARTIAL_RESULTS,
                    "DataForSEO reported partial results (40106)", providerId));
        }
        boolean problem = taskStatus == DataForSeoStatus.PARTIAL_RESULTS || !mapped.getWarnings().isEmpty();
        if (request.getRawPayloadPolicy() == RawPayloadPolicy.ALWAYS
                || (request.getRawPayloadPolicy() == RawPayloadPolicy.ON_ERROR && problem)) {
            metadata.rawPayload(rawPayload(http));
        }
        return SearchResponse.of(data, metadata.build());
    }

    /** Failure for an HTTP error status, using the body's DataForSEO status when there is one. */
    SearchProviderException errorFor(HttpResponse http) {
        try {
            StructuredValue root = JsonTrees.read(http.getBodyAsText());
            OptionalInt status = statusCode(root);
            if (status.isPresent() && !DataForSeoStatus.isSuccess(status.getAsInt())) {
                return failure(DataForSeoStatus.categoryOf(status.getAsInt()), message(root, status.getAsInt()),
                        status.getAsInt());
            }
        } catch (RuntimeException ignored) {
            // no JSON body: fall back to the HTTP status
        }
        return httpFailure(http.getStatus());
    }

    private SearchData postProcess(SearchData data, SearchRequest request, SearchEngineId searchEngine) {
        int offset = request.getPaging().getOffset();
        int limit = request.getPaging().getLimit();
        SearchData.Builder out = SearchData.builder();
        int organic = 0;
        for (SearchResultItem item : data.getResults()) {
            if (ResultType.ORGANIC.equals(item.getType())) {
                int rank = item.getPosition().flatMap(p -> p.getRankInGroup().isPresent()
                        ? Optional.of(p.getRankInGroup().getAsInt()) : Optional.<Integer>empty()).orElse(organic + 1);
                if (rank <= offset || organic >= limit) {
                    continue;
                }
                organic++;
            }
            ResultProvenance.Builder provenance = ResultProvenance.builder(providerId, searchEngine)
                    .originalId(item.getId());
            if (item.getPosition().isPresent()) {
                provenance.originalPosition(item.getPosition().get());
            }
            out.result(item.toBuilder().provenance(provenance.build()).build());
        }
        out.features(data.getFeatures());
        return out.build();
    }

    private static CustomMapperRegistry customMappers(MappingDefinition definition) {
        return CustomMapperRegistry.builder(definition)
                // "position": "right" is the sidebar (paid ads, knowledge graph on desktop).
                .itemMapper("serp", (element, item, ctx) -> {
                    Optional<String> position = element.text("position");
                    if (!position.isPresent()) {
                        return item;
                    }
                    element.consume("position");
                    SerpPosition.Builder pos = item.getPosition().map(SerpPosition::toBuilder)
                            .orElse(SerpPosition.builder());
                    pos.area("right".equals(position.get()) ? SerpArea.SIDEBAR : SerpArea.MAIN);
                    return item.toBuilder().position(pos.build()).build();
                })
                .featureMapper("serp", SerpFeatureType.KNOWLEDGE_PANEL, (element, seed, ctx) -> {
                    Optional<String> title = element.text("title");
                    if (!title.isPresent()) {
                        return null;
                    }
                    KnowledgePanelFeature.Builder b = seed.applyTo(KnowledgePanelFeature.builder(seed.getId(), title.get()));
                    element.consume("title");
                    element.text("sub_title").ifPresent(s -> {
                        b.subtitle(s);
                        element.consume("sub_title");
                    });
                    element.text("description").ifPresent(s -> {
                        b.description(s);
                        element.consume("description");
                    });
                    for (SourceElement row : element.elements("items[*]")) {
                        String type = row.text("type").orElse("");
                        if ("knowledge_graph_row_item".equals(type) || "knowledge_graph_description_item".equals(type)) {
                            Optional<String> text = row.text("text");
                            if (text.isPresent()) {
                                String key = row.text("title").orElse(type);
                                row.consume("type").consume("text").consume("title");
                                b.fact(Fact.of(key, text.get()));
                            }
                        }
                    }
                    element.consume("type");
                    return b.build();
                })
                .featureMapper("serp", SerpFeatureType.PEOPLE_ALSO_ASK, (element, seed, ctx) -> {
                    QuestionAnswerFeature.Builder b = seed.applyTo(
                            QuestionAnswerFeature.builder(seed.getId(), SerpFeatureType.PEOPLE_ALSO_ASK));
                    for (SourceElement q : element.elements("items[*]")) {
                        String question = q.text("title").orElse(null);
                        if (question == null) {
                            continue;
                        }
                        q.consume("type").consume("title");
                        String answer = q.text("expanded_element[0].description").orElse(null);
                        Optional<String> url = q.text("expanded_element[0].url");
                        b.question(url.isPresent()
                                ? QuestionAnswer.of(question, answer, SourceReference.of(url.get()))
                                : QuestionAnswer.of(question, answer));
                    }
                    element.consume("type");
                    return b.build();
                })
                .build();
    }

    private StructuredValue parse(HttpResponse http) {
        String text = http.getBodyAsText();
        try {
            return JsonTrees.read(text);
        } catch (RuntimeException e) {
            if (!http.isSuccessful()) {
                throw httpFailure(http.getStatus());
            }
            throw SearchProviderException.builder(providerId, SearchErrorCategory.RESPONSE_INVALID,
                    "response is not valid JSON").cause(e).build();
        }
    }

    private static OptionalInt statusCode(StructuredValue node) {
        Optional<StructuredValue> code = node.get("status_code");
        if (!code.isPresent() || !code.get().asLong().isPresent()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) code.get().asLong().getAsLong());
    }

    private static String message(StructuredValue node, int status) {
        return "DataForSEO status " + status + ": "
                + node.get("status_message").flatMap(StructuredValue::asText).orElse("(no message)");
    }

    private SearchProviderException failure(SearchErrorCategory category, String message, int status) {
        SearchProviderException.Builder b = SearchProviderException.builder(providerId, category, message);
        if (status > 0) {
            b.providerStatusCode(status).retryable(DataForSeoStatus.isRetryable(status));
        }
        return b.build();
    }

    private SearchProviderException httpFailure(int httpStatus) {
        return SearchProviderException.builder(providerId, DataForSeoStatus.categoryOfHttp(httpStatus),
                "DataForSEO answered HTTP " + httpStatus).detail("httpStatus", String.valueOf(httpStatus)).build();
    }

    private static RawPayload rawPayload(HttpResponse http) {
        RawPayload.Builder raw = RawPayload.builder(http.getContentType().orElse("application/json"), http.getBody())
                .httpStatus(http.getStatus());
        for (String name : http.getHeaders().getNames()) {
            for (String value : http.getHeaders().getAll(name)) {
                raw.header(name, value);
            }
        }
        return raw.build();
    }

    static Instant parseTimestamp(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String s = text.trim();
        try {
            return OffsetDateTime.parse(s, TIMESTAMP).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(s).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    static Optional<Duration> parseSeconds(String text) {
        Matcher m = SECONDS.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }
        BigDecimal seconds = new BigDecimal(m.group(1));
        return Optional.of(Duration.ofNanos(seconds.movePointRight(9).longValue()));
    }
}
