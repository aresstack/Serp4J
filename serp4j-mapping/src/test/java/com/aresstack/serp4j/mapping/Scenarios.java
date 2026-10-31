package com.aresstack.serp4j.mapping;

import com.aresstack.serp4j.core.model.data.SerpArea;
import com.aresstack.serp4j.core.model.data.SerpPosition;
import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.core.model.feature.Fact;
import com.aresstack.serp4j.core.model.feature.KnowledgePanelFeature;
import com.aresstack.serp4j.core.model.feature.QuestionAnswer;
import com.aresstack.serp4j.core.model.feature.QuestionAnswerFeature;
import com.aresstack.serp4j.core.model.feature.SerpFeatureType;
import com.aresstack.serp4j.core.model.feature.SourceReference;
import com.aresstack.serp4j.json.JsonTrees;
import com.aresstack.serp4j.mapping.convert.ValueConverters;
import com.aresstack.serp4j.mapping.custom.CustomMapperRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/** Loads the synthetic scenarios under {@code src/test/resources/scenarios} (see the README there). */
final class Scenarios {

    private Scenarios() {
    }

    static StructuredValue json(String scenario, String file) {
        return JsonTrees.readResource(Scenarios.class, "/scenarios/" + scenario + "/" + file);
    }

    static MappingDefinition definition(String scenario) {
        return MappingDefinitions.from(json(scenario, "definition.json"));
    }

    static StructuredValue response(String scenario) {
        return json(scenario, "response.json");
    }

    /** Engine of a scenario; scenario c gets its three custom mappers and the {@code serpArea} converter. */
    static MappingEngine engine(String scenario) {
        MappingDefinition definition = definition(scenario);
        if (!scenario.equals("c")) {
            return MappingEngine.of(definition);
        }
        return MappingEngine.builder(definition)
                .converters(ValueConverters.standard().toBuilder()
                        .register("serpArea", v -> "right".equals(v.asText().orElse(null)) ? "sidebar" : "main")
                        .build())
                .customMappers(scenarioCMappers(definition))
                .build();
    }

    /** The three custom mappers the polymorphic scenario needs (ADR-019 prototype findings). */
    static CustomMapperRegistry scenarioCMappers(MappingDefinition definition) {
        return CustomMapperRegistry.builder(definition)
                // Page area and a flag with meaning: more than a field name, so Java.
                .itemMapper("serp", (element, item, ctx) -> {
                    Optional<StructuredValue> position = element.get("position");
                    SerpPosition.Builder pos = item.getPosition().map(SerpPosition::toBuilder).orElse(SerpPosition.builder());
                    if (position.isPresent()) {
                        Object area = ctx.convert("serpArea", position.get());
                        pos.area(SerpArea.valueOf(String.valueOf(area).toUpperCase(java.util.Locale.ROOT)));
                    }
                    Optional<StructuredValue> featured = element.get("is_featured_snippet");
                    com.aresstack.serp4j.core.model.ext.Extensions ext = item.getExtensions();
                    if (featured.isPresent() && featured.get().asBoolean().orElse(false)) {
                        ext = ext.with(ctx.extensionKey("featuredSnippetSource", Boolean.class), Boolean.TRUE);
                    }
                    return item.toBuilder().position(pos.build()).extensions(ext).build();
                })
                // Knowledge graph: nested rows become facts; rows of other types stay unmapped.
                .featureMapper("serp", SerpFeatureType.KNOWLEDGE_PANEL, (element, seed, ctx) -> {
                    KnowledgePanelFeature.Builder b = seed.applyTo(
                            KnowledgePanelFeature.builder(seed.getId(), element.text("title").orElse("?")));
                    element.text("description").ifPresent(b::description);
                    for (SourceElement row : element.elements("items[*]")) {
                        if ("knowledge_graph_row_item".equals(row.getValue().get("type").flatMap(StructuredValue::asText).orElse(null))) {
                            row.consume("type");
                            b.fact(Fact.of(row.text("title").orElse("?"), row.text("text").orElse("")));
                        }
                    }
                    element.consume("type");
                    return b.build();
                })
                // People also ask: questions with the answer source from the nested expanded element.
                .featureMapper("serp", SerpFeatureType.PEOPLE_ALSO_ASK, (element, seed, ctx) -> {
                    QuestionAnswerFeature.Builder b = seed.applyTo(
                            QuestionAnswerFeature.builder(seed.getId(), SerpFeatureType.PEOPLE_ALSO_ASK));
                    for (SourceElement q : element.elements("items[*]")) {
                        q.consume("type");
                        String question = q.text("title").orElse(null);
                        if (question == null) {
                            continue;
                        }
                        String answer = q.text("expanded_element[0].description").orElse(null);
                        Optional<String> url = q.text("expanded_element[0].url");
                        b.question(url.isPresent() ? QuestionAnswer.of(question, answer, SourceReference.of(url.get()))
                                : QuestionAnswer.of(question, answer));
                    }
                    return b.build();
                })
                .build();
    }

    /** Golden file of a scenario; with environment variable SERP4J_UPDATE_GOLDEN=1 it is (re)written. */
    static String expected(String scenario, String actual) throws IOException {
        Path file = Paths.get("src/test/resources/scenarios", scenario, "expected.json");
        if ("1".equals(System.getenv("SERP4J_UPDATE_GOLDEN"))) {
            Files.write(file, actual.getBytes(StandardCharsets.UTF_8));
        }
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
