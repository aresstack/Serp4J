package com.aresstack.serp4j.testkit.contract;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.MappingDefinitions;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.testkit.fixture.Fixture;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The contract must fail for the defects it exists to catch, not only pass for a good definition. */
class MappingDefinitionContractTestTest {

    private static final String DEFINITION = "{\"id\":\"testkit-synthetic-web\",\"version\":1,\"namespace\":\"testkit\","
            + "\"metadata\":{\"originalQuery\":\"$.query\"},\"sections\":[{\"id\":\"items\",\"kind\":\"elements\","
            + "\"path\":\"$.items[*]\",\"discriminator\":\"type\",\"discriminatorMap\":{\"organic\":\"item:organic\","
            + "\"panel\":\"feature:knowledge_panel\"},\"fieldsByType\":{\"organic\":{\"url\":\"link\",\"title\":\"title\","
            + "\"rankInGroup\":\"rank\"%s},\"knowledge_panel\":{\"title\":\"name\",\"description\":\"text\"}}}%s]}";

    /** Contract over the synthetic fixture with a configurable definition. */
    private static class Probe extends MappingDefinitionContractTest {
        private final String extraOrganicFields;
        private final boolean allowSynthetic;
        private final List<Fixture> fixtures;
        private final String goldenPrefix;
        private String extraSections = "";
        private Set<String> unprobeable = Collections.emptySet();

        Probe(String extraOrganicFields, boolean allowSynthetic, List<Fixture> fixtures, String goldenPrefix) {
            this.extraOrganicFields = extraOrganicFields;
            this.allowSynthetic = allowSynthetic;
            this.fixtures = fixtures;
            this.goldenPrefix = goldenPrefix;
        }

        static Probe good() {
            return new Probe("", true, Fixtures.list("testkit-mapping"), null);
        }

        @Override
        protected MappingEngine engine() {
            return MappingEngine.of(MappingDefinitions.from(JsonTrees.read(String.format(DEFINITION, extraOrganicFields, extraSections))));
        }

        @Override
        protected List<Fixture> fixtures() {
            return fixtures;
        }

        @Override
        protected StructuredValue tree(Fixture fixture) {
            return JsonTrees.read(fixture.getText());
        }

        @Override
        protected boolean allowSyntheticFixtures() {
            return allowSynthetic;
        }

        @Override
        protected Set<String> unprobeableSections() {
            return unprobeable;
        }

        @Override
        protected boolean updateGoldenFiles() {
            return false; // negative cases must never write golden files
        }

        @Override
        protected String goldenResource(Fixture fixture) {
            return goldenPrefix == null ? super.goldenResource(fixture) : goldenPrefix + fixture.getName();
        }
    }

    @Test
    void a_good_definition_passes_every_check() {
        Probe probe = Probe.good();
        assertThatCode(() -> {
            probe.definitionLoadsAndHasFixtures();
            probe.everyPathHitsInAFixture();
            probe.unknownTypeSurvives();
            probe.outputMatchesGoldenFiles();
        }).doesNotThrowAnyException();
    }

    @Test
    void a_dead_source_path_is_reported() {
        Probe probe = new Probe(",\"snippet\":\"no_such_field\"", true, Fixtures.list("testkit-mapping"), null);
        assertThatThrownBy(probe::everyPathHitsInAFixture)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no_such_field");
    }

    @Test
    void synthetic_fixtures_are_rejected_by_default() {
        Probe probe = new Probe("", false, Fixtures.list("testkit-mapping"), null);
        assertThatThrownBy(probe::definitionLoadsAndHasFixtures)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("E-1");
    }

    @Test
    void a_definition_without_fixtures_fails() {
        Probe probe = new Probe("", true, Collections.<Fixture>emptyList(), null);
        assertThatThrownBy(probe::definitionLoadsAndHasFixtures)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no fixtures");
    }

    @Test
    void a_discriminator_section_that_was_never_probed_fails_unless_declared() {
        Probe probe = Probe.good();
        probe.extraSections = ",{\"id\":\"single\",\"kind\":\"features\",\"path\":\"$.items[1]\","
                + "\"discriminator\":\"type\",\"discriminatorMap\":{\"panel\":\"knowledge_panel\"},"
                + "\"fields\":{\"title\":\"name\"}}";
        assertThatThrownBy(probe::unknownTypeSurvives)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("[single]");

        probe.unprobeable = Collections.singleton("single");
        assertThatCode(probe::unknownTypeSurvives).doesNotThrowAnyException();
    }

    @Test
    void a_missing_golden_file_fails_with_the_actual_output() {
        Probe probe = new Probe("", true, Fixtures.list("testkit-mapping"), "golden/missing/");
        assertThatThrownBy(probe::outputMatchesGoldenFiles)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("golden/missing/web.json is missing")
                .hasMessageContaining("Serp4J");
    }

    @Test
    void a_changed_mapping_differs_from_its_golden_file() {
        // same golden file, but the definition now also maps the organic "extra" flag into an extension
        Probe probe = new Probe(",\"testkit:extra\":\"extra\"", true, Fixtures.list("testkit-mapping"), null);
        assertThatThrownBy(probe::outputMatchesGoldenFiles)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("differs");
    }
}
