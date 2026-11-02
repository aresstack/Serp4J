package com.aresstack.serp4j.provider.argos;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

class ArgosSearchOutputParserTest {

    static String syntheticFixture() {
        try (InputStream in = ArgosSearchOutputParserTest.class
                .getResourceAsStream("/fixtures/argos/synthetic-web-search.txt")) {
            Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void readsHitsHostsAndAttemptsOfTheSyntheticFixture() {
        ArgosSearchOutput output = ArgosSearchOutputParser.parse(syntheticFixture());

        assertThat(output.getProviderHosts()).containsExactly("html.duckduckgo.com", "www.bing.com");
        assertThat(output.getItems()).hasSize(3);
        assertThat(output.getItems().get(0).getId()).isEqualTo("1");
        assertThat(output.getItems().get(0).getTitle()).isEqualTo("Synthetic result one");
        assertThat(output.getItems().get(0).getUrl()).isEqualTo("https://example.invalid/argos/1");
        assertThat(output.getItems().get(0).getSnippet()).isEqualTo("First synthetic snippet.");
        assertThat(output.getItems().get(1).getTitle()).isEqualTo("Synthetic — title with dash");
        assertThat(output.getItems().get(1).getUrl()).isEqualTo("https://example.invalid/argos/2");
        assertThat(output.getItems().get(2).getSnippet()).isEmpty();
        assertThat(output.getAttempts()).extracting(ArgosSearchOutput.Attempt::toString)
                .containsExactly("html.duckduckgo.com ORGANIC_RESULTS", "www.bing.com EXTRACTION_FAILED");
        assertThat(output.getChallenges()).isEmpty();
        assertThat(output.getRawText()).startsWith("# SYNTHETIC");
    }

    @Test
    void noResultsIsEmpty() {
        ArgosSearchOutput output = ArgosSearchOutputParser.parse("No results.");

        assertThat(output.getItems()).isEmpty();
        assertThat(output.getAttempts()).isEmpty();
    }

    @Test
    void readsChallengesAndToleratesUnknownLinesAndCrLf() {
        ArgosSearchOutput output = ArgosSearchOutputParser.parse(
                "PROVIDER: www.bing.com\r\nSOMETHING_NEW: ignored\r\nCHALLENGE: bing.com captcha\r\n"
                        + "ATTEMPT: www.bing.com CHALLENGE\r\n");

        assertThat(output.getItems()).isEmpty();
        assertThat(output.getChallenges()).containsExactly("bing.com captcha");
        assertThat(output.getAttempts()).hasSize(1);
        assertThat(output.getAttempts().get(0).isOrganicResults()).isFalse();
    }

    @Test
    void nullIsEmpty() {
        assertThat(ArgosSearchOutputParser.parse(null).getItems()).isEmpty();
    }
}
