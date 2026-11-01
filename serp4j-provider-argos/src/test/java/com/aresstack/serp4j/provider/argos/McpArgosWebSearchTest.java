package com.aresstack.serp4j.provider.argos;

import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** All sidecar answers here are synthetic. */
class McpArgosWebSearchTest {

    private static final String ENDPOINT = "http://127.0.0.1:47001/mcp/browser/s3cr3t-token";
    private static final String TOOL_TEXT = "PROVIDER: html.duckduckgo.com\n"
            + "1: Synthetic — https://example.invalid/a\n   snippet\n"
            + "ATTEMPT: html.duckduckgo.com ORGANIC_RESULTS\n";

    /** A synthetic sidecar: initialize → session "sess-1", notification → 202, tools/call → TOOL_TEXT. */
    private static HttpResponse sidecar(HttpRequest request) {
        String method = ScriptedTransport.method(request);
        long id = ScriptedTransport.id(request);
        if ("initialize".equals(method)) {
            return HttpResponse.builder(200).header(McpArgosWebSearch.SESSION_HEADER, "sess-1")
                    .textBody(ScriptedTransport.initResult(id), "application/json").build();
        }
        if ("notifications/initialized".equals(method)) {
            return HttpResponse.builder(202).build();
        }
        if ("tools/call".equals(method)) {
            return ScriptedTransport.json(200, ScriptedTransport.toolResult(id, TOOL_TEXT, false));
        }
        return ScriptedTransport.json(400, "{}");
    }

    @Test
    void initializesOnceAndCallsTheWebSearchTool() {
        ScriptedTransport transport = new ScriptedTransport(McpArgosWebSearchTest::sidecar);
        McpArgosWebSearch search = McpArgosWebSearch.builder(ENDPOINT, transport).build();

        ArgosSearchOutput first = search.search("synthetic query", Duration.ofSeconds(5)).join();
        search.search("second", null).join();

        assertThat(first.getItems()).hasSize(1);
        assertThat(first.getItems().get(0).getUrl()).isEqualTo("https://example.invalid/a");
        assertThat(transport.requests).extracting(ScriptedTransport::method)
                .containsExactly("initialize", "notifications/initialized", "tools/call", "tools/call");
        HttpRequest call = transport.requests.get(2);
        assertThat(call.getUrl()).isEqualTo(ENDPOINT);
        assertThat(call.getHeaders().getFirst("Mcp-Session-Id")).contains("sess-1");
        assertThat(call.getHeaders().getFirst("Accept").get()).contains("text/event-stream");
        assertThat(call.getTimeout()).contains(Duration.ofSeconds(5));
        assertThat(ScriptedTransport.body(call).path("params", "name").get().asText()).contains("web_search");
        assertThat(ScriptedTransport.body(call).path("params", "arguments", "query").get().asText())
                .contains("synthetic query");
        assertThat(transport.requests.get(3).getTimeout()).contains(McpArgosWebSearch.DEFAULT_TIMEOUT);
    }

    @Test
    void readsEventStreamAnswers() {
        ScriptedTransport transport = new ScriptedTransport(request -> {
            String method = ScriptedTransport.method(request);
            long id = ScriptedTransport.id(request);
            if ("notifications/initialized".equals(method)) {
                return HttpResponse.builder(202).build();
            }
            String json = "initialize".equals(method) ? ScriptedTransport.initResult(id)
                    : ScriptedTransport.toolResult(id, TOOL_TEXT, false);
            String stream = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n"
                    + "event: message\ndata: " + json + "\n\n";
            return HttpResponse.builder(200).textBody(stream, "text/event-stream").build();
        });

        ArgosSearchOutput output = McpArgosWebSearch.builder(ENDPOINT, transport).build().search("q", null).join();

        assertThat(output.getItems()).hasSize(1);
    }

    @Test
    void reopensTheSessionOnceWhenTheSidecarForgotIt() {
        AtomicInteger initializations = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        ScriptedTransport transport = new ScriptedTransport(request -> {
            String method = ScriptedTransport.method(request);
            if ("initialize".equals(method)) {
                int n = initializations.incrementAndGet();
                return HttpResponse.builder(200).header(McpArgosWebSearch.SESSION_HEADER, "sess-" + n)
                        .textBody(ScriptedTransport.initResult(ScriptedTransport.id(request)), "application/json")
                        .build();
            }
            if ("tools/call".equals(method) && calls.incrementAndGet() == 2) {
                return HttpResponse.builder(404).build();
            }
            return sidecar(request);
        });
        McpArgosWebSearch search = McpArgosWebSearch.builder(ENDPOINT, transport).build();

        search.search("one", null).join();
        ArgosSearchOutput second = search.search("two", null).join();

        assertThat(second.getItems()).hasSize(1);
        assertThat(initializations).hasValue(2);
        assertThat(transport.requests.get(transport.requests.size() - 1).getHeaders().getFirst("Mcp-Session-Id"))
                .contains("sess-2");
    }

    @Test
    void toolErrorsAreReportedWithoutLeakingTheToken() {
        ScriptedTransport transport = new ScriptedTransport(request -> {
            if ("tools/call".equals(ScriptedTransport.method(request))) {
                return ScriptedTransport.json(200,
                        ScriptedTransport.toolResult(ScriptedTransport.id(request), "browser runtime unavailable",
                                true));
            }
            return sidecar(request);
        });
        McpArgosWebSearch search = McpArgosWebSearch.builder(ENDPOINT, transport).build();

        assertThatThrownBy(() -> search.search("q", null).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(ArgosWebSearchException.class)
                .satisfies(e -> assertThat(((ArgosWebSearchException) e.getCause()).getKind())
                        .isEqualTo(ArgosWebSearchException.Kind.TOOL_ERROR));
        assertThat(search.toString()).doesNotContain("s3cr3t").contains("/mcp/browser/***");
    }

    @Test
    void httpErrorsCarryTheStatusButNotTheToken() {
        ScriptedTransport transport = new ScriptedTransport(request -> HttpResponse.builder(403).build());

        assertThatThrownBy(() -> McpArgosWebSearch.builder(ENDPOINT, transport).build().search("q", null).join())
                .hasCauseInstanceOf(ArgosWebSearchException.class)
                .satisfies(e -> {
                    ArgosWebSearchException cause = (ArgosWebSearchException) e.getCause();
                    assertThat(cause.getHttpStatus()).isEqualTo(403);
                    assertThat(cause.getMessage()).doesNotContain("s3cr3t");
                });
    }

    @Test
    void invalidJsonIsAProtocolError() {
        ScriptedTransport transport = new ScriptedTransport(request -> ScriptedTransport.json(200, "<html>"));

        assertThatThrownBy(() -> McpArgosWebSearch.builder(ENDPOINT, transport).build().search("q", null).join())
                .hasCauseInstanceOf(ArgosWebSearchException.class)
                .satisfies(e -> assertThat(((ArgosWebSearchException) e.getCause()).getKind())
                        .isEqualTo(ArgosWebSearchException.Kind.PROTOCOL));
    }

    @Test
    void closeLeavesTheBorrowedTransportOpen() {
        ScriptedTransport transport = new ScriptedTransport(McpArgosWebSearchTest::sidecar);
        McpArgosWebSearch search = McpArgosWebSearch.builder(ENDPOINT, transport).build();

        search.close();
        search.close();

        assertThat(transport.closed).isFalse();
        assertThatThrownBy(() -> search.search("q", null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void redactsTheLastPathSegment() {
        assertThat(McpArgosWebSearch.redact("http://127.0.0.1:1/mcp/browser/tok")).isEqualTo(
                "http://127.0.0.1:1/mcp/browser/***");
        assertThat(McpArgosWebSearch.redact("http://127.0.0.1:1")).isEqualTo("http://127.0.0.1:1");
        assertThatThrownBy(() -> McpArgosWebSearch.builder("ftp://x/tok", new ScriptedTransport(r -> null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
