package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.json.JsonTrees;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class MappingDefinitionsTest {

    private static MappingDefinition read(String json) {
        return MappingDefinitions.from(JsonTrees.read(json));
    }

    private static InvalidMappingDefinitionException invalid(String json) {
        return catchThrowableOfType(InvalidMappingDefinitionException.class, () -> read(json));
    }

    private static String def(String sections) {
        return "{\"id\":\"t\",\"version\":2,\"namespace\":\"t\",\"sections\":[" + sections + "]}";
    }

    @Test
    void reads_all_parts() {
        MappingDefinition d = Scenarios.definition("c");
        assertThat(d.getId()).isEqualTo("synthetic-c-web");
        assertThat(d.getVersion()).isEqualTo(1);
        assertThat(d.getNamespace()).isEqualTo("synthetic-c");
        assertThat(d.getMetadata()).extracting(FieldMapping::getTarget)
                .containsExactly("originalQuery", "totalResultsEstimate", "providerRequestId", "cost", "currency");
        SectionDefinition s = d.getSection("serp").get();
        assertThat(s.getKind()).isEqualTo(SectionKind.ELEMENTS);
        assertThat(s.getDiscriminatorMap().get("knowledge_graph").toString()).isEqualTo("feature:knowledge_panel");
        assertThat(s.getSkipWhen()).containsExactly("images");
        assertThat(s.producesItems()).isTrue();
        assertThat(s.fieldsFor(s.getDiscriminatorMap().get("organic"))).extracting(FieldMapping::getTarget)
                .contains("rankInGroup", "rankAbsolute", "title", "url");
        assertThat(d).isEqualTo(Scenarios.definition("c")).hasSameHashCodeAs(Scenarios.definition("c"));
        assertThat(d).isNotEqualTo(Scenarios.definition("b"));
    }

    @Test
    void field_forms() {
        MappingDefinition d = read(def("{\"id\":\"s\",\"path\":\"$.r[*]\",\"type\":\"organic\",\"fields\":{"
                + "\"url\":\"u\",\"title\":[\"t\",\"name\"],"
                + "\"rankInGroup\":{\"from\":\"r\",\"convert\":\"int\",\"default\":1,\"required\":true}}}"));
        SectionDefinition s = d.getSections().get(0);
        assertThat(s.getKind()).isEqualTo(SectionKind.ITEMS);
        assertThat(s.getPosition()).isEqualTo(PositionMode.FIELDS);
        FieldMapping rank = s.getFields().get(2);
        assertThat(rank.getSources()).hasSize(1);
        assertThat(rank.getConverter()).hasValue("int");
        assertThat(rank.getDefault()).isPresent();
        assertThat(rank.isRequired()).isTrue();
        assertThat(s.getFields().get(1).getSources()).hasSize(2);
    }

    @Test
    void collects_all_problems_with_their_location() {
        InvalidMappingDefinitionException e = invalid("{\"id\":\"Bad Id\",\"version\":0,\"namespace\":\"t\",\"colour\":1,"
                + "\"sections\":[{\"id\":\"s\",\"path\":\"$.r[\",\"type\":\"organic\",\"fields\":{\"titel\":\"t\"}}]}");
        assertThat(e).isNotNull();
        assertThat(e.getProblems()).hasSize(5);
        assertThat(e.getProblems()).anyMatch(p -> p.startsWith("colour: unknown key"));
        assertThat(e.getProblems()).anyMatch(p -> p.startsWith("id: must match"));
        assertThat(e.getProblems()).anyMatch(p -> p.startsWith("version: must be a positive integer"));
        assertThat(e.getProblems()).anyMatch(p -> p.contains("section 's'.path") && p.contains("missing ']'"));
        assertThat(e.getProblems()).anyMatch(p -> p.contains("fields.titel: unknown target"));
    }

    @Test
    void targets_must_fit_the_element_type() {
        assertThat(invalid(def("{\"id\":\"s\",\"kind\":\"features\",\"path\":\"$.f\",\"type\":\"featured_snippet\","
                + "\"fieldsByType\":{\"featured_snippet\":{\"title\":\"t\"}}}")).getProblems())
                .anyMatch(p -> p.contains("target not supported for feature:featured_snippet"));
        // Lists into typed sub-objects are Java (ADR-019): no declarative "questions".
        assertThat(invalid(def("{\"id\":\"s\",\"kind\":\"features\",\"path\":\"$.f\",\"type\":\"people_also_ask\","
                + "\"fields\":{\"questions\":\"q\"}}")).getProblems()).isNotEmpty();
        assertThat(invalid(def("{\"id\":\"s\",\"path\":\"$.r[*]\",\"type\":\"organic\",\"fields\":{\"text\":\"t\"}}"))
                .getProblems()).isNotEmpty();
    }

    @Test
    void sections_are_validated() {
        assertThat(invalid(def("{\"id\":\"s\",\"path\":\"$.a\",\"type\":\"organic\"},{\"id\":\"s\",\"path\":\"$.b\",\"type\":\"organic\"}"))
                .getProblems()).anyMatch(p -> p.contains("duplicate section id 's'"));
        assertThat(invalid(def("{\"id\":\"s\",\"kind\":\"elements\",\"path\":\"$.a[*]\",\"discriminatorMap\":{\"x\":\"item:organic\"}}"))
                .getProblems()).anyMatch(p -> p.contains("requires a discriminator"));
        assertThat(invalid(def("{\"id\":\"s\",\"kind\":\"elements\",\"path\":\"$.a[*]\",\"discriminator\":\"type\","
                + "\"discriminatorMap\":{\"x\":\"organic\"}}")).getProblems())
                .anyMatch(p -> p.contains("'item:<code>' or 'feature:<code>'"));
        assertThat(invalid(def("{\"id\":\"s\",\"path\":\"$.a[*]\",\"type\":\"item:organic\"}")).getProblems())
                .anyMatch(p -> p.contains("only allowed in sections of kind elements"));
        assertThat(invalid(def("{\"id\":\"s\",\"path\":\"$.a[*]\"}")).getProblems())
                .anyMatch(p -> p.contains("needs a type or a discriminatorMap"));
        assertThat(invalid(def("{\"id\":\"s\",\"path\":\"$.a[*]\",\"type\":\"organic\",\"position\":\"none\","
                + "\"fields\":{\"url\":\"u\",\"rankInGroup\":\"r\"}}")).getProblems())
                .anyMatch(p -> p.contains("position fields are not allowed with position none"));
        assertThat(invalid(def("{\"id\":\"s\",\"kind\":\"elements\",\"path\":\"$.a[*]\",\"discriminator\":\"type\","
                + "\"discriminatorMap\":{\"x\":\"item:organic\"},\"fieldsByType\":{\"news\":{\"url\":\"u\"}}}")).getProblems())
                .anyMatch(p -> p.contains("produces no type 'news'"));
        assertThat(invalid(def("{\"id\":\"s\",\"path\":\"$.a[*]\",\"type\":\"organic\",\"fields\":{\"other:x\":\"u\"}}"))
                .getProblems()).anyMatch(p -> p.contains("extension namespace must be the definition namespace 't'"));
        assertThat(invalid(def("{\"id\":\"s\",\"path\":\"$.a[*]\",\"type\":\"organic\",\"fields\":{\"url\":{\"convert\":\"int\"}}}"))
                .getProblems()).anyMatch(p -> p.contains("needs 'from' or 'default'"));
    }

    @Test
    void root_must_be_an_object_with_id_version_namespace() {
        assertThatThrownBy(() -> read("[]")).isInstanceOf(InvalidMappingDefinitionException.class);
        assertThat(invalid("{}").getProblems()).containsExactly("id: missing", "version: missing", "namespace: missing");
    }
}
