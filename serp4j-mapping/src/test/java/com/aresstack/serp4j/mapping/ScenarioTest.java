package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.data.ResultType;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.data.SerpArea;
import com.aresstack.serp4j.core.model.ext.ExtensionKey;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.FeaturedSnippetFeature;
import com.aresstack.serp4j.core.model.feature.GenericSerpFeature;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.QuestionAnswerFeature;
import com.aresstack.serp4j.core.model.feature.RelatedQueriesFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeature;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.verify.MappingSnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three structures of the former prototype (ADR-019), now against the real engine and domain model. A and B
 * are purely declarative, C needs three custom mappers. Golden snapshots in {@code scenarios/<x>/expected.json}.
 */
class ScenarioTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "b", "c"})
    void snapshot_matches_golden_file(String scenario) throws Exception {
        String actual = MappingSnapshot.render(Scenarios.engine(scenario).map(Scenarios.response(scenario)));
        assertThat(actual).isEqualTo(Scenarios.expected(scenario, actual));
    }

    @Test
    void scenario_a_is_purely_declarative() {
        MappingResult r = Scenarios.engine("a").map(Scenarios.response("a"));

        assertThat(r.getData().getOrganicResults()).extracting(SearchResultItem::getUrl)
                .containsExactly("https://example.com", "https://Second.Example.org/path");
        SearchResultItem first = r.getData().getOrganicResults().get(0);
        assertThat(first.getTitle()).hasValue("Example");
        assertThat(first.getDomain()).hasValue("example.com");
        assertThat(first.getPosition().get().getRankInGroup()).hasValue(1);
        assertThat(r.getData().getOrganicResults().get(1).getDomain()).hasValue("second.example.org");
        // Fields nobody mapped are kept, not dropped.
        assertThat(first.getExtensions().getUnmapped("synthetic-a")).hasValue(
                JsonTrees.read("{\"cached_page_link\":\"https://cache.example/1\"}"));

        RelatedQueriesFeature related = r.getData().getFeatures(RelatedQueriesFeature.class).get(0);
        assertThat(related.getQueries()).extracting(q -> q.getText()).containsExactly("example meaning", "example synonyms");
        // people_also_ask has no declarative form (ADR-019: lists to typed sub-objects are Java): kept generically.
        GenericSerpFeature paa = r.getData().getFeatures(GenericSerpFeature.class).get(0);
        assertThat(paa.getTitle()).hasValue("What is an example?");
        assertThat(paa.getContent().get("answer")).isPresent();

        assertThat(r.getMetadata().getOriginalQuery()).hasValue("example");
        assertThat(r.getMetadata().getTotalResultsEstimate()).hasValue(1234567L);
        assertThat(r.getMetadata().getExtensions().get(ExtensionKey.of("synthetic-a", "nextPage", String.class)))
                .hasValue("https://a.example/search?page=2");
        assertThat(r.getWarnings()).isEmpty();
    }

    @Test
    void scenario_b_alternative_names_converters_required_fields() {
        MappingResult r = Scenarios.engine("b").map(Scenarios.response("b"));

        List<SearchResultItem> items = r.getData().getResults();
        assertThat(items).extracting(SearchResultItem::getUrl).containsExactly("https://example.com", "https://third.example.net");
        assertThat(items.get(0).getSnippet()).hasValue("Description & more");
        assertThat(items.get(0).getAdditionalSnippets()).containsExactly("More text", "Even more");
        // index position counts the source list, including the skipped element without URL.
        assertThat(items.get(1).getPosition().get().getRankInGroup()).hasValue(3);
        assertThat(items.get(1).getPosition().get().getRankAbsolute()).hasValue(3);
        assertThat(items.get(1).getThumbnail().get().getUrl()).isEqualTo("https://img.example/3.png");
        assertThat(items.get(1).getExtensions().get(ExtensionKey.of("synthetic-b", "thumbnailWidth", Integer.class))).hasValue(120);
        // Only the consumed leaves are removed: thumbnail.height survives.
        assertThat(items.get(1).getExtensions().getUnmapped("synthetic-b"))
                .hasValue(JsonTrees.read("{\"thumbnail\":{\"height\":80}}"));

        KnowledgePanelFeature panel = r.getData().getFeatures(KnowledgePanelFeature.class).get(0);
        assertThat(panel.getTitle()).isEqualTo("Example Corp");
        assertThat(panel.getExtensions().getUnmapped("synthetic-b")).hasValueSatisfying(
                v -> assertThat(v.get("attributes")).isPresent());
        GenericSerpFeature experimental = r.getData().getFeatures(GenericSerpFeature.class).get(0);
        assertThat(experimental.getType().getCode()).isEqualTo("experimental_block");
        assertThat(experimental.getContent()).isEqualTo(JsonTrees.read("{\"foo\":\"bar\"}"));

        assertThat(r.getMetadata().getDuration()).hasValue(Duration.ofMillis(42));
        assertThat(r.getWarnings()).hasSize(1);
        SearchWarning skipped = r.getWarnings().get(0);
        assertThat(skipped.getCode()).isEqualTo(SearchWarning.MAPPING_INCOMPLETE);
        assertThat(skipped.getMessage()).contains("$.results[1]").contains("url");
        // The skipped element travels in the warning, so even it is not lost.
        assertThat(skipped.getDetails().get().get("element").get().get("name"))
                .hasValue(StructuredValue.of("Only title, no url"));
    }

    @Test
    void scenario_c_polymorphic_list_with_custom_mappers() {
        MappingResult r = Scenarios.engine("c").map(Scenarios.response("c"));

        assertThat(r.getData().getResults()).extracting(i -> i.getType().getCode()).containsExactly("organic", "paid", "organic");
        SearchResultItem ad = r.getData().getResults(ResultType.PAID).get(0);
        assertThat(ad.getPosition().get().getArea()).isEqualTo(SerpArea.SIDEBAR);
        assertThat(ad.getPosition().get().getRankAbsolute()).hasValue(3);
        SearchResultItem first = r.getData().getOrganicResults().get(0);
        assertThat(first.getPosition().get().getRankInGroup()).hasValue(1);
        assertThat(first.getPosition().get().getRankAbsolute()).hasValue(2);
        assertThat(first.getDisplayUrl()).hasValue("example.com › home");

        FeaturedSnippetFeature snippet = r.getData().getFeatures(FeaturedSnippetFeature.class).get(0);
        assertThat(snippet.getText()).isEqualTo("Snippet text");
        assertThat(snippet.getSource().getUrl()).isEqualTo("https://example.com/snippet");
        assertThat(snippet.getSource().getDomain()).hasValue("example.com");

        KnowledgePanelFeature panel = r.getData().getFeatures(KnowledgePanelFeature.class).get(0);
        assertThat(panel.getFacts()).hasSize(1);
        assertThat(panel.getFacts().get(0).getKey()).isEqualTo("Founded");
        assertThat(panel.getPosition().get().getRankAbsolute()).hasValue(4);
        // The image row the mapper ignored is still there.
        assertThat(panel.getExtensions().getUnmapped("synthetic-c").get().toString()).contains("knowledge_graph_images_item");

        QuestionAnswerFeature paa = r.getData().getFeatures(QuestionAnswerFeature.class).get(0);
        assertThat(paa.getQuestions().get(0).getSource().get().getUrl()).isEqualTo("https://example.com/faq");

        SerpFeature unknown = r.getData().getFeatures().get(r.getData().getFeatures().size() - 1);
        assertThat(unknown).isInstanceOf(GenericSerpFeature.class);
        assertThat(unknown.getType().getCode()).isEqualTo("brand_new_widget_2027");
        assertThat(r.getWarnings()).extracting(SearchWarning::getCode).containsExactly(SearchWarning.UNKNOWN_ELEMENT);

        assertThat(r.getMetadata().getProviderRequestId()).hasValue("t-1");
        assertThat(r.getMetadata().toUsage().get().getCost().get().getAmount()).isEqualByComparingTo(new BigDecimal("0.002"));
        assertThat(r.getMetadata().getCurrency()).hasValue("USD");
        // skipWhen: the images block is deliberately not mapped and therefore stays in the root rest.
        StructuredValue rootRest = r.getMetadata().getExtensions().getUnmapped("synthetic-c").get();
        assertThat(rootRest.get("status_code")).hasValue(StructuredValue.of(20000));
        assertThat(rootRest.toString()).contains("\"images\"").doesNotContain("brand_new_widget_2027");
    }
}
