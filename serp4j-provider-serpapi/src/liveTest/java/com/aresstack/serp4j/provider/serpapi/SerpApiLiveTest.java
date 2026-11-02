package com.aresstack.serp4j.provider.serpapi;

import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.SearchEngineId;
import com.aresstack.serp4j.core.port.SearchContext;
import com.aresstack.serp4j.core.port.credential.EnvironmentCredentialProvider;
import com.aresstack.serp4j.http.ahc.AhcHttpTransport;
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
 * Real calls against SerpApi (Google and Bing web search). Runs only with {@code -Pserp4j.live=true} and the
 * environment variable {@code SERP4J_SERPAPI_API_KEY}:
 *
 * <pre>SERP4J_SERPAPI_API_KEY=… ./gradlew :serp4j-provider-serpapi:liveTest -Pserp4j.live=true -Pserp4j.recordFixtures=true</pre>
 *
 * Each uncached run spends one search per test from the monthly quota (free plan: 250 per month, 50 per hour);
 * repeating the run within an hour is served from SerpApi's cache and costs nothing. With {@code recordFixtures}
 * the raw answers are written to {@code build/recorded-fixtures/serpapi/} together with a sidecar marked
 * {@code anonymized=false}. Check and anonymize them by hand (search ids, endpoint URLs) before moving them to
 * {@code src/test/resources/fixtures/serpapi/} (E-1). The key is sent as {@code api_key} URL parameter and never
 * written to the recording.
 */
class SerpApiLiveTest {

    @Test
    void googleWebSearchReturnsResults() throws Exception {
        run(SearchEngineId.GOOGLE, "google-web-de.json");
    }

    @Test
    void bingWebSearchReturnsResults() throws Exception {
        run(SearchEngineId.BING, "bing-web-de.json");
    }

    private static void run(SearchEngineId engine, String fixtureName) throws Exception {
        assumeTrue(Boolean.getBoolean("serp4j.live"), "live tests are off");
        assumeTrue(System.getenv("SERP4J_SERPAPI_API_KEY") != null, "SERP4J_SERPAPI_API_KEY is not set");
        EnvironmentCredentialProvider credentials = EnvironmentCredentialProvider.systemEnvironment();

        try (AhcHttpTransport transport = AhcHttpTransport.create();
             SerpApiSearchProvider provider = SerpApiSearchProvider.builder().transport(transport)
                     .credentials(credentials).build()) {
            SearchRequest request = SearchRequest.builder("serp4j java search library")
                    .engine(engine)
                    .locale(SearchLocale.of("DE", "de"))
                    .limit(10)
                    .rawPayloadPolicy(RawPayloadPolicy.ALWAYS)
                    .build();
            SearchResponse response = provider.search(request, SearchContext.defaults()).get(120, TimeUnit.SECONDS);
            String body = response.getMetadata().get().getRawPayload().get().getBodyAsText();
            if (Boolean.getBoolean("serp4j.recordFixtures")) {
                record(engine, fixtureName, body);
            }
            assertThat(body).doesNotContain(System.getenv("SERP4J_SERPAPI_API_KEY").trim());
            assertThat(response.getData().getResults()).isNotEmpty();
        }
    }

    private static void record(SearchEngineId engine, String name, String body) throws IOException {
        Path dir = Paths.get("build", "recorded-fixtures", "serpapi");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), body.getBytes(StandardCharsets.UTF_8));
        String sidecar = "provenance=recorded\n"
                + "source=GET https://serpapi.com/search.json (engine=" + engine.getCode() + ", DE, de, 10 results)\n"
                + "recordedAt=" + Instant.now() + "\n"
                + "anonymized=false\n";
        Files.write(dir.resolve(name + ".fixture.properties"), sidecar.getBytes(StandardCharsets.UTF_8));
    }
}
