package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.data.SearchResultItem;
import com.aresstack.serp4j.core.model.ext.ExtensionKey;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.custom.CustomMapperRegistry;
import com.aresstack.serp4j.mapping.custom.FeatureMapper;
import com.aresstack.serp4j.mapping.custom.ItemMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test required by Angelo for package MP (ADR-019 §3, architecture.md §5.6 rule 3): custom mappers belong
 * to exactly one definition. Two definitions use the <em>same</em> section id ({@code serp}) and the <em>same</em>
 * feature type id ({@code knowledge_panel}); the custom mappers of one must never influence the other.
 *
 * <p>Background: the prototype first shared one {@code CustomMappers} instance across definitions, so a mapper
 * written for one provider's knowledge graph also ran on another provider's info box.</p>
 */
class CustomMapperIsolationTest {

    private static final String RESPONSE = "{\"items\":["
            + "{\"type\":\"organic\",\"url\":\"https://example.com\",\"title\":\"Example\"},"
            + "{\"type\":\"knowledge_graph\",\"title\":\"Example Corp\",\"rows\":[{\"k\":\"Founded\",\"v\":\"1999\"}]}"
            + "]}";

    /** Identical structure, different id and namespace – exactly the situation of two providers. */
    private static MappingDefinition definition(String id, String namespace) {
        return MappingDefinitions.from(JsonTrees.read("{"
                + "\"id\":\"" + id + "\",\"version\":1,\"namespace\":\"" + namespace + "\","
                + "\"sections\":[{\"id\":\"serp\",\"kind\":\"elements\",\"path\":\"$.items[*]\",\"discriminator\":\"type\","
                + "\"discriminatorMap\":{\"organic\":\"item:organic\",\"knowledge_graph\":\"feature:knowledge_panel\"},"
                + "\"fieldsByType\":{\"organic\":{\"url\":\"url\",\"title\":\"title\"},"
                + "\"knowledge_panel\":{\"title\":\"title\"}}}]}"));
    }

    private final MappingDefinition x = definition("isolation-x", "x");
    private final MappingDefinition y = definition("isolation-y", "y");
    private final StructuredValue response = JsonTrees.read(RESPONSE);

    private final AtomicInteger xFeatureCalls = new AtomicInteger();
    private final AtomicInteger xItemCalls = new AtomicInteger();

    private final FeatureMapper xKnowledgeGraph = (element, seed, ctx) -> {
        xFeatureCalls.incrementAndGet();
        element.consume("rows");
        return seed.applyTo(KnowledgePanelFeature.builder(seed.getId(), "from x mapper")).build();
    };

    private final ItemMapper xItems = (element, item, ctx) -> {
        xItemCalls.incrementAndGet();
        return item.toBuilder().extensions(item.getExtensions()
                .with(ExtensionKey.of("x", "marker", String.class), "x was here")).build();
    };

    private CustomMapperRegistry xRegistry() {
        return CustomMapperRegistry.builder(x)
                .featureMapper("serp", SerpFeatureType.KNOWLEDGE_PANEL, xKnowledgeGraph)
                .itemMapper("serp", xItems)
                .build();
    }

    @Test
    void same_section_and_feature_type_ids_do_not_influence_each_other() {
        MappingEngine engineX = MappingEngine.builder(x).customMappers(xRegistry()).build();
        MappingEngine engineY = MappingEngine.of(y);

        MappingResult fromX = engineX.map(response);
        MappingResult fromY = engineY.map(response);

        // X: its own mappers ran.
        assertThat(fromX.getData().getFeatures(KnowledgePanelFeature.class).get(0).getTitle()).isEqualTo("from x mapper");
        assertThat(fromX.getData().getResults().get(0).getExtensions().get(ExtensionKey.of("x", "marker", String.class)))
                .hasValue("x was here");
        assertThat(xFeatureCalls.get()).isEqualTo(1);
        assertThat(xItemCalls.get()).isEqualTo(1);

        // Y: same section id "serp", same feature type "knowledge_panel" – but only its declarative mapping.
        KnowledgePanelFeature panelY = fromY.getData().getFeatures(KnowledgePanelFeature.class).get(0);
        assertThat(panelY.getTitle()).isEqualTo("Example Corp");
        assertThat(panelY.getExtensions().getUnmapped("y")).hasValueSatisfying(v -> assertThat(v.get("rows")).isPresent());
        SearchResultItem itemY = fromY.getData().getResults().get(0);
        assertThat(itemY.getExtensions().getKeys()).isEmpty();
        assertThat(xFeatureCalls.get()).as("x's feature mapper must not run for y").isEqualTo(1);
        assertThat(xItemCalls.get()).as("x's item mapper must not run for y").isEqualTo(1);
    }

    @Test
    void each_definition_keeps_its_own_mapper_for_the_same_ids() {
        CustomMapperRegistry yRegistry = CustomMapperRegistry.builder(y)
                .featureMapper("serp", SerpFeatureType.KNOWLEDGE_PANEL,
                        (element, seed, ctx) -> seed.applyTo(KnowledgePanelFeature.builder(seed.getId(), "from y mapper")).build())
                .build();
        MappingEngine engineX = MappingEngine.builder(x).customMappers(xRegistry()).build();
        MappingEngine engineY = MappingEngine.builder(y).customMappers(yRegistry).build();

        // Interleaved use must not matter either.
        MappingResult y1 = engineY.map(response);
        MappingResult x1 = engineX.map(response);
        MappingResult y2 = engineY.map(response);

        assertThat(x1.getData().getFeatures(KnowledgePanelFeature.class).get(0).getTitle()).isEqualTo("from x mapper");
        assertThat(y1.getData().getFeatures(KnowledgePanelFeature.class).get(0).getTitle()).isEqualTo("from y mapper");
        assertThat(y2.getData().getFeatures(KnowledgePanelFeature.class).get(0).getTitle()).isEqualTo("from y mapper");
        assertThat(xFeatureCalls.get()).isEqualTo(1);
    }

    @Test
    void a_registry_cannot_be_used_with_another_definition() {
        CustomMapperRegistry registryOfX = xRegistry();

        assertThatThrownBy(() -> MappingEngine.builder(y).customMappers(registryOfX).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isolation-x")
                .hasMessageContaining("isolation-y");
        assertThat(registryOfX.belongsTo(x)).isTrue();
        assertThat(registryOfX.belongsTo(y)).isFalse();
    }

    @Test
    void registering_for_one_definition_later_does_not_change_an_existing_engine() {
        MappingEngine engineY = MappingEngine.of(y);
        MappingResult before = engineY.map(response);

        CustomMapperRegistry.builder(y)
                .featureMapper("serp", SerpFeatureType.KNOWLEDGE_PANEL, xKnowledgeGraph)
                .build();
        xRegistry();

        assertThat(engineY.map(response).getData()).isEqualTo(before.getData());
        assertThat(xFeatureCalls.get()).isZero();
    }

    @Test
    void a_definition_loaded_twice_is_the_same_definition() {
        // Value equality: the same resource read twice may share a registry – it is the same mapping.
        MappingDefinition xAgain = definition("isolation-x", "x");
        MappingEngine engine = MappingEngine.builder(xAgain).customMappers(xRegistry()).build();
        assertThat(engine.map(response).getData().getFeatures(KnowledgePanelFeature.class).get(0).getTitle())
                .isEqualTo("from x mapper");
    }

    @Test
    void registration_is_checked_against_the_definition() {
        assertThatThrownBy(() -> CustomMapperRegistry.builder(x).itemMapper("other", xItems))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no section 'other'");
        assertThatThrownBy(() -> CustomMapperRegistry.builder(x)
                .featureMapper("serp", SerpFeatureType.PEOPLE_ALSO_ASK, xKnowledgeGraph))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("people_also_ask");
        assertThatThrownBy(() -> CustomMapperRegistry.builder(x).itemMapper("serp", xItems).itemMapper("serp", xItems))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already");
    }
}
