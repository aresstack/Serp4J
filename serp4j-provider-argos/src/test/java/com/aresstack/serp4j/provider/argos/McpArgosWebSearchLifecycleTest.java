package com.aresstack.serp4j.provider.argos;

import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.testkit.http.FakeHttpStep;
import com.aresstack.serp4j.testkit.http.FakeHttpTransport;
import com.aresstack.serp4j.testkit.http.RequestMatcher;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cancellation, close and deadline reach the HTTP request (ADR-017). The sidecar answers are synthetic. */
class McpArgosWebSearchLifecycleTest {

    private static final String ENDPOINT = "http://127.0.0.1:47001/mcp/browser/synthetic-token";

    static RequestMatcher rpc(final String method) {
        return RequestMatcher.of(request -> request.hasBody()
                && new String(request.getBody(), StandardCharsets.UTF_8).contains("\"method\":\"" + method + "\""),
                "JSON-RPC " + method);
    }

    private static FakeHttpTransport sidecarHangingOnToolCall() {
        FakeHttpTransport transport = FakeHttpTransport.create();
        transport.on(rpc("tools/call")).reply(FakeHttpStep.hang());
        transport.on(RequestMatcher.any()).reply(FakeHttpStep.json(200, ScriptedTransport.initResult(1)));
        return transport;
    }

    private static void awaitRunning(FakeHttpTransport transport, int count) throws InterruptedException {
        for (int i = 0; i < 200 && transport.getRunningCount() < count; i++) {
            Thread.sleep(5);
        }
        assertThat(transport.getRunningCount()).isEqualTo(count);
    }

    @Test
    void cancellingTheSearchCancelsTheHttpRequest() throws Exception {
        FakeHttpTransport transport = sidecarHangingOnToolCall();
        CompletableFuture<ArgosSearchOutput> search = McpArgosWebSearch.builder(ENDPOINT, transport).build()
                .search("q", Duration.ofMinutes(1));
        awaitRunning(transport, 1);

        search.cancel(true);

        assertThat(transport.getRunningCount()).as("tools/call request is cancelled").isZero();
    }

    @Test
    void closeAbortsRunningSearchesAndTheirRequests() throws Exception {
        FakeHttpTransport transport = sidecarHangingOnToolCall();
        McpArgosWebSearch adapter = McpArgosWebSearch.builder(ENDPOINT, transport).build();
        CompletableFuture<ArgosSearchOutput> search = adapter.search("q", Duration.ofMinutes(1));
        awaitRunning(transport, 1);

        adapter.close();

        assertThatThrownBy(search::join).isInstanceOf(CompletionException.class)
                .satisfies(e -> assertThat(((HttpTransportException) e.getCause()).getKind())
                        .isEqualTo(HttpTransportException.Kind.CANCELLED));
        assertThat(transport.getRunningCount()).isZero();
        assertThat(transport.isClosed()).isFalse();
    }

    @Test
    void aSecondCallerKeepsItsDeadlineWhileTheSharedInitializationHangs() throws Exception {
        FakeHttpTransport transport = FakeHttpTransport.create();
        transport.on(RequestMatcher.any()).reply(FakeHttpStep.hang());
        McpArgosWebSearch adapter = McpArgosWebSearch.builder(ENDPOINT, transport).build();
        CompletableFuture<ArgosSearchOutput> first = adapter.search("first", Duration.ofMinutes(1));
        awaitRunning(transport, 1);

        long started = System.nanoTime();
        CompletableFuture<ArgosSearchOutput> second = adapter.search("second", Duration.ofMillis(100));

        assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS))
                .satisfies(e -> assertThat(((HttpTransportException) e.getCause()).getKind())
                        .isEqualTo(HttpTransportException.Kind.TIMEOUT));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
        assertThat(first.isDone()).as("the first caller's wait is unaffected").isFalse();
        assertThat(transport.getRunningCount()).as("shared initialization keeps running").isEqualTo(1);
        adapter.close();
    }

    @Test
    void aShortFirstDeadlineDoesNotShortenTheSharedInitializationForALaterCaller() {
        FakeHttpTransport transport = FakeHttpTransport.create();
        transport.on(rpc("tools/call")).reply(FakeHttpStep.json(200,
                ScriptedTransport.toolResult(0, "1: Synthetic \u2014 https://example.invalid/a\n", false)));
        transport.on(RequestMatcher.any()).reply(
                FakeHttpStep.json(200, ScriptedTransport.initResult(0)).after(Duration.ofMillis(300)));
        McpArgosWebSearch adapter = McpArgosWebSearch.builder(ENDPOINT, transport).build();

        CompletableFuture<ArgosSearchOutput> shortCall = adapter.search("short", Duration.ofMillis(100));
        CompletableFuture<ArgosSearchOutput> longCall = adapter.search("long", Duration.ofSeconds(5));

        assertThatThrownBy(shortCall::join)
                .satisfies(e -> assertThat(((HttpTransportException) e.getCause()).getKind())
                        .isEqualTo(HttpTransportException.Kind.TIMEOUT));
        assertThat(longCall.join().getItems()).hasSize(1);
        adapter.close();
    }

    @Test
    void closeAbortsARunningSharedInitialization() throws Exception {
        FakeHttpTransport transport = FakeHttpTransport.create();
        transport.on(RequestMatcher.any()).reply(FakeHttpStep.hang());
        McpArgosWebSearch adapter = McpArgosWebSearch.builder(ENDPOINT, transport).build();
        CompletableFuture<ArgosSearchOutput> search = adapter.search("q", Duration.ofMinutes(1));
        awaitRunning(transport, 1);

        adapter.close();

        assertThatThrownBy(search::join).isInstanceOf(CompletionException.class);
        assertThat(transport.getRunningCount()).as("initialize request is cancelled").isZero();
    }
}
