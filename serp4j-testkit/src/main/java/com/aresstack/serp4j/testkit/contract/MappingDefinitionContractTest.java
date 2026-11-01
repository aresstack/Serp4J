package com.aresstack.serp4j.testkit.contract;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.mapping.MappingDefinition;
import com.aresstack.serp4j.mapping.MappingEngine;
import com.aresstack.serp4j.mapping.verify.MappingDefinitionVerifier;
import com.aresstack.serp4j.mapping.verify.MappingSnapshot;
import com.aresstack.serp4j.testkit.fixture.Fixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contract for a declarative mapping definition (ADR-019 §5, architecture.md §5.6 principle 6): the definition loads
 * and its engine builds, every section path, source path and discriminator value hits in at least one fixture (dead
 * mappings show up), an element of unknown type survives as generic feature, and the mapped output of every fixture
 * equals its golden file. The checks themselves are the JUnit-free {@code com.aresstack.serp4j.mapping.verify}
 * building blocks; this class only binds them to fixtures.
 *
 * <p>A provider module extends it once per definition:
 *
 * <pre>{@code
 * class DataForSeoWebMappingContractTest extends MappingDefinitionContractTest {
 *     protected MappingEngine engine() { return DataForSeoMapping.webEngine(); }
 *     protected List<Fixture> fixtures() { return Fixtures.list("dataforseo"); }
 *     protected StructuredValue tree(Fixture fixture) { return JsonTrees.read(fixture.getText()); }
 * }
 * }</pre>
 *
 * <p>Fixtures must be recorded provider answers (decision E-1); synthetic fixtures are rejected unless
 * {@link #allowSyntheticFixtures()} is overridden, which is meant for the testkit's own tests only. Golden files lie
 * on the test classpath at {@link #goldenResource(Fixture)}; with the environment variable
 * {@code SERP4J_UPDATE_GOLDEN=1} ({@link #updateGoldenFiles()}) missing or outdated golden files are written below {@link #goldenSourceDirectory()}
 * instead of failing.
 *
 * <p>{@code serp4j-mapping} is a {@code compileOnly} dependency of the testkit: modules using this contract already
 * depend on it, all others never load this class.
 */
public abstract class MappingDefinitionContractTest {

    /** Environment variable that switches golden files to update mode. */
    public static final String UPDATE_GOLDEN = "SERP4J_UPDATE_GOLDEN";

    /** The engine under test, built exactly as the provider builds it (definition, converters, custom mappers). */
    protected abstract MappingEngine engine();

    /** Fixtures the definition is checked against, typically {@code Fixtures.list("<provider>")}; not empty. */
    protected abstract List<Fixture> fixtures();

    /** Parses a fixture into a tree, typically {@code JsonTrees.read(fixture.getText())}. */
    protected abstract StructuredValue tree(Fixture fixture);

    /** Whether synthetic fixtures are accepted; default {@code false} (E-1). Only for the testkit's own tests. */
    protected boolean allowSyntheticFixtures() {
        return false;
    }

    /** Classpath resource of the golden file of a fixture: {@code golden/<definition id>/<provider>/<name>.snapshot.json}. */
    protected String goldenResource(Fixture fixture) {
        return "golden/" + engine().getDefinition().getId() + "/" + fixture.getProvider() + "/" + fixture.getName()
                + ".snapshot.json";
    }

    /** Directory golden files are written to in update mode; default {@code src/test/resources}. */
    protected Path goldenSourceDirectory() {
        return Paths.get("src", "test", "resources");
    }

    /** Whether missing or outdated golden files are written instead of failing; default {@code SERP4J_UPDATE_GOLDEN=1}. */
    protected boolean updateGoldenFiles() {
        return "1".equals(System.getenv(UPDATE_GOLDEN));
    }

    /** Class loader golden files are read from; default the context class loader. */
    protected ClassLoader classLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : getClass().getClassLoader();
    }

    @Test
    @DisplayName("the definition loads, the engine builds and there are fixtures for it")
    void definitionLoadsAndHasFixtures() {
        MappingEngine engine = engine();
        if (engine == null) {
            throw new AssertionError("engine() returned null");
        }
        trees();
    }

    @Test
    @DisplayName("every section path, source path and discriminator value hits in at least one fixture")
    void everyPathHitsInAFixture() {
        MappingDefinition definition = engine().getDefinition();
        MappingDefinitionVerifier.CoverageReport report =
                MappingDefinitionVerifier.coverage(definition, new ArrayList<StructuredValue>(trees().values()));
        if (!report.isComplete()) {
            throw new AssertionError("definition " + definition.getId() + " v" + definition.getVersion()
                    + " has mappings that hit in none of " + report.getFixtureCount() + " fixture(s): "
                    + report.getUnmatched());
        }
    }

    @Test
    @DisplayName("an element of unknown type survives as generic feature with a warning")
    void unknownTypeSurvives() {
        MappingEngine engine = engine();
        List<String> problems = new ArrayList<String>();
        for (Map.Entry<Fixture, StructuredValue> e : trees().entrySet()) {
            MappingDefinitionVerifier.UnknownTypeReport report =
                    MappingDefinitionVerifier.verifyUnknownTypeSurvives(engine, e.getValue());
            if (!report.isOk()) {
                problems.add(e.getKey().getResourcePath() + ": " + report.getProblems());
            }
        }
        if (!problems.isEmpty()) {
            throw new AssertionError("unknown element types are lost: " + problems);
        }
    }

    @Test
    @DisplayName("the mapped output of every fixture equals its golden file")
    void outputMatchesGoldenFiles() {
        MappingEngine engine = engine();
        boolean update = updateGoldenFiles();
        List<String> problems = new ArrayList<String>();
        for (Map.Entry<Fixture, StructuredValue> e : trees().entrySet()) {
            String resource = goldenResource(e.getKey());
            String actual = MappingSnapshot.render(engine.map(e.getValue()));
            String expected = readResource(resource);
            if (expected != null && normalize(expected).equals(normalize(actual))) {
                continue;
            }
            if (update) {
                write(goldenSourceDirectory().resolve(resource), actual);
            } else if (expected == null) {
                problems.add(resource + " is missing (run with " + UPDATE_GOLDEN + "=1 to create it); actual output:\n"
                        + actual);
            } else {
                problems.add(resource + " differs from the mapped output of " + e.getKey().getResourcePath()
                        + " (run with " + UPDATE_GOLDEN + "=1 to accept it); actual output:\n" + actual);
            }
        }
        if (!problems.isEmpty()) {
            throw new AssertionError(String.join("\n\n", problems));
        }
    }

    private Map<Fixture, StructuredValue> trees() {
        List<Fixture> fixtures = fixtures();
        if (fixtures == null || fixtures.isEmpty()) {
            throw new AssertionError("no fixtures for definition " + engine().getDefinition().getId()
                    + "; definitions are written against recorded provider answers (decision E-1)");
        }
        Map<Fixture, StructuredValue> trees = new LinkedHashMap<Fixture, StructuredValue>();
        for (Fixture fixture : fixtures) {
            if (!fixture.isRecorded() && !allowSyntheticFixtures()) {
                throw new AssertionError(fixture.getResourcePath() + " is synthetic; mapping definitions are checked"
                        + " against recorded provider answers only (decision E-1)");
            }
            StructuredValue tree = tree(fixture);
            if (tree == null) {
                throw new AssertionError("tree() returned null for " + fixture.getResourcePath());
            }
            trees.put(fixture, tree);
        }
        return trees;
    }

    private String readResource(String resource) {
        InputStream in = classLoader().getResourceAsStream(resource);
        if (in == null) {
            return null;
        }
        try {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new AssertionError("cannot read " + resource + ": " + e.getMessage(), e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            OutputStream out = Files.newOutputStream(file);
            try {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new AssertionError("cannot write golden file " + file + ": " + e.getMessage(), e);
        }
    }

    /** Line endings and a trailing newline do not count (golden files are edited and checked out on any OS). */
    private static String normalize(String text) {
        String s = text.replace("\r\n", "\n");
        while (s.endsWith("\n")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
