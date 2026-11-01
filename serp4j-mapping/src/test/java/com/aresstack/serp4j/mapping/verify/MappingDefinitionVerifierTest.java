package com.aresstack.serp4j.mapping.verify;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.MappingDefinition;
import com.aresstack.serp4j.mapping.MappingDefinitions;
import com.aresstack.serp4j.mapping.MappingEngine;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class MappingDefinitionVerifierTest {

    private static StructuredValue res(String scenario, String file) {
        return JsonTrees.readResource(MappingDefinitionVerifierTest.class, "/scenarios/" + scenario + "/" + file);
    }

    @Test
    void reports_paths_that_match_in_no_fixture() {
        MappingDefinition d = MappingDefinitions.from(res("a", "definition.json"));
        MappingDefinitionVerifier.CoverageReport report =
                MappingDefinitionVerifier.coverage(d, Collections.singletonList(res("a", "response.json")));
        // The alternatives "name", "url", "description" and "rank" exist for the other structure only.
        assertThat(report.isComplete()).isFalse();
        assertThat(report.getUnmatched()).containsExactlyInAnyOrder(
                "section 'organic': field 'title' source 'name' matches in no fixture",
                "section 'organic': field 'url' source 'url' matches in no fixture",
                "section 'organic': field 'snippet' source 'description' matches in no fixture",
                "section 'organic': field 'rankInGroup' source 'rank' matches in no fixture",
                "section 'organic': field 'domain' source 'url' matches in no fixture");
    }

    @Test
    void complete_coverage_and_dead_discriminator_values() {
        MappingDefinition d = MappingDefinitions.from(JsonTrees.read("{\"id\":\"t\",\"version\":1,\"namespace\":\"t\","
                + "\"metadata\":{\"originalQuery\":\"q\"},"
                + "\"sections\":[{\"id\":\"s\",\"kind\":\"elements\",\"path\":\"$.i[*]\",\"discriminator\":\"k\","
                + "\"discriminatorMap\":{\"o\":\"item:organic\",\"gone\":\"item:news\"},"
                + "\"fieldsByType\":{\"organic\":{\"url\":\"u\"},\"news\":{\"url\":\"link\"}}}]}"));
        StructuredValue one = JsonTrees.read("{\"q\":\"x\",\"i\":[{\"k\":\"o\",\"u\":\"https://a\"}]}");
        StructuredValue two = JsonTrees.read("{\"i\":[{\"k\":\"o\",\"link\":\"https://not-news\"}]}");

        MappingDefinitionVerifier.CoverageReport report = MappingDefinitionVerifier.coverage(d, Arrays.asList(one, two));

        // "link" exists, but only on an organic element – it does not count for type news.
        assertThat(report.getUnmatched()).containsExactly(
                "section 's': discriminator value 'gone' occurs in no fixture",
                "section 's': field 'url' source 'link' matches in no fixture (type news)");
        assertThat(report.getFixtureCount()).isEqualTo(2);
    }

    @Test
    void the_same_source_is_tracked_per_type() {
        MappingDefinition d = MappingDefinitions.from(JsonTrees.read("{\"id\":\"t\",\"version\":1,\"namespace\":\"t\","
                + "\"sections\":[{\"id\":\"s\",\"kind\":\"elements\",\"path\":\"$.i[*]\",\"discriminator\":\"k\","
                + "\"discriminatorMap\":{\"o\":\"item:organic\",\"p\":\"item:paid\"},"
                + "\"fieldsByType\":{\"organic\":{\"url\":\"u\",\"title\":\"t\"},\"paid\":{\"url\":\"u\",\"title\":\"t\"}}}]}"));
        StructuredValue fixture = JsonTrees.read("{\"i\":[{\"k\":\"o\",\"u\":\"https://a\",\"t\":\"A\"},"
                + "{\"k\":\"p\",\"u\":\"https://ad\"}]}");
        assertThat(MappingDefinitionVerifier.coverage(d, Collections.singletonList(fixture)).getUnmatched())
                .containsExactly("section 's': field 'title' source 't' matches in no fixture (type paid)");
    }

    @Test
    void unknown_type_probe_survives_in_polymorphic_sections() {
        MappingEngine engine = MappingEngine.of(MappingDefinitions.from(res("c", "definition.json")));
        MappingDefinitionVerifier.UnknownTypeReport report =
                MappingDefinitionVerifier.verifyUnknownTypeSurvives(engine, res("c", "response.json"));
        assertThat(report.isOk()).as(report.toString()).isTrue();
        assertThat(report.getProbedSections()).containsExactly("serp");
        assertThat(report.getSkippedSections()).isEmpty();
    }

    @Test
    void sections_without_a_list_are_skipped() {
        MappingEngine engine = MappingEngine.of(MappingDefinitions.from(JsonTrees.read("{\"id\":\"t\",\"version\":1,"
                + "\"namespace\":\"t\",\"sections\":[{\"id\":\"s\",\"kind\":\"features\",\"path\":\"$.box\","
                + "\"discriminator\":\"k\",\"discriminatorMap\":{\"kp\":\"knowledge_panel\"}}]}")));
        MappingDefinitionVerifier.UnknownTypeReport report =
                MappingDefinitionVerifier.verifyUnknownTypeSurvives(engine, JsonTrees.read("{\"box\":{\"k\":\"kp\"}}"));
        assertThat(report.getSkippedSections()).containsExactly("s");
        assertThat(report.isOk()).isTrue();
    }

    @Test
    void snapshot_is_deterministic() {
        MappingEngine engine = MappingEngine.of(MappingDefinitions.from(res("b", "definition.json")));
        String first = MappingSnapshot.render(engine.map(res("b", "response.json")));
        String second = MappingSnapshot.render(engine.map(res("b", "response.json")));
        assertThat(first).isEqualTo(second).endsWith("}\n").contains("\"definition\": \"synthetic-b-web\"");
    }
}
