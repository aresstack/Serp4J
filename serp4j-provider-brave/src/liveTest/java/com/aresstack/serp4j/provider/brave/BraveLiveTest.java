package com.aresstack.serp4j.provider.brave;

import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
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
 * Real call against the Brave Search API. Runs only with {@code -Pserp4j.live=true} and the environment variable
 * {@code SERP4J_BRAVE_API_KEY}:
 *
 * <pre>SERP4J_BRAVE_API_KEY=… ./gradlew :serp4j-provider-brave:liveTest -Pserp4j.live=true -Pserp4j.recordFixtures=true</pre>
 *
 * With {@code recordFixtures} the raw answer is written to {@code build/recorded-fixtures/brave/} together with a
 * sidecar marked {@code anonymized=false}. Check and anonymize it by hand before moving it to
 * {@code src/test/resources/fixtures/brave/} (E-1).
 */
class BraveLiveTest {

    @Test
    void webSearchReturnsResults() throws Exception {
        assumeTrue(Boolean.getBoolean("serp4j.live"), "live tests are off");
        EnvironmentCredentialProvider credentials = EnvironmentCredentialProvider.systemEnvironment();
        assumeTrue(System.getenv("SERP4J_BRAVE_API_KEY") != null, "SERP4J_BRAVE_API_KEY is not set");

        try (AhcHttpTransport transport = AhcHttpTransport.create();
             BraveSearchProvider provider = BraveSearchProvider.builder().transport(transport)
                     .credentials(credentials).build()) {
            SearchRequest request = SearchRequest.builder("serp4j java search library")
                    .locale(SearchLocale.builder().countryCode("DE").languageCode("de").uiLanguage("de-DE").build())
                    .limit(10)
                    .rawPayloadPolicy(RawPayloadPolicy.ALWAYS)
                    .build();
            SearchResponse response = provider.search(request, SearchContext.defaults()).get(60, TimeUnit.SECONDS);
            assertThat(response.getData().getResults()).isNotEmpty();
            if (Boolean.getBoolean("serp4j.recordFixtures")) {
                record(response.getMetadata().get().getRawPayload().get().getBodyAsText());
            }
        }
    }

    private static void record(String body) throws IOException {
        Path dir = Paths.get("build", "recorded-fixtures", "brave");
        Files.createDirectories(dir);
        Files.write(dir.resolve("web-search-de.json"), body.getBytes(StandardCharsets.UTF_8));
        String sidecar = "provenance=recorded\n"
                + "source=GET https://api.search.brave.com/res/v1/web/search (country=DE, search_lang=de, count=10)\n"
                + "recordedAt=" + Instant.now() + "\n"
                + "anonymized=false\n";
        Files.write(dir.resolve("web-search-de.json.fixture.properties"), sidecar.getBytes(StandardCharsets.UTF_8));
    }
}
