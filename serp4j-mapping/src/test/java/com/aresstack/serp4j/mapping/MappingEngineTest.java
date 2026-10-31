package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.data.PublishedDate;
import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.ext.ExtensionKey;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.AnswerBoxFeature;
import com.aresstack.serp4j.core.model.feature.GenericSerpFeature;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.model.id.SearchVertical;
import com.aresstack.serp4j.core.model.metadata.SearchMetadata;
import com.aresstack.serp4j.core.model.metadata.SearchWarning;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.custom.CustomMapperRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MappingEngineTest {

    private static MappingEngine engine(String sections) {
        return MappingEngine.of(MappingDefinitions.from(JsonTrees.read(
                "{\"id\":\"t\",\"version\":1,\"namespace\":\"t\",\"sections\":[" + sections + "]}")));
    }

    private static MappingResult map(String sections, String response) {
        return engine(sections).map(JsonTrees.read(response));
    }

    @Test
    void first_alternative_with_a_value_wins_and_null_counts_as_missing() {
        MappingResult r = map("{\"id\":\"s\",\"path\":\"$.r[*]\",\"type\":\"organic\",\"fields\":{"
                + "\"url\":[\"link\",\"url\"],\"title\":[\"title\",\"name\"]}}",
                "{\"r\":[{\"link\":null,\"url\":\"https://a.example\",\"title\":null,\"name\":\"A\"}]}");
        SearchResultItem item = r.getData().getResults().get(0);
        assertThat(item.getUrl()).isEqualTo("https://a.example");
        assertThat(item.getTitle()).hasValue("A");
        assertThat(item.getExtensions().getUnmappedNamespaces()).isEmpty();
    }

    @Test
    void defaults_dates_and_extensions() {
        MappingResult r = map("{\"id\":\"s\",\"path\":\"$.r[*]\",\"type\":\"news\",\"fields\":{\"url\":\"u\","
                + "\"language\":{\"from\":\"lang\",\"default\":\"de\"},"
                + "\"publishedDate\":{\"from\":\"date\",\"convert\":\"isoDate\"},"
                + "\"t:score\":{\"from\":\"score\",\"convert\":\"decimal\"},\"t:tags\":\"tags\",\"t:raw\":\"n\"}}",
                "{\"r\":[{\"u\":\"https://n.example\",\"date\":\"2024-05-01T10:00:00Z\",\"score\":\"0.5\","
                        + "\"tags\":[\"a\"],\"n\":7}]}");
        SearchResultItem item = r.getData().getResults().get(0);
        assertThat(item.getLanguage()).hasValue("de");
        assertThat(item.getPublishedDate()).hasValue(PublishedDate.of(Instant.parse("2024-05-01T10:00:00Z")));
        assertThat(item.getExtensions().get(ExtensionKey.of("t", "score", BigDecimal.class))).hasValue(new BigDecimal("0.5"));
        assertThat(item.getExtensions().get(ExtensionKey.of("t", "tags", StructuredValue.class)))
                .hasValue(JsonTrees.read("[\"a\"]"));
        assertThat(item.getExtensions().get(ExtensionKey.of("t", "raw", BigDecimal.class))).hasValue(new BigDecimal(7));
    }

    @Test
    void a_value_that_cannot_be_converted_is_left_out_with_a_warning() {
        MappingResult r = map("{\"id\":\"s\",\"path\":\"$.r[*]\",\"type\":\"organic\",\"fields\":{\"url\":\"u\","
                + "\"rankAbsolute\":\"rank\",\"area\":\"col\"}}",
                "{\"r\":[{\"u\":\"https://a.example\",\"rank\":\"first\",\"col\":\"left\"}]}");
        SearchResultItem item = r.getData().getResults().get(0);
        assertThat(item.getPosition()).isEmpty();
        assertThat(r.getWarnings()).hasSize(2).allMatch(w -> w.getCode().equals(SearchWarning.MAPPING_INCOMPLETE));
        assertThat(r.getWarnings().get(0).getMessage()).contains("rankAbsolute").contains("$.r[0]");
    }

    @Test
    void invalid_domain_values_do_not_break_mapping() {
        MappingResult r = map("{\"id\":\"s\",\"path\":\"$.r[*]\",\"type\":\"organic\",\"fields\":{\"url\":\"u\",\"rankInGroup\":\"rank\"}}",
                "{\"r\":[{\"u\":\"https://a.example\",\"rank\":0},{\"u\":\"   \"}]}");
        assertThat(r.getData().getResults()).hasSize(1);
        assertThat(r.getData().getResults().get(0).getPosition()).isEmpty();
        assertThat(r.getWarnings()).hasSize(2);
    }

    @Test
    void index_position_counts_per_type_in_polymorphic_sections() {
        MappingResult r = map("{\"id\":\"s\",\"kind\":\"elements\",\"path\":\"$.i[*]\",\"discriminator\":\"k\",\"position\":\"index\","
                + "\"discriminatorMap\":{\"o\":\"item:organic\",\"p\":\"item:paid\"},\"fields\":{\"url\":\"u\"}}",
                "{\"i\":[{\"k\":\"p\",\"u\":\"https://p1\"},{\"k\":\"o\",\"u\":\"https://o1\"},{\"k\":\"o\",\"u\":\"https://o2\"}]}");
        assertThat(r.getData().getResults()).extracting(i -> i.getPosition().get().getRankInGroup().getAsInt())
                .containsExactly(1, 1, 2);
        assertThat(r.getData().getResults()).extracting(i -> i.getId().getValue()).containsExactly("s#1", "s#2", "s#3");
    }

    @Test
    void unknown_types_are_kept_with_their_full_content() {
        MappingResult r = map("{\"id\":\"s\",\"kind\":\"items\",\"path\":\"$.i[*]\",\"discriminator\":\"k\","
                + "\"discriminatorMap\":{\"o\":\"organic\"},\"fields\":{\"url\":\"u\"}}",
                "{\"i\":[{\"k\":\"Shiny Widget!\",\"u\":\"https://w\",\"deep\":{\"x\":[1]}},{\"u\":\"https://no-type\"}]}");
        assertThat(r.getData().getResults()).isEmpty();
        assertThat(r.getData().getFeatures()).hasSize(2).allMatch(f -> f instanceof GenericSerpFeature);
        GenericSerpFeature widget = (GenericSerpFeature) r.getData().getFeatures().get(0);
        assertThat(widget.getType().getCode()).isEqualTo("shiny_widget_");
        assertThat(widget.getContent().toString()).contains("Shiny Widget!").contains("deep");
        assertThat(r.getData().getFeatures().get(1).getType().getCode()).isEqualTo("unknown");
        assertThat(r.getWarnings()).extracting(SearchWarning::getCode).containsOnly(SearchWarning.UNKNOWN_ELEMENT);
    }

    @Test
    void declarative_features_fall_back_to_generic_when_required_values_are_missing() {
        MappingResult r = map("{\"id\":\"s\",\"kind\":\"features\",\"path\":\"$.f[*]\",\"type\":\"answer_box\",\"position\":\"none\","
                + "\"fields\":{\"text\":\"answer\",\"sourceUrl\":\"src\"}}",
                "{\"f\":[{\"answer\":\"42\",\"src\":\"https://calc\"},{\"src\":\"https://empty\"}]}");
        assertThat(r.getData().getFeatures().get(0)).isInstanceOf(AnswerBoxFeature.class);
        assertThat(((AnswerBoxFeature) r.getData().getFeatures().get(0)).getSource().get().getUrl()).isEqualTo("https://calc");
        assertThat(r.getData().getFeatures().get(1)).isInstanceOf(GenericSerpFeature.class);
        assertThat(r.getWarnings()).hasSize(1);
    }

    @Test
    void keep_unmapped_false_drops_the_rest_deliberately() {
        MappingResult r = map("{\"id\":\"s\",\"path\":\"$.r[*]\",\"type\":\"organic\",\"keepUnmapped\":false,\"fields\":{\"url\":\"u\"}}",
                "{\"r\":[{\"u\":\"https://a\",\"x\":1}],\"top\":true}");
        assertThat(r.getData().getResults().get(0).getExtensions().isEmpty()).isTrue();
        assertThat(r.getMetadata().getExtensions().getUnmapped("t")).hasValue(JsonTrees.read("{\"top\":true}"));
    }

    @Test
    void failing_custom_mappers_keep_the_declarative_result_and_the_unmapped_rest() {
        MappingDefinition d = MappingDefinitions.from(JsonTrees.read("{\"id\":\"t\",\"version\":1,\"namespace\":\"t\","
                + "\"sections\":[{\"id\":\"s\",\"kind\":\"elements\",\"path\":\"$.i[*]\",\"discriminator\":\"k\","
                + "\"discriminatorMap\":{\"o\":\"item:organic\",\"kg\":\"feature:knowledge_panel\"},"
                + "\"fieldsByType\":{\"organic\":{\"url\":\"u\"},\"knowledge_panel\":{\"title\":\"t\"}}}]}"));
        MappingEngine engine = MappingEngine.builder(d)
                .provider(ProviderId.of("test"))
                .customMappers(CustomMapperRegistry.builder(d)
                        .itemMapper("s", (e, item, ctx) -> {
                            e.get("secret");
                            throw new IllegalStateException("boom");
                        })
                        .featureMapper("s", SerpFeatureType.KNOWLEDGE_PANEL, (e, seed, ctx) -> {
                            e.get("facts");
                            throw new IllegalStateException("bang");
                        })
                        .build())
                .build();
        MappingResult r = engine.map(JsonTrees.read("{\"i\":[{\"k\":\"o\",\"u\":\"https://a\",\"secret\":1},"
                + "{\"k\":\"kg\",\"t\":\"Panel\",\"facts\":[1]}]}"));

        assertThat(r.getData().getResults().get(0).getExtensions().getUnmapped("t")).hasValue(JsonTrees.read("{\"secret\":1}"));
        KnowledgePanelFeature panel = r.getData().getFeatures(KnowledgePanelFeature.class).get(0);
        assertThat(panel.getTitle()).isEqualTo("Panel");
        assertThat(panel.getExtensions().getUnmapped("t")).hasValue(JsonTrees.read("{\"facts\":[1]}"));
        assertThat(r.getWarnings()).hasSize(2).allMatch(w -> w.getProvider().equals(java.util.Optional.of(ProviderId.of("test"))));
    }

    @Test
    void metadata_mapper_and_transfer_to_search_metadata() {
        MappingDefinition d = MappingDefinitions.from(JsonTrees.read("{\"id\":\"t\",\"version\":1,\"namespace\":\"t\","
                + "\"metadata\":{\"originalQuery\":\"q\",\"providerRequestId\":\"id\",\"creditsUsed\":\"credits\"}}"));
        MappingEngine engine = MappingEngine.builder(d)
                .customMappers(CustomMapperRegistry.builder(d)
                        .metadataMapper((root, m, ctx) -> m.cost(root.get("price.amount").flatMap(StructuredValue::asNumber).orElse(null))
                                .currency(root.text("price.currency").orElse(null)))
                        .build())
                .build();
        MappingResult r = engine.map(JsonTrees.read("{\"q\":\"java\",\"id\":\"r-1\",\"credits\":3,"
                + "\"price\":{\"amount\":0.01,\"currency\":\"eur\"},\"extra\":1}"));

        SearchMetadata metadata = r.applyTo(SearchMetadata.forProvider(ProviderId.of("test"), SearchEngineId.of("google"),
                SearchVertical.WEB, r.getMetadata().toQueryInfo("fallback"))).build();
        assertThat(metadata.getQuery().getOriginalQuery()).isEqualTo("java");
        assertThat(metadata.getExecution().get().getProviderRequestId()).hasValue("r-1");
        assertThat(metadata.getUsage().get().getCreditsUsed()).hasValue(3L);
        assertThat(metadata.getUsage().get().getCost().get().getCurrency()).isEqualTo("EUR");
        assertThat(metadata.getExtensions().getUnmapped("t")).hasValue(JsonTrees.read("{\"extra\":1}"));
    }

    @Test
    void any_response_shape_is_accepted() {
        MappingEngine e = engine("{\"id\":\"s\",\"path\":\"$.r[*]\",\"type\":\"organic\",\"fields\":{\"url\":\"u\"}}");
        assertThat(e.map(JsonTrees.read("[1,2]")).getData().isEmpty()).isTrue();
        assertThat(e.map(StructuredValue.of("text")).getData().isEmpty()).isTrue();
        assertThat(e.map(JsonTrees.read("{\"r\":{\"u\":\"not a list\"}}")).getData().isEmpty()).isTrue();
        assertThat(e.map(JsonTrees.read("{\"r\":[null,\"x\",[1]]}")).getWarnings()).hasSize(2);
    }

    @Test
    void unknown_converters_are_rejected_when_binding() {
        MappingDefinition d = MappingDefinitions.from(JsonTrees.read("{\"id\":\"t\",\"version\":1,\"namespace\":\"t\","
                + "\"sections\":[{\"id\":\"s\",\"path\":\"$.r[*]\",\"type\":\"organic\",\"fields\":{\"url\":{\"from\":\"u\",\"convert\":\"rot13\"}}}]}"));
        assertThatThrownBy(() -> MappingEngine.of(d)).isInstanceOf(InvalidMappingDefinitionException.class)
                .hasMessageContaining("unknown converter 'rot13'");
    }
}
