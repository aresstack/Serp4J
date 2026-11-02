package com.aresstack.serp4j.provider.dataforseo;

import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.credential.EnvironmentCredentialProvider;
import com.aresstack.serp4j.http.ahc.AhcHttpTransport;
import com.aresstack.serp4j.http.resolve.GoogleGotoUrlResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real call against the DataForSEO SERP API (Google organic, live/advanced). Runs only with
 * {@code -Pserp4j.live=true} and the environment variables {@code SERP4J_DATAFORSEO_LOGIN} and
 * {@code SERP4J_DATAFORSEO_PASSWORD} (sent only as Basic auth header):
 *
 * <pre>SERP4J_DATAFORSEO_LOGIN=… SERP4J_DATAFORSEO_PASSWORD=… \
 *   ./gradlew :serp4j-provider-dataforseo:liveTest -Pserp4j.live=true -Pserp4j.recordFixtures=true</pre>
 *
 * With {@code recordFixtures} the raw answer is written to {@code build/recorded-fixtures/dataforseo/} together
 * with a sidecar marked {@code anonymized=false}. Check and anonymize it by hand (the response echoes the request
 * and contains task ids and cost) before moving it to {@code src/test/resources/fixtures/dataforseo/} (E-1).
 * Each run costs a small amount of DataForSEO credit.
 */
class DataForSeoLiveTest {

    @Test
    void googleOrganicLiveAdvancedReturnsResults() throws Exception {
        assumeTrue(Boolean.getBoolean("serp4j.live"), "live tests are off");
        assumeTrue(System.getenv("SERP4J_DATAFORSEO_LOGIN") != null, "SERP4J_DATAFORSEO_LOGIN is not set");
        assumeTrue(System.getenv("SERP4J_DATAFORSEO_PASSWORD") != null, "SERP4J_DATAFORSEO_PASSWORD is not set");
        EnvironmentCredentialProvider credentials = EnvironmentCredentialProvider.systemEnvironment();

        try (AhcHttpTransport transport = AhcHttpTransport.create();
             DataForSeoSearchProvider provider = DataForSeoSearchProvider.builder(transport, credentials)
                     .function(DataForSeoFunction.ADVANCED)
                     .build()) {
            SearchRequest request = SearchRequest.builder("serp4j java search library")
                    .engine(SearchEngineId.GOOGLE)
                    .locale(SearchLocale.of("DE", "de"))
                    .limit(10)
                    .rawPayloadPolicy(RawPayloadPolicy.ALWAYS)
                    .build();
            SearchResponse response = provider.search(request, SearchContext.defaults()).get(120, TimeUnit.SECONDS);
            if (Boolean.getBoolean("serp4j.recordFixtures")) {
                record(response.getMetadata().get().getRawPayload().get().getBodyAsText());
            }
            assertThat(response.getData().getResults()).isNotEmpty();
            // Google /goto redirects are resolved by default: every result either has a direct URL or, when the
            // resolution failed, keeps its /goto URL and the search carries the url_resolution_failed warning.
            boolean unresolved = false;
            for (com.aresstack.serp4j.core.model.data.SearchResultItem item : response.getData().getResults()) {
                if (GoogleGotoUrlResolver.isGoogleGotoUrl(item.getUrl())) {
                    unresolved = true;
                } else if (item.getExtensions().contains(DataForSeoResultUrlEnricher.ORIGINAL_URL)) {
                    assertThat(item.getUrl()).startsWith("http");
                    assertThat(item.getUrl()).doesNotContain("google.com/goto");
                }
            }
            if (unresolved) {
                assertThat(response.getMetadata().get().getWarnings()).extracting(w -> w.getCode())
                        .contains(DataForSeoResultUrlEnricher.WARNING_CODE);
            }
        }
    }

    /**
     * Resolves one real Google {@code /goto} URL without DataForSEO credentials (no credit used). Runs only with
     * {@code -Pserp4j.live=true} and {@code SERP4J_GOOGLE_GOTO_URL} set to a current {@code /goto} URL, e.g. the
     * confirmed Elastic redirect; {@code SERP4J_GOOGLE_GOTO_TARGET} optionally names the expected target.
     */
    @Test
    void googleGotoUrlResolvesToItsTarget() throws Exception {
        assumeTrue(Boolean.getBoolean("serp4j.live"), "live tests are off");
        String gotoUrl = System.getenv("SERP4J_GOOGLE_GOTO_URL");
        assumeTrue(gotoUrl != null && !gotoUrl.trim().isEmpty(), "SERP4J_GOOGLE_GOTO_URL is not set");
        try (AhcHttpTransport transport = AhcHttpTransport.create()) {
            String target = new GoogleGotoUrlResolver(transport).resolve(gotoUrl, java.time.Duration.ofSeconds(3))
                    .get(10, TimeUnit.SECONDS);
            assertThat(target).startsWith("http").doesNotContain("google.com/goto");
            String expected = System.getenv("SERP4J_GOOGLE_GOTO_TARGET");
            if (expected != null && !expected.trim().isEmpty()) {
                assertThat(target).isEqualTo(expected.trim());
            }
        }
    }

    private static void record(String body) throws IOException {
        Path dir = Paths.get("build", "recorded-fixtures", "dataforseo");
        Files.createDirectories(dir);
        Files.write(dir.resolve("google-organic-live-advanced-de.json"), body.getBytes(StandardCharsets.UTF_8));
        String sidecar = "provenance=recorded\n"
                + "source=POST https://api.dataforseo.com/v3/serp/google/organic/live/advanced (DE, de, depth=10)\n"
                + "recordedAt=" + Instant.now() + "\n"
                + "anonymized=false\n";
        Files.write(dir.resolve("google-organic-live-advanced-de.json.fixture.properties"),
                sidecar.getBytes(StandardCharsets.UTF_8));
    }
}
