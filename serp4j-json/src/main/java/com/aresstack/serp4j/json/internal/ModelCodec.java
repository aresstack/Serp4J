package com.aresstack.serp4j.json.internal;

import com.aresstack.serp4j.core.model.DeviceType;
import com.aresstack.serp4j.core.model.GeoLocation;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.analysis.AnalysisProvenance;
import com.aresstack.serp4j.core.model.analysis.EntityMention;
import com.aresstack.serp4j.core.model.analysis.IntentCategory;
import com.aresstack.serp4j.core.model.analysis.MetricKey;
import com.aresstack.serp4j.core.model.analysis.SearchAnalysis;
import com.aresstack.serp4j.core.model.analysis.SearchIntent;
import com.aresstack.serp4j.core.model.analysis.SearchMetrics;
import com.aresstack.serp4j.core.model.analysis.Topic;
import com.aresstack.serp4j.core.model.data.MediaInfo;
import com.aresstack.serp4j.core.model.data.PublishedDate;
import com.aresstack.serp4j.core.model.data.Rating;
import com.aresstack.serp4j.core.model.data.ResultProvenance;
import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchData;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.data.SerpArea;
import com.aresstack.serp4j.core.model.data.SerpPosition;
import com.aresstack.serp4j.core.model.data.SiteLink;
import com.aresstack.serp4j.core.model.data.TextRange;
import com.aresstack.serp4j.core.model.ext.ExtensionKey;
import com.aresstack.serp4j.core.model.ext.Extensions;
import com.aresstack.serp4j.core.model.ext.RawPayload;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.AbstractSerpFeature;
import com.aresstack.serp4j.core.model.feature.AnswerBoxFeature;
import com.aresstack.serp4j.core.model.feature.AnswerSegment;
import com.aresstack.serp4j.core.model.feature.Fact;
import com.aresstack.serp4j.core.model.feature.FeaturedSnippetFeature;
import com.aresstack.serp4j.core.model.feature.GeneratedAnswerFeature;
import com.aresstack.serp4j.core.model.feature.GenericSerpFeature;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.QuestionAnswer;
import com.aresstack.serp4j.core.model.feature.QuestionAnswerFeature;
import com.aresstack.serp4j.core.model.feature.RelatedQueriesFeature;
import com.aresstack.serp4j.core.model.feature.RelatedQuery;
import com.aresstack.serp4j.core.model.feature.ResultGroupFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.core.model.feature.SourceReference;
import com.aresstack.serp4j.core.model.id.ElementId;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchSessionId;
import com.aresstack.serp4j.core.model.id.SearchTarget;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.model.metadata.ConversationContext;
import com.aresstack.serp4j.core.model.metadata.ExecutionInfo;
import com.aresstack.serp4j.core.model.metadata.MonetaryAmount;
import com.aresstack.serp4j.core.model.metadata.PageInfo;
import com.aresstack.serp4j.core.model.metadata.ProviderUsage;
import com.aresstack.serp4j.core.model.metadata.QueryInfo;
import com.aresstack.serp4j.core.model.metadata.RateLimitInfo;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.core.model.metadata.SpellCorrection;
import com.aresstack.serp4j.core.port.capability.ResponseFeature;
import com.aresstack.serp4j.core.port.conversation.ConversationTurn;
import com.aresstack.serp4j.core.port.conversation.SearchConversation;
import com.aresstack.serp4j.core.port.conversation.SearchConversationRequest;
import com.aresstack.serp4j.core.port.conversation.SessionHandle;
import com.aresstack.serp4j.core.port.conversation.SessionState;
import com.aresstack.serp4j.json.Serp4jJsonException;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON format of the domain model, format version 1 (ADR-013). Writes the model into a {@link StructuredValue}
 * tree and reads it back; text conversion is done by {@code JsonTrees}. Stateless apart from the raw-payload switch
 * and thread-safe.
 *
 * <p>Format rules: member names follow the getter names; absent optional values and empty lists are left out;
 * instants and durations are ISO-8601 strings; open value types (provider, engine, result type, …) are written as
 * their code; enums by name; features carry a {@code kind} discriminator naming the Java feature class.</p>
 */
public final class ModelCodec {

    // feature kind discriminators (format v1)
    private static final String KIND_GENERATED_ANSWER = "generated_answer";
    private static final String KIND_KNOWLEDGE_PANEL = "knowledge_panel";
    private static final String KIND_FEATURED_SNIPPET = "featured_snippet";
    private static final String KIND_ANSWER_BOX = "answer_box";
    private static final String KIND_QUESTION_ANSWER = "question_answer";
    private static final String KIND_RELATED_QUERIES = "related_queries";
    private static final String KIND_RESULT_GROUP = "result_group";
    private static final String KIND_GENERIC = "generic";

    // typed extension value types (format v1)
    private static final Map<Class<?>, String> EXTENSION_TYPES;
    private static final Map<String, Class<?>> EXTENSION_CLASSES;

    static {
        Map<Class<?>, String> types = new LinkedHashMap<Class<?>, String>();
        types.put(String.class, "string");
        types.put(Boolean.class, "boolean");
        types.put(Integer.class, "int");
        types.put(Long.class, "long");
        types.put(Double.class, "double");
        types.put(BigDecimal.class, "decimal");
        types.put(StructuredValue.class, "structured");
        EXTENSION_TYPES = Collections.unmodifiableMap(types);
        Map<String, Class<?>> classes = new LinkedHashMap<String, Class<?>>();
        for (Map.Entry<Class<?>, String> entry : types.entrySet()) {
            classes.put(entry.getValue(), entry.getKey());
        }
        EXTENSION_CLASSES = Collections.unmodifiableMap(classes);
    }

    private final boolean includeRawPayload;

    public ModelCodec(boolean includeRawPayload) {
        this.includeRawPayload = includeRawPayload;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Envelope

    /** Writes a top-level document: {@code formatVersion}, {@code type}, then the members of {@code body}. */
    public static StructuredValue envelope(int formatVersion, String type, StructuredValue body) {
        StructuredValue.ObjectBuilder builder = StructuredValue.objectBuilder()
                .put("formatVersion", formatVersion)
                .put("type", type);
        for (Map.Entry<String, StructuredValue> member : body.getMembers().entrySet()) {
            builder.put(member.getKey(), member.getValue());
        }
        return builder.build();
    }

    /** Checks the {@code type} member of a top-level document whose version was already validated. */
    public static void requireType(StructuredValue document, String expectedType) {
        In root = In.root(document);
        String type = root.text("type");
        if (!expectedType.equals(type)) {
            throw new Serp4jJsonException("$.type: expected '" + expectedType + "' but found '" + type + "'");
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // SessionHandle

    public StructuredValue writeSessionHandle(SessionHandle handle) {
        return Out.object()
                .put("providerId", handle.getProviderId().getCode())
                .put("sessionId", handle.getSessionId().getValue())
                .put("opaqueToken", handle.getOpaqueToken())
                .build();
    }

    public SessionHandle readSessionHandle(StructuredValue document) {
        In in = In.root(document);
        return SessionHandle.of(ProviderId.of(in.text("providerId")), SearchSessionId.of(in.text("sessionId")),
                in.text("opaqueToken"));
    }

    // ------------------------------------------------------------------------------------------------------------
    // SearchConversation

    public StructuredValue writeConversation(SearchConversation conversation) {
        List<StructuredValue> turns = new ArrayList<StructuredValue>();
        for (ConversationTurn turn : conversation.getTurns()) {
            turns.add(writeTurn(turn));
        }
        return Out.object()
                .put("sessionId", conversation.getSessionId().getValue())
                .put("providerId", conversation.getProviderId().getCode())
                .put("target", writeTarget(conversation.getTarget()))
                .put("state", conversation.getState().name())
                .putArray("turns", turns)
                .build();
    }

    public SearchConversation readConversation(StructuredValue document) {
        In in = In.root(document);
        SearchConversation.Builder builder = SearchConversation.builder(SearchSessionId.of(in.text("sessionId")),
                ProviderId.of(in.text("providerId")), readTarget(in.obj("target")),
                in.enumValue("state", SessionState.class));
        for (In turn : in.objects("turns")) {
            builder.turn(readTurn(turn));
        }
        return builder.build();
    }

    private StructuredValue writeTurn(ConversationTurn turn) {
        SearchConversationRequest request = turn.getRequest();
        List<String> features = new ArrayList<String>();
        for (ResponseFeature feature : request.getRequestedFeatures()) {
            features.add(feature.name());
        }
        // Provider request options are runtime configuration of a provider-specific type and are not part of the
        // format (ADR-013, decision 3).
        Out requestOut = Out.object()
                .put("message", request.getMessage())
                .put("expectedTurnIndex", request.getExpectedTurnIndex())
                .putTexts("requestedFeatures", features);
        return Out.object()
                .put("index", turn.getIndex())
                .put("request", requestOut)
                .put("response", writeResponse(turn.getResponse()))
                .put("startedAt", turn.getStartedAt())
                .put("completedAt", turn.getCompletedAt())
                .build();
    }

    private ConversationTurn readTurn(In in) {
        In requestIn = in.obj("request");
        SearchConversationRequest.Builder request = SearchConversationRequest.builder(requestIn.text("message"));
        Integer expected = requestIn.optInteger("expectedTurnIndex");
        if (expected != null) {
            request.expectedTurnIndex(expected);
        }
        for (String feature : requestIn.texts("requestedFeatures")) {
            try {
                request.requestedFeature(ResponseFeature.valueOf(feature));
            } catch (IllegalArgumentException e) {
                throw requestIn.fail("requestedFeatures", "unknown ResponseFeature '" + feature + "'");
            }
        }
        return ConversationTurn.of(in.integer("index"), request.build(), readResponse(in.obj("response")),
                in.instant("startedAt"), in.instant("completedAt"));
    }

    // ------------------------------------------------------------------------------------------------------------
    // SearchResponse

    public StructuredValue writeResponse(SearchResponse response) {
        Out out = Out.object().put("data", writeData(response.getData()));
        if (response.getMetadata().isPresent()) {
            out.put("metadata", writeMetadata(response.getMetadata().get()));
        }
        if (response.getAnalysis().isPresent()) {
            out.put("analysis", writeAnalysis(response.getAnalysis().get()));
        }
        return out.build();
    }

    public SearchResponse readResponse(StructuredValue document) {
        return readResponse(In.root(document));
    }

    private SearchResponse readResponse(In in) {
        SearchResponse.Builder builder = SearchResponse.builder(readData(in.obj("data")));
        In metadata = in.optObj("metadata");
        if (metadata != null) {
            builder.metadata(readMetadata(metadata));
        }
        In analysis = in.optObj("analysis");
        if (analysis != null) {
            builder.analysis(readAnalysis(analysis));
        }
        return builder.build();
    }

    // ------------------------------------------------------------------------------------------------------------
    // SearchData

    private Out writeData(SearchData data) {
        List<StructuredValue> results = new ArrayList<StructuredValue>();
        for (SearchResultItem item : data.getResults()) {
            results.add(writeItem(item));
        }
        List<StructuredValue> features = new ArrayList<StructuredValue>();
        for (SerpFeature feature : data.getFeatures()) {
            features.add(writeFeature(feature));
        }
        return Out.object().putArray("results", results).putArray("features", features);
    }

    private SearchData readData(In in) {
        SearchData.Builder builder = SearchData.builder();
        for (In item : in.objects("results")) {
            builder.result(readItem(item));
        }
        for (In feature : in.objects("features")) {
            builder.feature(readFeature(feature));
        }
        return builder.build();
    }

    private StructuredValue writeItem(SearchResultItem item) {
        List<StructuredValue> highlights = new ArrayList<StructuredValue>();
        for (TextRange range : item.getHighlights()) {
            highlights.add(Out.object().put("start", range.getStart()).put("end", range.getEnd()).build());
        }
        List<StructuredValue> siteLinks = new ArrayList<StructuredValue>();
        for (SiteLink link : item.getSiteLinks()) {
            siteLinks.add(writeSiteLink(link));
        }
        Out out = Out.object()
                .put("id", item.getId().getValue())
                .put("type", item.getType().getCode())
                .put("url", item.getUrl())
                .put("title", item.getTitle())
                .put("snippet", item.getSnippet())
                .putTexts("additionalSnippets", item.getAdditionalSnippets())
                .putArray("highlights", highlights)
                .put("displayUrl", item.getDisplayUrl())
                .put("domain", item.getDomain())
                .put("siteName", item.getSiteName())
                .put("language", item.getLanguage())
                .putArray("siteLinks", siteLinks)
                .putArray("provenance", writeProvenance(item.getProvenance()))
                .put("extensions", writeExtensions(item.getExtensions()));
        if (item.getPublishedDate().isPresent()) {
            PublishedDate date = item.getPublishedDate().get();
            out.put("publishedDate", Out.object().put("instant", date.getInstant())
                    .put("displayText", date.getDisplayText()));
        }
        if (item.getThumbnail().isPresent()) {
            out.put("thumbnail", writeMedia(item.getThumbnail().get()));
        }
        if (item.getRating().isPresent()) {
            Rating rating = item.getRating().get();
            out.put("rating", Out.object().put("value", rating.getValue()).put("max", rating.getMax())
                    .put("votes", rating.getVotes()));
        }
        if (item.getPosition().isPresent()) {
            out.put("position", writePosition(item.getPosition().get()));
        }
        return out.build();
    }

    private SearchResultItem readItem(In in) {
        SearchResultItem.Builder builder = SearchResultItem.builder(ElementId.of(in.text("id")),
                ResultType.of(in.text("type")), in.text("url"))
                .title(in.optText("title"))
                .snippet(in.optText("snippet"))
                .additionalSnippets(in.texts("additionalSnippets"))
                .displayUrl(in.optText("displayUrl"))
                .domain(in.optText("domain"))
                .siteName(in.optText("siteName"))
                .language(in.optText("language"));
        for (In range : in.objects("highlights")) {
            builder.highlight(TextRange.of(range.integer("start"), range.integer("end")));
        }
        for (In link : in.objects("siteLinks")) {
            builder.siteLink(readSiteLink(link));
        }
        In date = in.optObj("publishedDate");
        if (date != null) {
            builder.publishedDate(readPublishedDate(date));
        }
        In thumbnail = in.optObj("thumbnail");
        if (thumbnail != null) {
            builder.thumbnail(readMedia(thumbnail));
        }
        In rating = in.optObj("rating");
        if (rating != null) {
            builder.rating(readRating(rating));
        }
        In position = in.optObj("position");
        if (position != null) {
            builder.position(readPosition(position));
        }
        builder.provenance(readProvenance(in));
        In extensions = in.optObj("extensions");
        if (extensions != null) {
            builder.extensions(readExtensions(extensions));
        }
        return builder.build();
    }

    private static PublishedDate readPublishedDate(In in) {
        java.time.Instant instant = in.optInstant("instant");
        String text = in.optText("displayText");
        if (instant != null && text != null) {
            return PublishedDate.of(instant, text);
        }
        if (instant != null) {
            return PublishedDate.of(instant);
        }
        if (text != null) {
            return PublishedDate.ofText(text);
        }
        throw new Serp4jJsonException(in.path() + ": a published date needs 'instant' or 'displayText'");
    }

    private static Rating readRating(In in) {
        double value = in.dbl("value");
        Double max = in.optDouble("max");
        Long votes = in.optLong("votes");
        if (votes != null) {
            if (max == null) {
                throw in.fail("max", "required when 'votes' is present");
            }
            return Rating.of(value, max, votes);
        }
        return max == null ? Rating.of(value) : Rating.of(value, max);
    }

    private static Out writeMedia(MediaInfo media) {
        return Out.object().put("url", media.getUrl()).put("width", media.getWidth()).put("height", media.getHeight());
    }

    private static MediaInfo readMedia(In in) {
        Integer width = in.optInteger("width");
        Integer height = in.optInteger("height");
        if (width == null && height == null) {
            return MediaInfo.of(in.text("url"));
        }
        if (width == null || height == null) {
            throw new Serp4jJsonException(in.path() + ": 'width' and 'height' must be present together");
        }
        return MediaInfo.of(in.text("url"), width, height);
    }

    private static StructuredValue writeSiteLink(SiteLink link) {
        return Out.object().put("url", link.getUrl()).put("title", link.getTitle())
                .put("description", link.getDescription()).build();
    }

    private static SiteLink readSiteLink(In in) {
        return SiteLink.of(in.text("url"), in.optText("title"), in.optText("description"));
    }

    private static Out writePosition(SerpPosition position) {
        return Out.object()
                .put("rankInGroup", position.getRankInGroup())
                .put("rankAbsolute", position.getRankAbsolute())
                .put("page", position.getPage())
                .put("area", position.getArea().name());
    }

    private static SerpPosition readPosition(In in) {
        SerpPosition.Builder builder = SerpPosition.builder();
        Integer rankInGroup = in.optInteger("rankInGroup");
        if (rankInGroup != null) {
            builder.rankInGroup(rankInGroup);
        }
        Integer rankAbsolute = in.optInteger("rankAbsolute");
        if (rankAbsolute != null) {
            builder.rankAbsolute(rankAbsolute);
        }
        Integer page = in.optInteger("page");
        if (page != null) {
            builder.page(page);
        }
        SerpArea area = in.optEnum("area", SerpArea.class);
        if (area != null) {
            builder.area(area);
        }
        return builder.build();
    }

    private static List<StructuredValue> writeProvenance(List<ResultProvenance> provenance) {
        List<StructuredValue> result = new ArrayList<StructuredValue>();
        for (ResultProvenance origin : provenance) {
            Out out = Out.object()
                    .put("provider", origin.getProvider().getCode())
                    .put("engine", origin.getEngine().getCode());
            if (origin.getOriginalId().isPresent()) {
                out.put("originalId", origin.getOriginalId().get().getValue());
            }
            if (origin.getOriginalPosition().isPresent()) {
                out.put("originalPosition", writePosition(origin.getOriginalPosition().get()));
            }
            result.add(out.build());
        }
        return result;
    }

    private static List<ResultProvenance> readProvenance(In parent) {
        List<ResultProvenance> result = new ArrayList<ResultProvenance>();
        for (In in : parent.objects("provenance")) {
            ResultProvenance.Builder builder = ResultProvenance.builder(ProviderId.of(in.text("provider")),
                    SearchEngineId.of(in.text("engine")));
            String originalId = in.optText("originalId");
            if (originalId != null) {
                builder.originalId(ElementId.of(originalId));
            }
            In position = in.optObj("originalPosition");
            if (position != null) {
                builder.originalPosition(readPosition(position));
            }
            result.add(builder.build());
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Features

    private StructuredValue writeFeature(SerpFeature feature) {
        if (!(feature instanceof AbstractSerpFeature)) {
            throw new Serp4jJsonException("cannot write feature of type " + feature.getClass().getName()
                    + ": only the feature classes of serp4j-core are part of the JSON format");
        }
        AbstractSerpFeature base = (AbstractSerpFeature) feature;
        Out out = Out.object().put("id", base.getId().getValue());
        if (feature instanceof GeneratedAnswerFeature) {
            GeneratedAnswerFeature f = (GeneratedAnswerFeature) feature;
            List<StructuredValue> segments = new ArrayList<StructuredValue>();
            for (AnswerSegment segment : f.getSegments()) {
                List<StructuredValue> indexes = new ArrayList<StructuredValue>();
                for (Integer index : segment.getReferenceIndexes()) {
                    indexes.add(StructuredValue.of(index));
                }
                segments.add(Out.object().put("text", segment.getText()).putArray("referenceIndexes", indexes)
                        .build());
            }
            List<StructuredValue> references = new ArrayList<StructuredValue>();
            for (SourceReference reference : f.getReferences()) {
                references.add(writeSource(reference).build());
            }
            out.put("kind", KIND_GENERATED_ANSWER)
                    .put("type", f.getType().getCode())
                    .put("text", f.getText())
                    .put("markdown", f.getMarkdown())
                    .putArray("segments", segments)
                    .putArray("references", references)
                    .putTexts("followUpSuggestions", f.getFollowUpSuggestions())
                    .put("complete", f.isComplete());
        } else if (feature instanceof KnowledgePanelFeature) {
            KnowledgePanelFeature f = (KnowledgePanelFeature) feature;
            List<StructuredValue> facts = new ArrayList<StructuredValue>();
            for (Fact fact : f.getFacts()) {
                List<StructuredValue> values = new ArrayList<StructuredValue>();
                for (Fact.Value value : fact.getValues()) {
                    values.add(Out.object().put("text", value.getText()).put("url", value.getUrl()).build());
                }
                facts.add(Out.object().put("key", fact.getKey()).putArray("values", values).build());
            }
            List<StructuredValue> images = new ArrayList<StructuredValue>();
            for (MediaInfo image : f.getImages()) {
                images.add(writeMedia(image).build());
            }
            List<StructuredValue> profiles = new ArrayList<StructuredValue>();
            for (SiteLink profile : f.getProfiles()) {
                profiles.add(writeSiteLink(profile));
            }
            out.put("kind", KIND_KNOWLEDGE_PANEL)
                    .put("title", f.getTitle())
                    .put("subtitle", f.getSubtitle())
                    .put("description", f.getDescription())
                    .putArray("facts", facts)
                    .putArray("images", images)
                    .put("websiteUrl", f.getWebsiteUrl())
                    .putArray("profiles", profiles);
            if (f.getDescriptionSource().isPresent()) {
                out.put("descriptionSource", writeSource(f.getDescriptionSource().get()));
            }
        } else if (feature instanceof FeaturedSnippetFeature) {
            FeaturedSnippetFeature f = (FeaturedSnippetFeature) feature;
            out.put("kind", KIND_FEATURED_SNIPPET).put("text", f.getText()).put("source", writeSource(f.getSource()));
        } else if (feature instanceof AnswerBoxFeature) {
            AnswerBoxFeature f = (AnswerBoxFeature) feature;
            out.put("kind", KIND_ANSWER_BOX).put("type", f.getType().getCode()).put("text", f.getText());
            if (f.getSource().isPresent()) {
                out.put("source", writeSource(f.getSource().get()));
            }
        } else if (feature instanceof QuestionAnswerFeature) {
            QuestionAnswerFeature f = (QuestionAnswerFeature) feature;
            List<StructuredValue> questions = new ArrayList<StructuredValue>();
            for (QuestionAnswer qa : f.getQuestions()) {
                Out q = Out.object().put("question", qa.getQuestion()).put("answer", qa.getAnswer());
                if (qa.getSource().isPresent()) {
                    q.put("source", writeSource(qa.getSource().get()));
                }
                questions.add(q.build());
            }
            out.put("kind", KIND_QUESTION_ANSWER).put("type", f.getType().getCode()).putArray("questions", questions);
        } else if (feature instanceof RelatedQueriesFeature) {
            RelatedQueriesFeature f = (RelatedQueriesFeature) feature;
            List<StructuredValue> queries = new ArrayList<StructuredValue>();
            for (RelatedQuery query : f.getQueries()) {
                queries.add(Out.object().put("text", query.getText()).put("url", query.getUrl()).build());
            }
            out.put("kind", KIND_RELATED_QUERIES).putArray("queries", queries);
        } else if (feature instanceof ResultGroupFeature) {
            ResultGroupFeature f = (ResultGroupFeature) feature;
            List<String> items = new ArrayList<String>();
            for (ElementId item : f.getItems()) {
                items.add(item.getValue());
            }
            out.put("kind", KIND_RESULT_GROUP).put("type", f.getType().getCode()).put("title", f.getTitle())
                    .putTexts("items", items);
        } else if (feature instanceof GenericSerpFeature) {
            GenericSerpFeature f = (GenericSerpFeature) feature;
            out.put("kind", KIND_GENERIC).put("type", f.getType().getCode()).put("title", f.getTitle())
                    .put("content", f.getContent());
        } else {
            throw new Serp4jJsonException("cannot write feature of type " + feature.getClass().getName()
                    + ": only the feature classes of serp4j-core are part of the JSON format");
        }
        if (base.getPosition().isPresent()) {
            out.put("position", writePosition(base.getPosition().get()));
        }
        out.putArray("provenance", writeProvenance(base.getProvenance()))
                .put("extensions", writeExtensions(base.getExtensions()));
        return out.build();
    }

    private SerpFeature readFeature(In in) {
        ElementId id = ElementId.of(in.text("id"));
        String kind = in.text("kind");
        AbstractSerpFeature.Builder<?> builder;
        if (KIND_GENERATED_ANSWER.equals(kind)) {
            GeneratedAnswerFeature.Builder b = GeneratedAnswerFeature.builder(id, featureType(in), in.text("text"))
                    .markdown(in.optText("markdown"))
                    .followUpSuggestions(in.texts("followUpSuggestions"))
                    .complete(in.bool("complete"));
            for (In segment : in.objects("segments")) {
                b.segment(AnswerSegment.of(segment.text("text"), segment.integers("referenceIndexes")));
            }
            for (In reference : in.objects("references")) {
                b.reference(readSource(reference));
            }
            builder = b;
        } else if (KIND_KNOWLEDGE_PANEL.equals(kind)) {
            KnowledgePanelFeature.Builder b = KnowledgePanelFeature.builder(id, in.text("title"))
                    .subtitle(in.optText("subtitle"))
                    .description(in.optText("description"))
                    .websiteUrl(in.optText("websiteUrl"));
            In descriptionSource = in.optObj("descriptionSource");
            if (descriptionSource != null) {
                b.descriptionSource(readSource(descriptionSource));
            }
            for (In fact : in.objects("facts")) {
                Fact.Builder f = Fact.builder(fact.text("key"));
                for (In value : fact.objects("values")) {
                    f.value(Fact.Value.of(value.text("text"), value.optText("url")));
                }
                b.fact(f.build());
            }
            for (In image : in.objects("images")) {
                b.image(readMedia(image));
            }
            for (In profile : in.objects("profiles")) {
                b.profile(readSiteLink(profile));
            }
            builder = b;
        } else if (KIND_FEATURED_SNIPPET.equals(kind)) {
            builder = FeaturedSnippetFeature.builder(id, in.text("text"), readSource(in.obj("source")));
        } else if (KIND_ANSWER_BOX.equals(kind)) {
            AnswerBoxFeature.Builder b = AnswerBoxFeature.builder(id, featureType(in), in.text("text"));
            In source = in.optObj("source");
            if (source != null) {
                b.source(readSource(source));
            }
            builder = b;
        } else if (KIND_QUESTION_ANSWER.equals(kind)) {
            QuestionAnswerFeature.Builder b = QuestionAnswerFeature.builder(id, featureType(in));
            for (In q : in.objects("questions")) {
                In source = q.optObj("source");
                b.question(QuestionAnswer.of(q.text("question"), q.optText("answer"),
                        source == null ? null : readSource(source)));
            }
            builder = b;
        } else if (KIND_RELATED_QUERIES.equals(kind)) {
            RelatedQueriesFeature.Builder b = RelatedQueriesFeature.builder(id);
            for (In q : in.objects("queries")) {
                b.query(RelatedQuery.of(q.text("text"), q.optText("url")));
            }
            builder = b;
        } else if (KIND_RESULT_GROUP.equals(kind)) {
            ResultGroupFeature.Builder b = ResultGroupFeature.builder(id, featureType(in)).title(in.optText("title"));
            for (String item : in.texts("items")) {
                b.item(ElementId.of(item));
            }
            builder = b;
        } else if (KIND_GENERIC.equals(kind)) {
            StructuredValue content = in.structured("content");
            if (content == null) {
                throw in.fail("content", "required member is missing");
            }
            builder = GenericSerpFeature.builder(id, featureType(in), content).title(in.optText("title"));
        } else {
            throw in.fail("kind", "unknown feature kind '" + kind + "'");
        }
        In position = in.optObj("position");
        if (position != null) {
            builder.position(readPosition(position));
        }
        builder.provenance(readProvenance(in));
        In extensions = in.optObj("extensions");
        if (extensions != null) {
            builder.extensions(readExtensions(extensions));
        }
        return buildFeature(builder);
    }

    private static SerpFeature buildFeature(AbstractSerpFeature.Builder<?> builder) {
        if (builder instanceof GeneratedAnswerFeature.Builder) {
            return ((GeneratedAnswerFeature.Builder) builder).build();
        } else if (builder instanceof KnowledgePanelFeature.Builder) {
            return ((KnowledgePanelFeature.Builder) builder).build();
        } else if (builder instanceof FeaturedSnippetFeature.Builder) {
            return ((FeaturedSnippetFeature.Builder) builder).build();
        } else if (builder instanceof AnswerBoxFeature.Builder) {
            return ((AnswerBoxFeature.Builder) builder).build();
        } else if (builder instanceof QuestionAnswerFeature.Builder) {
            return ((QuestionAnswerFeature.Builder) builder).build();
        } else if (builder instanceof RelatedQueriesFeature.Builder) {
            return ((RelatedQueriesFeature.Builder) builder).build();
        } else if (builder instanceof ResultGroupFeature.Builder) {
            return ((ResultGroupFeature.Builder) builder).build();
        }
        return ((GenericSerpFeature.Builder) builder).build();
    }

    private static SerpFeatureType featureType(In in) {
        return SerpFeatureType.of(in.text("type"));
    }

    private static Out writeSource(SourceReference source) {
        Out out = Out.object()
                .put("url", source.getUrl())
                .put("title", source.getTitle())
                .put("snippet", source.getSnippet())
                .put("domain", source.getDomain())
                .put("sourceName", source.getSourceName());
        if (source.getMatchingResult().isPresent()) {
            out.put("matchingResult", source.getMatchingResult().get().getValue());
        }
        return out;
    }

    private static SourceReference readSource(In in) {
        SourceReference.Builder builder = SourceReference.builder(in.text("url"))
                .title(in.optText("title"))
                .snippet(in.optText("snippet"))
                .domain(in.optText("domain"))
                .sourceName(in.optText("sourceName"));
        String matching = in.optText("matchingResult");
        if (matching != null) {
            builder.matchingResult(ElementId.of(matching));
        }
        return builder.build();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Extensions

    /** Returns {@code null} for empty extensions, so the member is left out. */
    private static Out writeExtensions(Extensions extensions) {
        if (extensions.isEmpty()) {
            return null;
        }
        List<StructuredValue> values = new ArrayList<StructuredValue>();
        for (ExtensionKey<?> key : extensions.getKeys()) {
            String type = EXTENSION_TYPES.get(key.getType());
            if (type == null) {
                throw new Serp4jJsonException("cannot write extension " + key + " of type " + key.getType().getName()
                        + ": the JSON format supports extension values of type " + EXTENSION_CLASSES.keySet()
                        + " (String, Boolean, Integer, Long, Double, BigDecimal, StructuredValue); put other data"
                        + " into the unmapped remainder as StructuredValue");
            }
            Object value = extensions.get(key).get();
            values.add(Out.object()
                    .put("namespace", key.getNamespace())
                    .put("name", key.getName())
                    .put("type", type)
                    .put("value", toStructured(value))
                    .build());
        }
        StructuredValue.ObjectBuilder unmapped = StructuredValue.objectBuilder();
        for (String namespace : extensions.getUnmappedNamespaces()) {
            unmapped.put(namespace, extensions.getUnmapped(namespace).get());
        }
        Out out = Out.object().putArray("values", values);
        if (!extensions.getUnmappedNamespaces().isEmpty()) {
            out.put("unmapped", unmapped.build());
        }
        return out;
    }

    private static StructuredValue toStructured(Object value) {
        if (value instanceof StructuredValue) {
            return (StructuredValue) value;
        } else if (value instanceof String) {
            return StructuredValue.of((String) value);
        } else if (value instanceof Boolean) {
            return StructuredValue.of((Boolean) value);
        } else if (value instanceof Integer || value instanceof Long) {
            return StructuredValue.of(((Number) value).longValue());
        } else if (value instanceof Double) {
            return StructuredValue.of((Double) value);
        }
        return StructuredValue.of((BigDecimal) value);
    }

    private static Extensions readExtensions(In in) {
        Extensions.Builder builder = Extensions.builder();
        for (In entry : in.objects("values")) {
            String typeName = entry.text("type");
            Class<?> type = EXTENSION_CLASSES.get(typeName);
            if (type == null) {
                throw entry.fail("type", "unknown extension value type '" + typeName + "'");
            }
            putExtension(builder, ExtensionKey.of(entry.text("namespace"), entry.text("name"), type),
                    fromStructured(entry, type));
        }
        for (Map.Entry<String, StructuredValue> unmapped : in.members("unmapped").entrySet()) {
            builder.unmapped(unmapped.getKey(), unmapped.getValue());
        }
        return builder.build();
    }

    private static Object fromStructured(In entry, Class<?> type) {
        if (type == StructuredValue.class) {
            StructuredValue value = entry.structured("value");
            if (value == null) {
                throw entry.fail("value", "required member is missing");
            }
            return value;
        } else if (type == String.class) {
            return entry.text("value");
        } else if (type == Boolean.class) {
            return entry.bool("value");
        } else if (type == Integer.class) {
            return entry.integer("value");
        } else if (type == Long.class) {
            Long value = entry.optLong("value");
            if (value == null) {
                throw entry.fail("value", "required member is missing");
            }
            return value;
        } else if (type == Double.class) {
            return entry.dbl("value");
        }
        return entry.decimal("value");
    }

    @SuppressWarnings("unchecked")
    private static <T> void putExtension(Extensions.Builder builder, ExtensionKey<T> key, Object value) {
        builder.put(key, (T) value);
    }

    // ------------------------------------------------------------------------------------------------------------
    // SearchMetadata

    private Out writeMetadata(SearchMetadata metadata) {
        List<String> contributors = new ArrayList<String>();
        for (ProviderId provider : metadata.getContributingProviders()) {
            contributors.add(provider.getCode());
        }
        List<StructuredValue> warnings = new ArrayList<StructuredValue>();
        for (SearchWarning warning : metadata.getWarnings()) {
            Out w = Out.object().put("code", warning.getCode()).put("message", warning.getMessage())
                    .put("details", warning.getDetails());
            if (warning.getProvider().isPresent()) {
                w.put("provider", warning.getProvider().get().getCode());
            }
            warnings.add(w.build());
        }
        QueryInfo query = metadata.getQuery();
        Out queryOut = Out.object()
                .put("originalQuery", query.getOriginalQuery())
                .put("effectiveQuery", query.getEffectiveQuery());
        if (query.getSpellCorrection().isPresent()) {
            SpellCorrection correction = query.getSpellCorrection().get();
            queryOut.put("spellCorrection", Out.object()
                    .put("correctedQuery", correction.getCorrectedQuery())
                    .put("type", correction.getType().name())
                    .put("providerType", correction.getProviderType()));
        }
        Out out = Out.object();
        if (metadata.getProvider().isPresent()) {
            out.put("provider", metadata.getProvider().get().getCode());
        }
        if (metadata.getEngine().isPresent()) {
            out.put("engine", metadata.getEngine().get().getCode());
        }
        if (metadata.getVertical().isPresent()) {
            out.put("vertical", metadata.getVertical().get().getCode());
        }
        out.putTexts("contributingProviders", contributors).put("query", queryOut);
        if (metadata.getLocale().isPresent()) {
            out.put("locale", writeLocale(metadata.getLocale().get()));
        }
        if (metadata.getDevice().isPresent()) {
            out.put("device", metadata.getDevice().get().name());
        }
        if (metadata.getPage().isPresent()) {
            PageInfo page = metadata.getPage().get();
            out.put("page", Out.object()
                    .put("offset", page.getOffset())
                    .put("requestedCount", page.getRequestedCount())
                    .put("pageNumber", page.getPageNumber())
                    .put("hasMore", page.getHasMore())
                    .put("totalResultsEstimate", page.getTotalResultsEstimate()));
        }
        if (metadata.getExecution().isPresent()) {
            ExecutionInfo execution = metadata.getExecution().get();
            out.put("execution", Out.object()
                    .put("startedAt", execution.getStartedAt())
                    .put("duration", execution.getDuration())
                    .put("providerReportedDuration", execution.getProviderReportedDuration())
                    .put("providerRequestId", execution.getProviderRequestId())
                    .put("sourceUrl", execution.getSourceUrl())
                    .put("attempts", execution.getAttempts())
                    .put("providerTimestamp", execution.getProviderTimestamp()));
        }
        if (metadata.getUsage().isPresent()) {
            out.put("usage", writeUsage(metadata.getUsage().get()));
        }
        out.put("complete", metadata.isComplete()).putArray("warnings", warnings);
        if (includeRawPayload && metadata.getRawPayload().isPresent()) {
            out.put("rawPayload", writeRawPayload(metadata.getRawPayload().get()));
        }
        if (metadata.getConversation().isPresent()) {
            ConversationContext conversation = metadata.getConversation().get();
            Out c = Out.object()
                    .put("sessionId", conversation.getSessionId().getValue())
                    .put("turnIndex", conversation.getTurnIndex())
                    .put("providerConversationId", conversation.getProviderConversationId());
            if (conversation.getAnswerElement().isPresent()) {
                c.put("answerElement", conversation.getAnswerElement().get().getValue());
            }
            out.put("conversation", c);
        }
        out.put("extensions", writeExtensions(metadata.getExtensions()));
        return out;
    }

    private SearchMetadata readMetadata(In in) {
        In queryIn = in.obj("query");
        SpellCorrection correction = null;
        In correctionIn = queryIn.optObj("spellCorrection");
        if (correctionIn != null) {
            correction = SpellCorrection.of(correctionIn.text("correctedQuery"),
                    correctionIn.enumValue("type", SpellCorrection.Type.class), correctionIn.optText("providerType"));
        }
        SearchMetadata.Builder builder = SearchMetadata.builder(QueryInfo.of(queryIn.text("originalQuery"),
                queryIn.optText("effectiveQuery"), correction));
        String provider = in.optText("provider");
        if (provider != null) {
            builder.provider(ProviderId.of(provider));
        }
        String engine = in.optText("engine");
        if (engine != null) {
            builder.engine(SearchEngineId.of(engine));
        }
        String vertical = in.optText("vertical");
        if (vertical != null) {
            builder.vertical(SearchVertical.of(vertical));
        }
        List<ProviderId> contributors = new ArrayList<ProviderId>();
        for (String code : in.texts("contributingProviders")) {
            contributors.add(ProviderId.of(code));
        }
        builder.contributingProviders(contributors);
        In locale = in.optObj("locale");
        if (locale != null) {
            builder.locale(readLocale(locale));
        }
        builder.device(in.optEnum("device", DeviceType.class));
        In page = in.optObj("page");
        if (page != null) {
            PageInfo.Builder p = PageInfo.builder(page.integer("offset"), page.integer("requestedCount"));
            Integer pageNumber = page.optInteger("pageNumber");
            if (pageNumber != null) {
                p.pageNumber(pageNumber);
            }
            Boolean hasMore = page.optBool("hasMore");
            if (hasMore != null) {
                p.hasMore(hasMore);
            }
            Long total = page.optLong("totalResultsEstimate");
            if (total != null) {
                p.totalResultsEstimate(total);
            }
            builder.page(p.build());
        }
        In execution = in.optObj("execution");
        if (execution != null) {
            ExecutionInfo.Builder e = ExecutionInfo.builder()
                    .startedAt(execution.optInstant("startedAt"))
                    .duration(execution.optDuration("duration"))
                    .providerReportedDuration(execution.optDuration("providerReportedDuration"))
                    .providerRequestId(execution.optText("providerRequestId"))
                    .sourceUrl(execution.optText("sourceUrl"))
                    .providerTimestamp(execution.optInstant("providerTimestamp"));
            Integer attempts = execution.optInteger("attempts");
            if (attempts != null) {
                e.attempts(attempts);
            }
            builder.execution(e.build());
        }
        In usage = in.optObj("usage");
        if (usage != null) {
            builder.usage(readUsage(usage));
        }
        Boolean complete = in.optBool("complete");
        builder.complete(complete == null || complete);
        for (In warning : in.objects("warnings")) {
            SearchWarning.Builder w = SearchWarning.builder(warning.text("code"), warning.text("message"))
                    .details(warning.structured("details"));
            String warningProvider = warning.optText("provider");
            if (warningProvider != null) {
                w.provider(ProviderId.of(warningProvider));
            }
            builder.warning(w.build());
        }
        In raw = in.optObj("rawPayload");
        if (raw != null) {
            builder.rawPayload(readRawPayload(raw));
        }
        In conversation = in.optObj("conversation");
        if (conversation != null) {
            ConversationContext.Builder c = ConversationContext.builder(
                    SearchSessionId.of(conversation.text("sessionId")), conversation.integer("turnIndex"))
                    .providerConversationId(conversation.optText("providerConversationId"));
            String answer = conversation.optText("answerElement");
            if (answer != null) {
                c.answerElement(ElementId.of(answer));
            }
            builder.conversation(c.build());
        }
        In extensions = in.optObj("extensions");
        if (extensions != null) {
            builder.extensions(readExtensions(extensions));
        }
        return builder.build();
    }

    private static Out writeLocale(SearchLocale locale) {
        Out out = Out.object()
                .put("countryCode", locale.getCountryCode())
                .put("languageCode", locale.getLanguageCode())
                .put("uiLanguage", locale.getUiLanguage());
        if (locale.getLocation().isPresent()) {
            GeoLocation location = locale.getLocation().get();
            out.put("location", Out.object()
                    .put("name", location.getName())
                    .put("latitude", location.getLatitude())
                    .put("longitude", location.getLongitude()));
        }
        return out;
    }

    private static SearchLocale readLocale(In in) {
        SearchLocale.Builder builder = SearchLocale.builder()
                .countryCode(in.optText("countryCode"))
                .languageCode(in.optText("languageCode"))
                .uiLanguage(in.optText("uiLanguage"));
        In location = in.optObj("location");
        if (location != null) {
            String name = location.optText("name");
            Double latitude = location.optDouble("latitude");
            Double longitude = location.optDouble("longitude");
            if ((latitude == null) != (longitude == null)) {
                throw new Serp4jJsonException(location.path() + ": 'latitude' and 'longitude' must be present together");
            }
            if (latitude == null) {
                if (name == null) {
                    throw new Serp4jJsonException(location.path() + ": a location needs a name or coordinates");
                }
                builder.location(GeoLocation.named(name));
            } else {
                builder.location(name == null ? GeoLocation.coordinates(latitude, longitude)
                        : GeoLocation.of(name, latitude, longitude));
            }
        }
        return builder.build();
    }

    private static Out writeUsage(ProviderUsage usage) {
        Out out = Out.object().put("credits", usage.getCredits());
        if (usage.getCost().isPresent()) {
            MonetaryAmount cost = usage.getCost().get();
            out.put("cost", Out.object().put("amount", StructuredValue.of(cost.getAmount()))
                    .put("currency", cost.getCurrency()));
        }
        if (usage.getRateLimit().isPresent()) {
            RateLimitInfo rateLimit = usage.getRateLimit().get();
            out.put("rateLimit", Out.object()
                    .put("limit", rateLimit.getLimit())
                    .put("remaining", rateLimit.getRemaining())
                    .put("resetAt", rateLimit.getResetAt()));
        }
        return out;
    }

    private static ProviderUsage readUsage(In in) {
        ProviderUsage.Builder builder = ProviderUsage.builder().credits(in.optDecimal("credits"));
        In cost = in.optObj("cost");
        if (cost != null) {
            builder.cost(MonetaryAmount.of(cost.decimal("amount"), cost.text("currency")));
        }
        In rateLimit = in.optObj("rateLimit");
        if (rateLimit != null) {
            RateLimitInfo.Builder r = RateLimitInfo.builder().resetAt(rateLimit.optInstant("resetAt"));
            Long limit = rateLimit.optLong("limit");
            if (limit != null) {
                r.limit(limit);
            }
            Long remaining = rateLimit.optLong("remaining");
            if (remaining != null) {
                r.remaining(remaining);
            }
            builder.rateLimit(r.build());
        }
        return builder.build();
    }

    private static Out writeRawPayload(RawPayload payload) {
        StructuredValue.ObjectBuilder headers = StructuredValue.objectBuilder();
        for (Map.Entry<String, List<String>> header : payload.getHeaders().entrySet()) {
            List<StructuredValue> values = new ArrayList<StructuredValue>();
            for (String value : header.getValue()) {
                values.add(StructuredValue.of(value));
            }
            headers.put(header.getKey(), StructuredValue.ofArray(values));
        }
        Out out = Out.object()
                .put("contentType", payload.getContentType())
                .put("httpStatus", payload.getHttpStatus())
                .put("body", Base64.getEncoder().encodeToString(payload.getBody()));
        if (payload.getCharset().isPresent()) {
            out.put("charset", payload.getCharset().get().name());
        }
        if (!payload.getHeaders().isEmpty()) {
            out.put("headers", headers.build());
        }
        return out;
    }

    private static RawPayload readRawPayload(In in) {
        byte[] body;
        try {
            body = Base64.getDecoder().decode(in.text("body"));
        } catch (IllegalArgumentException e) {
            throw in.fail("body", "expected base64 but found invalid data");
        }
        RawPayload.Builder builder = RawPayload.builder(in.text("contentType"), body);
        String charset = in.optText("charset");
        if (charset != null) {
            try {
                builder.charset(Charset.forName(charset));
            } catch (IllegalArgumentException e) {
                throw in.fail("charset", "unknown charset '" + charset + "'");
            }
        }
        Integer status = in.optInteger("httpStatus");
        if (status != null) {
            builder.httpStatus(status);
        }
        In headers = in.optObj("headers");
        if (headers != null) {
            for (String name : in.members("headers").keySet()) {
                for (String value : headers.texts(name)) {
                    builder.header(name, value);
                }
            }
        }
        return builder.build();
    }

    // ------------------------------------------------------------------------------------------------------------
    // SearchAnalysis

    private static Out writeAnalysis(SearchAnalysis analysis) {
        List<StructuredValue> provenance = new ArrayList<StructuredValue>();
        for (AnalysisProvenance origin : analysis.getProvenance()) {
            provenance.add(Out.object()
                    .put("analyzerId", origin.getAnalyzerId())
                    .put("version", origin.getVersion())
                    .put("model", origin.getModel())
                    .put("analyzedAt", origin.getAnalyzedAt())
                    .build());
        }
        List<StructuredValue> entities = new ArrayList<StructuredValue>();
        for (EntityMention entity : analysis.getEntities()) {
            entities.add(Out.object()
                    .put("name", entity.getName())
                    .put("type", entity.getType())
                    .put("confidence", entity.getConfidence())
                    .putTexts("elements", elementIds(entity.getElements()))
                    .build());
        }
        List<StructuredValue> topics = new ArrayList<StructuredValue>();
        for (Topic topic : analysis.getTopics()) {
            topics.add(Out.object()
                    .put("label", topic.getLabel())
                    .put("weight", topic.getWeight())
                    .putTexts("elements", elementIds(topic.getElements()))
                    .build());
        }
        Out out = Out.object().putArray("provenance", provenance);
        if (analysis.getIntent().isPresent()) {
            SearchIntent intent = analysis.getIntent().get();
            out.put("intent", Out.object()
                    .put("category", intent.getCategory().getCode())
                    .put("confidence", intent.getConfidence())
                    .put("rationale", intent.getRationale()));
        }
        out.putArray("entities", entities).putArray("topics", topics);
        if (!analysis.getMetrics().isEmpty()) {
            StructuredValue.ObjectBuilder metrics = StructuredValue.objectBuilder();
            for (Map.Entry<MetricKey, Double> metric : analysis.getMetrics().asMap().entrySet()) {
                metrics.put(metric.getKey().getCode(), metric.getValue());
            }
            out.put("metrics", metrics.build());
        }
        return out.put("extensions", writeExtensions(analysis.getExtensions()));
    }

    private static SearchAnalysis readAnalysis(In in) {
        List<In> provenance = in.objects("provenance");
        if (provenance.isEmpty()) {
            throw in.fail("provenance", "an analysis needs at least one provenance entry");
        }
        SearchAnalysis.Builder builder = null;
        for (In origin : provenance) {
            AnalysisProvenance p = AnalysisProvenance.builder(origin.text("analyzerId"))
                    .version(origin.optText("version"))
                    .model(origin.optText("model"))
                    .analyzedAt(origin.optInstant("analyzedAt"))
                    .build();
            if (builder == null) {
                builder = SearchAnalysis.builder(p);
            } else {
                builder.provenance(p);
            }
        }
        In intent = in.optObj("intent");
        if (intent != null) {
            builder.intent(SearchIntent.of(IntentCategory.of(intent.text("category")), intent.dbl("confidence"),
                    intent.optText("rationale")));
        }
        for (In entity : in.objects("entities")) {
            EntityMention.Builder e = EntityMention.builder(entity.text("name")).type(entity.optText("type"))
                    .elements(toElementIds(entity.texts("elements")));
            Double confidence = entity.optDouble("confidence");
            if (confidence != null) {
                e.confidence(confidence);
            }
            builder.entity(e.build());
        }
        for (In topic : in.objects("topics")) {
            builder.topic(Topic.builder(topic.text("label"), topic.dbl("weight"))
                    .elements(toElementIds(topic.texts("elements"))).build());
        }
        In metricsIn = in.optObj("metrics");
        if (metricsIn != null) {
            SearchMetrics.Builder metrics = SearchMetrics.builder();
            for (String code : in.members("metrics").keySet()) {
                metrics.put(MetricKey.of(code), metricsIn.dbl(code));
            }
            builder.metrics(metrics.build());
        }
        In extensions = in.optObj("extensions");
        if (extensions != null) {
            builder.extensions(readExtensions(extensions));
        }
        return builder.build();
    }

    private static List<String> elementIds(Collection<ElementId> ids) {
        List<String> result = new ArrayList<String>(ids.size());
        for (ElementId id : ids) {
            result.add(id.getValue());
        }
        return result;
    }

    private static List<ElementId> toElementIds(List<String> values) {
        List<ElementId> result = new ArrayList<ElementId>(values.size());
        for (String value : values) {
            result.add(ElementId.of(value));
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------------------------
    // shared

    private static Out writeTarget(SearchTarget target) {
        return Out.object().put("engine", target.getEngine().getCode()).put("vertical", target.getVertical().getCode());
    }

    private static SearchTarget readTarget(In in) {
        return SearchTarget.of(SearchEngineId.of(in.text("engine")), SearchVertical.of(in.text("vertical")));
    }
}
