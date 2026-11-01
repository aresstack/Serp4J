package com.aresstack.serp4j.testkit.fixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixturesTest {

    @TempDir
    Path root;

    private void write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private URLClassLoader classLoader() throws IOException {
        return new URLClassLoader(new URL[]{root.toUri().toURL()}, null);
    }

    @Test
    void loadsSyntheticFixtureWithProvenance() {
        Fixture fixture = Fixtures.load("testkit-synthetic", "search-ok.json");

        assertThat(fixture.isSynthetic()).isTrue();
        assertThat(fixture.getDescription()).isPresent();
        assertThat(fixture.getContentType()).startsWith("application/json");
        assertThat(fixture.getStatus()).isEqualTo(200);
        assertThat(fixture.getText()).contains("Synthetic result 1");
        assertThat(fixture.toHttpResponse().getContentType()).contains("application/json; charset=utf-8");
        assertThat(fixture.getResourcePath()).isEqualTo("fixtures/testkit-synthetic/search-ok.json");
    }

    @Test
    void sidecarStatusIsUsed() {
        assertThat(Fixtures.load("testkit-synthetic", "task-pending.json").toHttpResponse().getStatus())
                .isEqualTo(202);
    }

    @Test
    void loadRecordedRefusesSyntheticData() {
        assertThatThrownBy(() -> Fixtures.loadRecorded("testkit-synthetic", "search-ok.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("E-1");
    }

    @Test
    void recordedFixtureNeedsSourceAndAnonymization() throws IOException {
        write("fixtures/acme/ok.json", "{}");
        write("fixtures/acme/ok.json.fixture.properties",
                "provenance=recorded\nsource=ACME playground\nanonymized=true\nrecordedAt=2026-10-01\n");
        write("fixtures/acme/raw.json", "{}");
        write("fixtures/acme/raw.json.fixture.properties", "provenance=recorded\nsource=ACME playground\n");
        write("fixtures/acme/nosource.json", "{}");
        write("fixtures/acme/nosource.json.fixture.properties", "provenance=recorded\nanonymized=true\n");

        try (URLClassLoader loader = classLoader()) {
            Fixture ok = Fixtures.load("acme", "ok.json", loader);
            assertThat(ok.isRecorded()).isTrue();
            assertThat(ok.getSource()).contains("ACME playground");
            assertThat(ok.getRecordedAt()).contains("2026-10-01");
            assertThatThrownBy(() -> Fixtures.load("acme", "raw.json", loader)).hasMessageContaining("anonymized");
            assertThatThrownBy(() -> Fixtures.load("acme", "nosource.json", loader)).hasMessageContaining("source");
        }
    }

    @Test
    void sidecarExampleFromTheReadmeLoadsAsWritten() throws IOException {
        String readme = new String(Files.readAllBytes(java.nio.file.Paths.get("README.md")), StandardCharsets.UTF_8);
        int start = readme.indexOf("```properties\n") + "```properties\n".length();
        String example = readme.substring(start, readme.indexOf("```", start));
        write("fixtures/acme/example.json", "{}");
        write("fixtures/acme/example.json.fixture.properties", example);

        try (URLClassLoader loader = classLoader()) {
            Fixture fixture = Fixtures.load("acme", "example.json", loader);
            assertThat(fixture.getProvenance()).isEqualTo(FixtureProvenance.RECORDED);
            assertThat(fixture.getRecordedAt()).contains("2026-10-12");
            assertThat(fixture.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void fixtureWithoutSidecarOrProvenanceIsRejected() throws IOException {
        write("fixtures/acme/orphan.json", "{}");
        write("fixtures/acme/bad.json", "{}");
        write("fixtures/acme/bad.json.fixture.properties", "provenance=guessed\n");
        write("fixtures/acme/synthetic.json", "{}");
        write("fixtures/acme/synthetic.json.fixture.properties", "provenance=synthetic\n");

        try (URLClassLoader loader = classLoader()) {
            assertThatThrownBy(() -> Fixtures.load("acme", "orphan.json", loader)).hasMessageContaining("sidecar");
            assertThatThrownBy(() -> Fixtures.load("acme", "bad.json", loader))
                    .hasMessageContaining("recorded or synthetic");
            assertThatThrownBy(() -> Fixtures.load("acme", "synthetic.json", loader))
                    .hasMessageContaining("description");
            assertThatThrownBy(() -> Fixtures.load("acme", "missing.json", loader)).hasMessageContaining("not found");
        }
    }

    @Test
    void catalogReportsLayoutProblems() throws IOException {
        write("fixtures/acme/ok.json", "{}");
        write("fixtures/acme/ok.json.fixture.properties", "provenance=synthetic\ndescription=test\n");
        write("fixtures/acme/orphan.json", "{}");
        write("fixtures/acme/lonely.json.fixture.properties", "provenance=synthetic\ndescription=test\n");
        write("fixtures/acme/deep/nested.json", "{}");
        write("fixtures/README.md", "ignored");

        try (URLClassLoader loader = classLoader()) {
            FixtureCatalog catalog = Fixtures.catalog(loader);

            assertThat(catalog.getFixtures()).extracting(Fixture::getName).containsExactly("ok.json");
            assertThat(catalog.getProblems()).hasSize(3)
                    .anySatisfy(problem -> assertThat(problem).contains("orphan.json").contains("sidecar"))
                    .anySatisfy(problem -> assertThat(problem).contains("lonely.json").contains("without fixture"))
                    .anySatisfy(problem -> assertThat(problem).contains("deep/nested.json"));
            assertThatThrownBy(catalog::assertValid).isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void catalogReadsJars(@TempDir Path jarDir) throws Exception {
        Path jar = jarDir.resolve("fixtures.jar");
        try (java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
            // directory entries as Gradle's jar task writes them; ClassLoader.getResources needs them
            out.putNextEntry(new java.util.jar.JarEntry("fixtures/"));
            out.putNextEntry(new java.util.jar.JarEntry("fixtures/acme/"));
            out.putNextEntry(new java.util.jar.JarEntry("fixtures/acme/ok.json"));
            out.write("{}".getBytes(StandardCharsets.UTF_8));
            out.putNextEntry(new java.util.jar.JarEntry("fixtures/acme/ok.json.fixture.properties"));
            out.write("provenance=synthetic\ndescription=jar test\n".getBytes(StandardCharsets.UTF_8));
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            FixtureCatalog catalog = Fixtures.catalog(loader);
            assertThat(catalog.getProblems()).isEmpty();
            assertThat(catalog.getFixtures()).extracting(Fixture::getResourcePath)
                    .containsExactly("fixtures/acme/ok.json");
        }
    }

    @Test
    void listReturnsTheFixturesOfOneProvider() {
        assertThat(Fixtures.list("testkit-synthetic")).extracting(Fixture::getName)
                .containsExactly("search-ok.json", "task-pending.json");
    }

    @Test
    void pathTraversalIsRejected() {
        assertThatThrownBy(() -> Fixtures.load("..", "x.json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Fixtures.load("acme", "../x.json")).isInstanceOf(IllegalArgumentException.class);
    }
}
