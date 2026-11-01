package com.aresstack.serp4j.json;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.math.BigDecimal;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonTreesTest {

    @Test
    void reads_every_json_type_and_keeps_member_order() throws Exception {
        StructuredValue v = JsonTrees.read("{\"z\":1,\"a\":[true,false,null,\"x\"],\"m\":{\"n\":-2.5e3}}");
        assertThat(v.getMembers().keySet()).containsExactly("z", "a", "m");
        assertThat(v.get("a").get().getElements()).containsExactly(StructuredValue.of(true), StructuredValue.of(false),
                StructuredValue.nullValue(), StructuredValue.of("x"));
        assertThat(v.path("m", "n").get().asNumber().get()).isEqualByComparingTo(new BigDecimal("-2500"));
        assertThat(JsonTrees.read(new StringReader("[]"))).isEqualTo(StructuredValue.ofArray(java.util.Collections.<StructuredValue>emptyList()));
        assertThat(JsonTrees.read("\"just text\"")).isEqualTo(StructuredValue.of("just text"));
    }

    @Test
    void numbers_keep_their_exact_value() {
        String big = "123456789012345678901234567890.000000000000000000001";
        assertThat(JsonTrees.read(big).asNumber()).hasValue(new BigDecimal(big));
        assertThat(JsonTrees.read("0.1").asNumber()).hasValue(new BigDecimal("0.1"));
    }

    @Test
    void duplicate_member_names_last_one_wins() {
        assertThat(JsonTrees.read("{\"a\":1,\"a\":2}").get("a")).hasValue(StructuredValue.of(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{\"a\":1,}", "[1 2]", "{'a':1}", "{a:1}", "// c\n{}", "NaN", "[1] [2]", "{} x",
            "\"unterminated", "tru"})
    void invalid_json_is_rejected(String json) {
        assertThatThrownBy(() -> JsonTrees.read(json)).isInstanceOf(JsonTreeException.class);
    }

    @Test
    void writes_compact_and_pretty_json_round_trip() {
        String json = "{\"text\":\"a \\\"quote\\\" <tag> & ümlaut\\n\",\"n\":[1,1.5,-0.001],\"b\":false,\"x\":null,\"o\":{}}";
        StructuredValue v = JsonTrees.read(json);
        assertThat(JsonTrees.write(v)).isEqualTo(json);
        assertThat(JsonTrees.read(JsonTrees.writePretty(v))).isEqualTo(v);
        assertThat(JsonTrees.writePretty(v)).contains("\n  \"n\": [");
    }

    @Test
    void reads_utf8_resources() {
        StructuredValue v = JsonTrees.readResource(JsonTreesTest.class, "/json/sample.json");
        assertThat(v.get("hello").get().get(0)).hasValue(StructuredValue.of("wörld"));
        assertThatThrownBy(() -> JsonTrees.readResource(JsonTreesTest.class, "/json/missing.json"))
                .isInstanceOf(JsonTreeException.class).hasMessageContaining("not found");
    }

    @Test
    void gson_stays_internal() {
        JavaClasses classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.aresstack.serp4j.json");
        noClasses().that().resideOutsideOfPackage("..json.internal..")
                .should().dependOnClassesThat(resideInAnyPackage("com.google.gson.."))
                .because("Gson is an implementation detail of serp4j-json (ADR-019 §1)")
                .check(classes);
    }
}
