package com.aresstack.serp4j.core.model.feature;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.id.ElementId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SerpFeatureTest {

    @Test
    void generatedAnswerValidatesSegmentReferences() {
        GeneratedAnswerFeature.Builder builder = GeneratedAnswerFeature
                .builder(ElementId.of("ai"), SerpFeatureType.AI_OVERVIEW, "Answer text")
                .reference(SourceReference.of("https://a.example", "A"))
                .segment(AnswerSegment.of("cites a", 0));
        assertThat(builder.build().isComplete()).isTrue();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.segment(AnswerSegment.of("cites missing", 1)).build())
                .withMessageContaining("reference 1");
    }

    @Test
    void generatedAnswerTypeReflectsSearchModeNotProviderElement() {
        GeneratedAnswerFeature aiMode = GeneratedAnswerFeature
                .builder(ElementId.of("ai"), SerpFeatureType.AI_MODE_ANSWER, "text").complete(false)
                .followUpSuggestion("why?").followUpSuggestion("  ").build();

        assertThat(aiMode.getType()).isEqualTo(SerpFeatureType.AI_MODE_ANSWER);
        assertThat(aiMode.isComplete()).isFalse();
        assertThat(aiMode.getFollowUpSuggestions()).containsExactly("why?");
        assertThat(aiMode.toBuilder().build()).isEqualTo(aiMode);
    }

    @Test
    void visitorDispatchesToTypedMethodsAndFallsBackToOther() {
        SerpFeatureVisitor<String> visitor = new SerpFeatureVisitor<String>() {
            @Override
            public String visitKnowledgePanel(KnowledgePanelFeature feature) {
                return "panel:" + feature.getTitle();
            }

            @Override
            public String visitOther(SerpFeature feature) {
                return "other:" + feature.getType();
            }
        };
        KnowledgePanelFeature panel = KnowledgePanelFeature.builder(ElementId.of("kp"), "Einstein")
                .fact(Fact.of("Born", "1879")).build();
        GenericSerpFeature generic = GenericSerpFeature.builder(ElementId.of("g"), SerpFeatureType.of("hotels_pack"),
                StructuredValue.objectBuilder().put("count", 3).build()).build();

        assertThat(panel.accept(visitor)).isEqualTo("panel:Einstein");
        assertThat(generic.accept(visitor)).isEqualTo("other:hotels_pack");
        assertThat(RelatedQueriesFeature.builder(ElementId.of("r")).build().accept(visitor))
                .isEqualTo("other:related_searches");
    }

    @Test
    void equalityCoversBaseFieldsClassAndContent() {
        ElementId id = ElementId.of("f");
        SourceReference source = SourceReference.of("https://s.example");
        FeaturedSnippetFeature a = FeaturedSnippetFeature.builder(id, "text", source).build();
        FeaturedSnippetFeature b = FeaturedSnippetFeature.builder(id, "text", source).build();
        AnswerBoxFeature box = AnswerBoxFeature.builder(id, SerpFeatureType.ANSWER_BOX, "text").source(source).build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(box);
        assertThat(a).isNotEqualTo(FeaturedSnippetFeature.builder(id, "other", source).build());
        assertThat(a.toString()).contains("FeaturedSnippetFeature").contains("featured_snippet");
    }

    @Test
    void factNeedsAtLeastOneValueAndGroupKeepsItemOrder() {
        assertThatIllegalStateException().isThrownBy(() -> Fact.builder("Born").build());

        ResultGroupFeature group = ResultGroupFeature.builder(ElementId.of("ts"), SerpFeatureType.TOP_STORIES)
                .title("Top stories")
                .items(Arrays.asList(ElementId.of("n2"), ElementId.of("n1"))).build();
        assertThat(group.getItems()).extracting(ElementId::getValue).containsExactly("n2", "n1");
        assertThat(group.getTitle()).contains("Top stories");

        QuestionAnswerFeature paa = QuestionAnswerFeature.builder(ElementId.of("paa"), SerpFeatureType.PEOPLE_ALSO_ASK)
                .question(QuestionAnswer.of("Why?", "Because.", SourceReference.of("https://q.example"))).build();
        assertThat(paa.getQuestions().get(0).getAnswer()).contains("Because.");
    }
}
