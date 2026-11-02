package com.aresstack.serp4j.playwright;

import com.aresstack.serp4j.core.error.SearchErrorCategory;
import com.aresstack.serp4j.core.error.SearchProviderException;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.SearchResponse;
import com.aresstack.serp4j.core.model.ext.RawPayloadPolicy;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.port.SearchContext;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-020 with a real browser (only with {@code -Pserp4j.browserTests=true}): the test plays the host. It starts
 * Playwright and Chromium (executable from {@code SERP4J_BROWSER_EXECUTABLE} or Playwright's lookup), passes the
 * {@link Browser} to a {@link LocalPageSearchProvider} per call and closes the browser itself at the end.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaywrightSearchProviderLiveTest {

    private static final ProviderId ID = ProviderId.of("local-test");

    private HttpServer server;
    private String baseUrl;
    private Playwright playwright;
    private Browser browser;
    private LocalPageSearchProvider provider;

    @BeforeAll
    void startHostInfrastructure() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String q = query == null ? "" : URLDecoder.decode(query.replaceFirst("^q=", ""),
                    StandardCharsets.UTF_8);
            String body;
            if (q.equals("consent")) {
                body = "<html><body><div id=\"consent\">Before you continue</div></body></html>";
            } else if (q.equals("slow")) {
                try {
                    Thread.sleep(3_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                body = "<html><body><ul id=\"results\"></ul></body></html>";
            } else {
                StringBuilder b = new StringBuilder("<html><head><title>" + q + "</title></head><body><ul id=\"results\">");
                for (int i = 1; i <= 3; i++) {
                    b.append("<li class=\"result\"><a href=\"http://example.invalid/").append(q).append('/')
                            .append(i).append("\">Result ").append(i).append(" for ").append(q)
                            .append("</a><p>Snippet ").append(i).append("</p></li>");
                }
                body = b.append("</ul></body></html>").toString();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(Collections.singletonMap("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        BrowserType.LaunchOptions launch = new BrowserType.LaunchOptions().setHeadless(true);
        String executable = System.getenv("SERP4J_BROWSER_EXECUTABLE");
        if (executable != null && !executable.isEmpty()) {
            Path path = Paths.get(executable);
            if (Files.isRegularFile(path)) {
                launch.setExecutablePath(path);
            }
        }
        browser = playwright.chromium().launch(launch);
        provider = new LocalPageSearchProvider(ID, baseUrl);
    }

    @AfterAll
    void stopHostInfrastructure() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void searchesOnThePassedBrowserAndLeavesItOpen() {
        SearchRequest request = SearchRequest.builder("serp4j").limit(2)
                .rawPayloadPolicy(RawPayloadPolicy.ALWAYS).build();
        SearchResponse response = provider.search(request, browser);

        assertThat(response.getData().getOrganicResults()).hasSize(2);
        assertThat(response.getData().getOrganicResults().get(0).getUrl()).isEqualTo("http://example.invalid/serp4j/1");
        assertThat(response.getData().getOrganicResults().get(0).getTitle()).contains("Result 1 for serp4j");
        assertThat(response.getMetadata()).isPresent();
        assertThat(response.getMetadata().get().getProvider()).contains(ID);
        assertThat(response.getMetadata().get().getEngine()).contains(LocalPageSearchProvider.ENGINE);
        assertThat(response.getMetadata().get().getRawPayload()).isPresent();
        assertThat(response.getMetadata().get().getExecution().get().getSourceUrl().get()).startsWith(baseUrl);
        assertThat(browser.isConnected()).isTrue();
        assertThat(browser.contexts()).as("the provider leaves no page or context behind").isEmpty();

        // a second call with the same browser instance works the same way
        assertThat(provider.search(request, browser).getData().size()).isEqualTo(2);
    }

    @Test
    void obstacleIsReportedNotWorkedAround() {
        assertThatThrownBy(() -> provider.search(SearchRequest.builder("consent").build(), browser))
                .isInstanceOf(SearchProviderException.class)
                .satisfies(e -> {
                    SearchProviderException failure = (SearchProviderException) e;
                    assertThat(failure.getCategory()).isEqualTo(SearchErrorCategory.BLOCKED);
                    assertThat(failure.getDetails()).containsEntry(PlaywrightSearchFailures.DETAIL_OBSTACLE, "consent");
                });
        assertThat(browser.contexts()).isEmpty();
    }

    @Test
    void deadlineBoundsTheSearch() {
        SearchContext context = SearchContext.builder().timeout(Instant.now(), Duration.ofMillis(500)).build();
        assertThatThrownBy(() -> provider.search(SearchRequest.builder("slow").build(), context, browser))
                .isInstanceOf(SearchProviderException.class)
                .extracting(e -> ((SearchProviderException) e).getCategory())
                .isEqualTo(SearchErrorCategory.TIMEOUT);
        assertThat(browser.contexts()).isEmpty();
    }

    @Test
    void closedBrowserFailsAsUnavailableAndIsTheHostsToRestart() {
        Browser own = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true)
                .setExecutablePath(executablePathOrNull()));
        own.close();
        assertThatThrownBy(() -> provider.search(SearchRequest.builder("serp4j").build(), own))
                .isInstanceOf(SearchProviderException.class)
                .extracting(e -> ((SearchProviderException) e).getCategory())
                .isEqualTo(SearchErrorCategory.UNAVAILABLE);
    }

    private static Path executablePathOrNull() {
        String executable = System.getenv("SERP4J_BROWSER_EXECUTABLE");
        return executable == null || executable.isEmpty() || !Files.isRegularFile(Paths.get(executable)) ? null
                : Paths.get(executable);
    }
}
