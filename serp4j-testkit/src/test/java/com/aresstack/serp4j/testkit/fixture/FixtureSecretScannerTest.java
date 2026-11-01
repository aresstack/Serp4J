package com.aresstack.serp4j.testkit.fixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixtureSecretScannerTest {

    private final FixtureSecretScanner scanner = FixtureSecretScanner.defaults();

    @ParameterizedTest
    @ValueSource(strings = {
            "Authorization: Bearer abcdef1234567890abcdef",
            "\"authorization\": \"Basic dXNlcjpwYXNzd29yZA==\"",
            "{\"api_key\": \"sk_live_9f8e7d6c5b4a\"}",
            "X-Subscription-Token: BSAx9f8e7d6c5b4a3210",
            "\"password\":\"hunter2hunter2\"",
            "client_secret=Zm9vYmFyYmF6cXV4",
            "https://api.example.invalid/search?q=x&key=AIzaSyA1234567890abcdefgh",
            "-----BEGIN RSA PRIVATE KEY-----",
            "AKIAIOSFODNN7EXAMPLE",
            "ghp_abcdefghijklmnopqrstuvwxyz0123456789AB",
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
    })
    void detectsSecrets(String line) {
        assertThat(scanner.scan("test", "{\n" + line + "\n}")).isNotEmpty()
                .allSatisfy(finding -> assertThat(finding.getLine()).isEqualTo(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Authorization: Bearer ****",
            "\"api_key\": \"REDACTED\"",
            "\"api_key\": \"<api-key>\"",
            "\"password\": \"${DATAFORSEO_PASSWORD}\"",
            "\"password\": null",
            "\"password\": \"\"",
            "\"secret\": false",
            "\"next_page_token\": \"CAoQAA\"",
            "\"keyword\": \"serp4j api key tutorial\"",
            "\"title\": \"How to rotate your API key\"",
            "\"status_code\": 20000, \"tasks_count\": 1",
            "https://example.invalid/search?q=serp4j&hl=de"
    })
    void acceptsPlaceholdersAndOrdinaryContent(String line) {
        assertThat(scanner.scan("test", line)).isEmpty();
    }

    @Test
    void findsCredentialValuesOnTheLineAfterTheirName() {
        String json = "{\n  \"task\": 1,\n  \"api_key\":\n    \"sk_live_9f8e7d6c5b4a\",\n"
                + "  \"Authorization\":\n    \"Bearer abcdef1234567890abcdef\"\n}";

        List<SecretFinding> findings = scanner.scan("pretty", json);

        assertThat(findings).extracting(SecretFinding::getRule)
                .containsExactly("credential-field", "authorization-header");
        assertThat(findings).extracting(SecretFinding::getLine).containsExactly(4, 6);
        assertThat(findings.toString()).doesNotContain("9f8e7d6c5b4a").doesNotContain("1234567890abcdef");
    }

    @Test
    void emptyPropertiesValueDoesNotReachIntoTheNextLine() {
        assertThat(scanner.scan("props", "token=\nkeyword=serp4j tutorial\npassword:\nnested: value")).isEmpty();
    }

    @Test
    void forbiddenValuesAreFoundAnywhereAndNeverPrinted() {
        String key = "live-recording-key-0815";
        List<SecretFinding> findings = scanner.forbidding(key).scan("rec", "{\"note\": \"" + key + "\"}");

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRule()).isEqualTo("forbidden-value");
        assertThat(findings.get(0).toString()).doesNotContain(key);
    }

    @Test
    void assertCleanListsFindingsWithMaskedValues() {
        Fixture fixture = new Fixture("acme", "leak.json", "{\"api_key\": \"sk_live_9f8e7d6c5b4a\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8), FixtureProvenance.SYNTHETIC, null, null, false,
                "test", "application/json", 200);

        assertThatThrownBy(() -> scanner.assertClean(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("fixtures/acme/leak.json:1")
                .hasMessageContaining("credential-field")
                .hasMessageNotContaining("sk_live_9f8e7d6c5b4a");
    }
}
