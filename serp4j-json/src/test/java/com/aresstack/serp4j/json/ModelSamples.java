package com.aresstack.serp4j.json;

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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Synthetic, fully populated model instances for round-trip tests (no provider data, E-1 does not apply). */
final class ModelSamples {

    static final ProviderId DATAFORSEO = ProviderId.of("dataforseo");
    static final ProviderId BRAVE = ProviderId.of("brave");
    static final SearchSessionId SESSION = SearchSessionId.of("session-1");
    static final Instant T0 = Instant.parse("2026-11-01T03:00:00Z");

    private ModelSamples() {
    }

    static ResultProvenance provenance() {
        return ResultProvenance.builder(DATAFORSEO, SearchEngineId.GOOGLE)
                .originalId(ElementId.of("orig-1"))
                .originalPosition(SerpPosition.builder().rankAbsolute(3).build())
                .build();
    }

    static Extensions extensions() {
        return Extensions.builder()
                .put(ExtensionKey.of("dataforseo", "check_url", String.class), "https://example.org/check")
                .put(ExtensionKey.of("dataforseo", "is_featured", Boolean.class), Boolean.TRUE)
                .put(ExtensionKey.of("dataforseo", "rank_group", Integer.class), 7)
                .put(ExtensionKey.of("dataforseo", "etv", Long.class), 9_000_000_000L)
                .put(ExtensionKey.of("dataforseo", "ratio", Double.class), 0.125)
                .put(ExtensionKey.of("dataforseo", "cpc", BigDecimal.class), new BigDecimal("1.2300"))
                .put(ExtensionKey.of("dataforseo", "raw_item", StructuredValue.class),
                        StructuredValue.objectBuilder().put("xpath", "/html/body").putNull("empty").build())
                .unmapped("dataforseo", StructuredValue.objectBuilder().put("se_type", "organic")
                        .put("amp", false).build())
                .build();
    }

    static SearchResultItem organic() {
        return SearchResultItem.builder(ElementId.of("r1"), ResultType.ORGANIC, "https://example.org/a")
                .title("Example A")
                .snippet("Snippet with ümlaut and \"quotes\"")
                .additionalSnippet("second snippet")
                .highlight(TextRange.of(0, 7))
                .displayUrl("example.org › a")
                .domain("example.org")
                .siteName("Example")
                .publishedDate(PublishedDate.of(T0.minus(Duration.ofDays(2)), "vor 2 Tagen"))
                .language("de")
                .thumbnail(MediaInfo.of("https://example.org/a.png", 120, 80))
                .rating(Rating.of(4.5, 5.0, 120))
                .siteLink(SiteLink.of("https://example.org/a/1", "Unterseite", "Beschreibung"))
                .siteLink(SiteLink.of("https://example.org/a/2"))
                .position(SerpPosition.builder().rankInGroup(1).rankAbsolute(2).page(1).area(SerpArea.MAIN).build())
                .provenance(provenance())
                .extensions(extensions())
                .build();
    }

    static SearchData data() {
        SourceReference source = SourceReference.builder("https://example.org/a").title("Example A")
                .snippet("cited").domain("example.org").sourceName("Example").matchingResult(ElementId.of("r1"))
                .build();
        return SearchData.builder()
                .result(organic())
                .result(SearchResultItem.builder(ElementId.of("r2"), ResultType.of("forum_post"),
                        "https://forum.example.net/t/1").build())
                .result(SearchResultItem.builder(ElementId.of("r3"), ResultType.NEWS, "https://news.example.com/x")
                        .publishedDate(PublishedDate.ofText("gestern"))
                        .thumbnail(MediaInfo.of("https://news.example.com/x.jpg"))
                        .rating(Rating.of(3.0))
                        .build())
                .feature(GeneratedAnswerFeature.builder(ElementId.of("f-ai"), SerpFeatureType.AI_OVERVIEW,
                        "Antworttext")
                        .markdown("**Antworttext**")
                        .segment(AnswerSegment.of("Antworttext", 0))
                        .segment(AnswerSegment.of("ohne Quelle"))
                        .reference(source)
                        .followUpSuggestion("Und weiter?")
                        .complete(false)
                        .position(SerpPosition.builder().area(SerpArea.TOP).build())
                        .provenance(provenance())
                        .extensions(extensions())
                        .build())
                .feature(KnowledgePanelFeature.builder(ElementId.of("f-kp"), "Berlin")
                        .subtitle("Hauptstadt")
                        .description("Berlin ist die Hauptstadt Deutschlands.")
                        .descriptionSource(SourceReference.of("https://de.wikipedia.org/wiki/Berlin", "Wikipedia"))
                        .fact(Fact.builder("Einwohner").value("3,9 Mio.").value("Stand 2024",
                                "https://example.org/stat").build())
                        .image(MediaInfo.of("https://example.org/berlin.jpg", 640, 480))
                        .websiteUrl("https://berlin.de")
                        .profile(SiteLink.of("https://twitter.example/berlin", "Twitter"))
                        .position(SerpPosition.builder().area(SerpArea.SIDEBAR).build())
                        .build())
                .feature(FeaturedSnippetFeature.builder(ElementId.of("f-fs"), "Snippet", source).build())
                .feature(AnswerBoxFeature.builder(ElementId.of("f-calc"), SerpFeatureType.CALCULATOR, "42")
                        .source(SourceReference.of("https://calc.example"))
                        .build())
                .feature(AnswerBoxFeature.builder(ElementId.of("f-weather"), SerpFeatureType.WEATHER, "12 °C").build())
                .feature(QuestionAnswerFeature.builder(ElementId.of("f-paa"), SerpFeatureType.PEOPLE_ALSO_ASK)
                        .question(QuestionAnswer.of("Wie groß ist Berlin?", "891 km²", source))
                        .question(QuestionAnswer.of("Offene Frage?"))
                        .build())
                .feature(RelatedQueriesFeature.builder(ElementId.of("f-rel"))
                        .query("berlin wetter")
                        .query(RelatedQuery.of("berlin karte", "https://www.google.com/search?q=berlin+karte"))
                        .build())
                .feature(ResultGroupFeature.builder(ElementId.of("f-news"), SerpFeatureType.TOP_STORIES)
                        .title("Schlagzeilen")
                        .item(ElementId.of("r3"))
                        .build())
                .feature(GenericSerpFeature.builder(ElementId.of("f-gen"), SerpFeatureType.of("shopping_carousel"),
                        StructuredValue.arrayBuilder().add("a").add(1).add(2.5).add(true)
                                .add(StructuredValue.nullValue()).build())
                        .title("Shopping")
                        .build())
                .build();
    }

    static RawPayload rawPayload() {
        return RawPayload.builder("application/json", "{\"tasks\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .charset(java.nio.charset.StandardCharsets.UTF_8)
                .httpStatus(200)
                .header("Content-Type", "application/json")
                .header("X-Multi", "a")
                .header("X-Multi", "b")
                .build();
    }

    static SearchMetadata metadata() {
        return SearchMetadata.forProvider(DATAFORSEO, SearchEngineId.GOOGLE, SearchVertical.WEB,
                QueryInfo.of("berln", "berlin", SpellCorrection.of("berlin",
                        SpellCorrection.Type.SHOWING_RESULTS_FOR, "showing_results_for")))
                .locale(SearchLocale.builder().countryCode("DE").languageCode("de").uiLanguage("de-DE")
                        .location(GeoLocation.of("Berlin", 52.52, 13.405)).build())
                .device(DeviceType.MOBILE)
                .page(PageInfo.builder(10, 10).pageNumber(2).hasMore(true).totalResultsEstimate(123_456_789_012L)
                        .build())
                .execution(ExecutionInfo.builder()
                        .startedAt(T0)
                        .duration(Duration.ofMillis(1534))
                        .providerReportedDuration(Duration.ofMillis(800))
                        .providerRequestId("req-42")
                        .sourceUrl("https://api.example/v3/serp")
                        .attempts(2)
                        .providerTimestamp(T0.plusMillis(5))
                        .build())
                .usage(ProviderUsage.builder()
                        .cost(MonetaryAmount.of("0.0006", "USD"))
                        .credits(new BigDecimal("0.5"))
                        .rateLimit(RateLimitInfo.builder().limit(2000).remaining(1999)
                                .resetAt(T0.plusSeconds(60)).build())
                        .build())
                .complete(false)
                .warning(SearchWarning.builder(SearchWarning.MAPPING_INCOMPLETE, "2 Elemente nicht abgebildet")
                        .provider(DATAFORSEO)
                        .details(StructuredValue.objectBuilder().put("count", 2).build())
                        .build())
                .warning(SearchWarning.of(SearchWarning.PARTIAL_RESULTS, "Teilergebnis"))
                .rawPayload(rawPayload())
                .extensions(extensions())
                .build();
    }

    static SearchAnalysis analysis() {
        return SearchAnalysis.builder(AnalysisProvenance.builder("heuristic").version("1.0").model("rules")
                .analyzedAt(T0).build())
                .provenance(AnalysisProvenance.of("second"))
                .intent(SearchIntent.of(IntentCategory.LOCAL, 0.75, "Ortsname"))
                .entity(EntityMention.builder("Berlin").type("place").confidence(0.9).element(ElementId.of("r1"))
                        .build())
                .entity(EntityMention.builder("Deutschland").build())
                .topic(Topic.builder("Städte", 0.6).element(ElementId.of("f-kp")).build())
                .metrics(SearchMetrics.builder().put(MetricKey.RESULT_COUNT, 3).put(MetricKey.of("custom"), 0.1)
                        .build())
                .extensions(Extensions.builder().unmapped("heuristic", StructuredValue.of("x")).build())
                .build();
    }

    static SearchResponse fullResponse() {
        return SearchResponse.builder(data()).metadata(metadata()).analysis(analysis()).build();
    }

    static SearchResponse mergedResponse() {
        return SearchResponse.of(SearchData.empty(), SearchMetadata.forMerged(
                java.util.Arrays.asList(DATAFORSEO, BRAVE), QueryInfo.of("berlin")).build());
    }

    static SearchResponse turnResponse(int turnIndex, String text) {
        SearchData data = SearchData.builder()
                .feature(GeneratedAnswerFeature.builder(ElementId.of("answer-" + turnIndex),
                        SerpFeatureType.AI_MODE_ANSWER, text).complete(true).build())
                .build();
        SearchMetadata metadata = SearchMetadata.forProvider(DATAFORSEO, SearchEngineId.GOOGLE,
                SearchVertical.AI_MODE, QueryInfo.of(text))
                .conversation(ConversationContext.builder(SESSION, turnIndex).providerConversationId("conv-9")
                        .answerElement(ElementId.of("answer-" + turnIndex)).build())
                .rawPayload(RawPayload.ofText("text/html", "<html>" + turnIndex + "</html>"))
                .build();
        return SearchResponse.of(data, metadata);
    }

    static SearchConversation conversation() {
        return SearchConversation.builder(SESSION, DATAFORSEO, SearchTarget.of(SearchEngineId.GOOGLE,
                SearchVertical.AI_MODE), SessionState.ACTIVE)
                .turn(ConversationTurn.of(0, SearchConversationRequest.builder("Was ist Berlin?")
                                .expectedTurnIndex(0)
                                .requestedFeature(ResponseFeature.AI_ANSWER)
                                .requestedFeature(ResponseFeature.CITED_SOURCES)
                                .build(),
                        turnResponse(0, "Berlin ist eine Stadt."), T0, T0.plusMillis(2500)))
                .turn(ConversationTurn.of(1, SearchConversationRequest.of("Wie viele Einwohner?"),
                        turnResponse(1, "Etwa 3,9 Millionen."), T0.plusSeconds(10), T0.plusSeconds(12)))
                .build();
    }

    static SessionHandle sessionHandle() {
        return SessionHandle.of(DATAFORSEO, SESSION, "opaque-token-123");
    }
}
