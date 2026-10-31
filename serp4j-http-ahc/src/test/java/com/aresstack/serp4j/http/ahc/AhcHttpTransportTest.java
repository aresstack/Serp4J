package com.aresstack.serp4j.http.ahc;

import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.http.HttpTransportException.Kind;
import com.aresstack.serp4j.http.RequestAuthentication;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class AhcHttpTransportTest {

    private static final String SECRET = "s3cr3t-value-123";

    private LocalHttpServer server;
    private AhcHttpTransport transport;

    @BeforeEach
    void start() throws Exception {
        server = new LocalHttpServer();
        transport = AhcHttpTransport.create(AhcHttpTransportOptions.builder()
                .requestTimeout(Duration.ofSeconds(10))
                .build());
    }

    @AfterEach
    void stop() throws Exception {
        transport.close();
        server.close();
    }

    @Test
    void deliversStatusHeadersAndBodyAndSendsDefaultUserAgent() throws Exception {
        server.on("/search", exchange -> {
            exchange.getResponseHeaders().add("X-Rate-Limit", "10");
            exchange.getResponseHeaders().add("X-Rate-Limit", "20");
            LocalHttpServer.respond(exchange, 200, "application/json; charset=UTF-8", "{\"ok\":\"ä\"}");
        });

        HttpResponse response = transport.execute(HttpRequest.get(server.url("/search?q=java%208&lang=de"))
                .header("Accept", "application/json")
                .header("X-Multi", "a")
                .header("X-Multi", "b")
                .build()).get(10, TimeUnit.SECONDS);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getBodyAsText()).isEqualTo("{\"ok\":\"ä\"}");
        assertThat(response.getContentType()).contains("application/json; charset=UTF-8");
        assertThat(response.getHeaders().getAll("x-rate-limit")).containsExactly("10", "20");

        LocalHttpServer.Recorded seen = server.lastRequest();
        assertThat(seen.method).isEqualTo("GET");
        assertThat(seen.uri).isEqualTo("/search?q=java%208&lang=de");
        assertThat(seen.header("User-Agent")).isEqualTo(AhcHttpTransportOptions.DEFAULT_USER_AGENT);
        assertThat(seen.header("Accept")).isEqualTo("application/json");
        assertThat(seen.headerValues("X-Multi")).containsExactly("a", "b");
    }

    @Test
    void defaultUserAgentNamesSerp4jAndModuleVersion() {
        assertThat(AhcHttpTransportOptions.DEFAULT_USER_AGENT).matches("Serp4J/\\d+\\.\\d+\\.\\d+.*");
    }

    @Test
    void requestUserAgentWinsOverDefault() throws Exception {
        server.on("/", exchange -> LocalHttpServer.respond(exchange, 204, null, ""));

        transport.execute(HttpRequest.get(server.url("/")).header("User-Agent", "MyHost/2").build())
                .get(10, TimeUnit.SECONDS);

        assertThat(server.lastRequest().headerValues("User-Agent")).containsExactly("MyHost/2");
    }

    @Test
    void sendsBodyAndContentType() throws Exception {
        server.on("/post", exchange -> LocalHttpServer.respond(exchange, 201, "text/plain", "created"));

        HttpResponse response = transport.execute(HttpRequest.post(server.url("/post"))
                .jsonBody("[{\"keyword\":\"äöü\"}]")
                .build()).get(10, TimeUnit.SECONDS);

        assertThat(response.getStatus()).isEqualTo(201);
        LocalHttpServer.Recorded seen = server.lastRequest();
        assertThat(seen.method).isEqualTo("POST");
        assertThat(seen.header("Content-Type")).isEqualTo("application/json; charset=UTF-8");
        assertThat(seen.bodyText()).isEqualTo("[{\"keyword\":\"äöü\"}]");
    }

    @Test
    void errorStatusesAreResponsesNotExceptions() throws Exception {
        server.on("/401", exchange -> LocalHttpServer.respond(exchange, 401, "application/json", "{\"e\":1}"));
        server.on("/503", exchange -> LocalHttpServer.respond(exchange, 503, "text/plain", "busy"));

        HttpResponse unauthorized = transport.execute(HttpRequest.get(server.url("/401")).build())
                .get(10, TimeUnit.SECONDS);
        HttpResponse unavailable = transport.execute(HttpRequest.get(server.url("/503")).build())
                .get(10, TimeUnit.SECONDS);

        assertThat(unauthorized.getStatus()).isEqualTo(401);
        assertThat(unauthorized.getBodyAsText()).isEqualTo("{\"e\":1}");
        assertThat(unavailable.getStatus()).isEqualTo(503);
        assertThat(unavailable.isSuccessful()).isFalse();
    }

    @Test
    void headRequestHasEmptyBody() throws Exception {
        server.on("/", exchange -> LocalHttpServer.respond(exchange, 200, "text/plain", "ignored"));

        HttpResponse response = transport.execute(HttpRequest.builder(com.aresstack.serp4j.http.HttpMethod.HEAD,
                server.url("/")).build()).get(10, TimeUnit.SECONDS);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getBodyLength()).isZero();
        assertThat(server.lastRequest().method).isEqualTo("HEAD");
    }

    @Test
    void decompressesGzipResponses() throws Exception {
        server.on("/gz", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Accept-Encoding")).contains("gzip");
            java.io.ByteArrayOutputStream compressed = new java.io.ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                gzip.write("{\"compressed\":true}".getBytes(StandardCharsets.UTF_8));
            }
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, compressed.size());
            try (OutputStream out = exchange.getResponseBody()) {
                compressed.writeTo(out);
            }
        });

        HttpResponse response = transport.execute(HttpRequest.get(server.url("/gz")).build())
                .get(10, TimeUnit.SECONDS);

        assertThat(response.getBodyAsText()).isEqualTo("{\"compressed\":true}");
    }

    @Test
    void perRequestTimeoutFailsWithTimeout() throws Exception {
        server.on("/slow", exchange -> {
            server.release.await(20, TimeUnit.SECONDS);
            LocalHttpServer.respond(exchange, 200, "text/plain", "late");
        });

        long start = System.nanoTime();
        HttpTransportException failure = failureOf(transport.execute(HttpRequest.get(server.url("/slow"))
                .timeout(Duration.ofMillis(300))
                .build()));

        assertThat(failure.getKind()).isEqualTo(Kind.TIMEOUT);
        assertThat(failure.isRetryable()).isTrue();
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(5_000);
    }

    @Test
    void refusedConnectionFailsWithConnect() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        HttpTransportException failure = failureOf(transport.execute(
                HttpRequest.get("http://127.0.0.1:" + closedPort + "/").build()));

        assertThat(failure.getKind()).isEqualTo(Kind.CONNECT);
        assertThat(failure.isRetryable()).isTrue();
    }

    @Test
    void failuresLeakNeitherCredentialsNorBodies() throws Exception {
        server.on("/drop", exchange -> {
            // The request was read; the connection is closed without any response.
            throw new IllegalStateException("drop " + SECRET);
        });
        HttpRequest.Builder builder = HttpRequest.post(server.url("/drop?api_key=" + SECRET + "&q=x"))
                .jsonBody("{\"body\":\"" + SECRET + "\"}");
        RequestAuthentication.header("X-Custom-Credential", "", () -> SECRET.toCharArray()).apply(builder);
        RequestAuthentication.queryParameter("custom_token", () -> SECRET.toCharArray()).apply(builder);

        HttpTransportException failure = failureOf(transport.execute(builder.build()));

        assertThat(failure.getKind()).isIn(Kind.IO, Kind.PROTOCOL, Kind.CONNECT);
        assertThat(server.lastRequest().header("X-Custom-Credential")).isEqualTo(SECRET);
        assertNoSecretInChain(failure);
        assertThat(failure.getMessage()).contains("POST").contains("api_key=****").contains("custom_token=****");
    }

    @Test
    void connectFailureMessageIsRedacted() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        HttpTransportException failure = failureOf(transport.execute(
                HttpRequest.get("http://127.0.0.1:" + closedPort + "/x?key=" + SECRET).build()));

        assertNoSecretInChain(failure);
        assertThat(failure.getCause()).isInstanceOf(TransportFailures.SanitizedCause.class);
        assertThat(failure.getCause().getStackTrace()).isNotEmpty();
    }

    @Test
    void followsRedirectsWithinOriginKeepingCredentials() throws Exception {
        server.on("/start", exchange -> LocalHttpServer.redirect(exchange, 302, "/target?step=2"));
        server.on("/target", exchange -> LocalHttpServer.respond(exchange, 200, "text/plain", "arrived"));

        HttpRequest.Builder builder = HttpRequest.get(server.url("/start"));
        RequestAuthentication.header("X-Custom-Credential", "", () -> SECRET.toCharArray()).apply(builder);
        HttpResponse response = transport.execute(builder.build()).get(10, TimeUnit.SECONDS);

        assertThat(response.getBodyAsText()).isEqualTo("arrived");
        assertThat(server.requests()).hasSize(2);
        assertThat(server.lastRequest().uri).isEqualTo("/target?step=2");
        assertThat(server.lastRequest().header("X-Custom-Credential")).isEqualTo(SECRET);
    }

    @Test
    void crossOriginRedirectDropsEverySensitiveHeader() throws Exception {
        try (LocalHttpServer other = new LocalHttpServer()) {
            other.on("/landing", exchange -> LocalHttpServer.respond(exchange, 200, "text/plain", "elsewhere"));
            server.on("/start", exchange -> LocalHttpServer.redirect(exchange, 307, other.url("/landing")));

            HttpRequest.Builder builder = HttpRequest.get(server.url("/start"))
                    .header("Accept", "application/json")
                    .header("Cookie", "session=" + SECRET)
                    .header("Authorization", "Bearer " + SECRET);
            RequestAuthentication.header("X-Custom-Credential", "", () -> SECRET.toCharArray()).apply(builder);

            HttpResponse response = transport.execute(builder.build()).get(10, TimeUnit.SECONDS);

            assertThat(response.getBodyAsText()).isEqualTo("elsewhere");
            LocalHttpServer.Recorded seen = other.lastRequest();
            assertThat(seen.header("Accept")).isEqualTo("application/json");
            assertThat(seen.header("X-Custom-Credential")).isNull();
            assertThat(seen.header("Authorization")).isNull();
            assertThat(seen.header("Cookie")).isNull();
            assertThat(server.lastRequest().header("X-Custom-Credential")).isEqualTo(SECRET);
        }
    }

    @Test
    void seeOtherAndFoundAfterPostContinueAsGetWithoutBody() throws Exception {
        server.on("/303", exchange -> LocalHttpServer.redirect(exchange, 303, "/result"));
        server.on("/302", exchange -> LocalHttpServer.redirect(exchange, 302, "/result"));
        server.on("/result", exchange -> LocalHttpServer.respond(exchange, 200, "text/plain", "result"));

        for (String path : new String[]{"/303", "/302"}) {
            HttpResponse response = transport.execute(HttpRequest.post(server.url(path)).jsonBody("{\"a\":1}").build())
                    .get(10, TimeUnit.SECONDS);

            assertThat(response.getBodyAsText()).isEqualTo("result");
            LocalHttpServer.Recorded seen = server.lastRequest();
            assertThat(seen.method).isEqualTo("GET");
            assertThat(seen.body).isEmpty();
            assertThat(seen.header("Content-Type")).isNull();
        }
    }

    @Test
    void temporaryAndPermanentRedirectRepeatMethodAndBody() throws Exception {
        server.on("/307", exchange -> LocalHttpServer.redirect(exchange, 307, "/echo"));
        server.on("/308", exchange -> LocalHttpServer.redirect(exchange, 308, "/echo"));
        server.on("/echo", exchange -> LocalHttpServer.respond(exchange, 200, "text/plain", "ok"));

        for (String path : new String[]{"/307", "/308"}) {
            transport.execute(HttpRequest.post(server.url(path)).jsonBody("{\"a\":1}").build())
                    .get(10, TimeUnit.SECONDS);

            LocalHttpServer.Recorded seen = server.lastRequest();
            assertThat(seen.uri).isEqualTo("/echo");
            assertThat(seen.method).isEqualTo("POST");
            assertThat(seen.bodyText()).isEqualTo("{\"a\":1}");
            assertThat(seen.header("Content-Type")).isEqualTo("application/json; charset=UTF-8");
        }
    }

    @Test
    void redirectLoopFailsWithProtocol() throws Exception {
        server.on("/loop", exchange -> LocalHttpServer.redirect(exchange, 302, "/loop"));

        HttpTransportException failure = failureOf(transport.execute(HttpRequest.get(server.url("/loop")).build()));

        assertThat(failure.getKind()).isEqualTo(Kind.PROTOCOL);
        assertThat(failure.getMessage()).contains("more than 5 redirects");
        assertThat(server.requests()).hasSize(6);
    }

    @Test
    void redirectsAreReturnedWhenFollowingIsOff() throws Exception {
        server.on("/start", exchange -> LocalHttpServer.redirect(exchange, 302, "/target"));
        try (AhcHttpTransport noRedirects = AhcHttpTransport.create(AhcHttpTransportOptions.builder()
                .followRedirects(false).build())) {

            HttpResponse response = noRedirects.execute(HttpRequest.get(server.url("/start")).build())
                    .get(10, TimeUnit.SECONDS);

            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getHeaders().getFirst("Location")).contains("/target");
            assertThat(server.requests()).hasSize(1);
        }
    }

    @Test
    void closeFailsRunningRequestsWithCancelledAndRejectsNewOnes() throws Exception {
        server.on("/slow", exchange -> {
            server.release.await(20, TimeUnit.SECONDS);
            LocalHttpServer.respond(exchange, 200, "text/plain", "late");
        });
        CompletableFuture<HttpResponse> running = transport.execute(HttpRequest.get(server.url("/slow")).build());
        awaitRequests(1);

        transport.close();
        transport.close();

        assertThat(failureOf(running).getKind()).isEqualTo(Kind.CANCELLED);
        assertThat(transport.isClosed()).isTrue();
        assertThatThrownBy(() -> transport.execute(HttpRequest.get(server.url("/slow")).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellingTheFutureAbortsTheRequest() throws Exception {
        server.on("/slow", exchange -> {
            server.release.await(20, TimeUnit.SECONDS);
            LocalHttpServer.respond(exchange, 200, "text/plain", "late");
        });
        CompletableFuture<HttpResponse> running = transport.execute(HttpRequest.get(server.url("/slow")).build());
        awaitRequests(1);

        assertThat(running.cancel(true)).isTrue();

        assertThat(running.isCancelled()).isTrue();
        server.on("/fast", exchange -> LocalHttpServer.respond(exchange, 200, "text/plain", "fast"));
        assertThat(transport.execute(HttpRequest.get(server.url("/fast")).build()).get(10, TimeUnit.SECONDS)
                .getBodyAsText()).isEqualTo("fast");
    }

    @Test
    void wrappedClientBelongsToTheCallerAndSurvivesClose() throws Exception {
        server.on("/", exchange -> LocalHttpServer.respond(exchange, 200, "text/plain", "ok"));
        try (AsyncHttpClient hostClient = new DefaultAsyncHttpClient()) {
            AhcHttpTransport first = AhcHttpTransport.wrap(hostClient, AhcHttpTransportOptions.defaults());
            AhcHttpTransport second = AhcHttpTransport.wrap(hostClient, AhcHttpTransportOptions.defaults());
            assertThat(first.ownsClient()).isFalse();

            first.execute(HttpRequest.get(server.url("/")).build()).get(10, TimeUnit.SECONDS);
            first.close();

            assertThat(hostClient.isClosed()).isFalse();
            assertThat(second.execute(HttpRequest.get(server.url("/")).build()).get(10, TimeUnit.SECONDS)
                    .getStatus()).isEqualTo(200);
            assertThat(server.lastRequest().header("User-Agent")).isEqualTo(AhcHttpTransportOptions.DEFAULT_USER_AGENT);
            second.close();
            assertThat(hostClient.isClosed()).isFalse();
        }
    }

    @Test
    void ownedClientIsClosedWithTheTransport() {
        AhcHttpTransport owned = AhcHttpTransport.create();
        assertThat(owned.ownsClient()).isTrue();
        owned.close();
        assertThat(owned.isClosed()).isTrue();
    }

    @Test
    void wrappedClientClosedByItsOwnerFailsTypedInsteadOfHanging() throws Exception {
        AsyncHttpClient hostClient = new DefaultAsyncHttpClient();
        AhcHttpTransport borrowed = AhcHttpTransport.wrap(hostClient, AhcHttpTransportOptions.defaults());
        hostClient.close();

        HttpTransportException failure = failureOf(borrowed.execute(HttpRequest.get(server.url("/")).build()));

        assertThat(failure.getKind()).isEqualTo(Kind.CANCELLED);
        borrowed.close();
    }

    @Test
    void sharedTransportServesConcurrentRequests() throws Exception {
        server.on("/n", exchange -> LocalHttpServer.respond(exchange, 200, "text/plain",
                exchange.getRequestURI().getQuery()));
        List<CompletableFuture<HttpResponse>> futures = new ArrayList<CompletableFuture<HttpResponse>>();
        for (int i = 0; i < 40; i++) {
            futures.add(transport.execute(HttpRequest.get(server.url("/n?i=" + i)).build()));
        }
        for (int i = 0; i < 40; i++) {
            assertThat(futures.get(i).get(10, TimeUnit.SECONDS).getBodyAsText()).isEqualTo("i=" + i);
        }
    }

    @Test
    void requestsOverTheConnectionLimitWaitInsteadOfFailing() throws Exception {
        java.util.concurrent.atomic.AtomicInteger concurrent = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger maxConcurrent = new java.util.concurrent.atomic.AtomicInteger();
        server.on("/limited", exchange -> {
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            Thread.sleep(20);
            concurrent.decrementAndGet();
            LocalHttpServer.respond(exchange, 200, "text/plain", "ok");
        });
        try (AhcHttpTransport limited = AhcHttpTransport.create(AhcHttpTransportOptions.builder()
                .maxConnections(3).maxConnectionsPerHost(2).build())) {
            List<CompletableFuture<HttpResponse>> futures = new ArrayList<CompletableFuture<HttpResponse>>();
            for (int i = 0; i < 25; i++) {
                futures.add(limited.execute(HttpRequest.get(server.url("/limited")).build()));
            }
            for (CompletableFuture<HttpResponse> future : futures) {
                assertThat(future.get(20, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
            }
        }
        assertThat(maxConcurrent.get()).isBetween(1, 2);
    }

    @Test
    void requestTimeoutAlsoBoundsTheWaitForAConnection() throws Exception {
        server.on("/slow", exchange -> {
            server.release.await(20, TimeUnit.SECONDS);
            LocalHttpServer.respond(exchange, 200, "text/plain", "late");
        });
        try (AhcHttpTransport single = AhcHttpTransport.create(AhcHttpTransportOptions.builder()
                .maxConnections(1).maxConnectionsPerHost(1).build())) {
            CompletableFuture<HttpResponse> blocking = single.execute(HttpRequest.get(server.url("/slow")).build());
            awaitRequests(1);

            long start = System.nanoTime();
            HttpTransportException failure = failureOf(single.execute(HttpRequest.get(server.url("/slow"))
                    .timeout(Duration.ofMillis(300)).build()));

            assertThat(failure.getKind()).isEqualTo(Kind.TIMEOUT);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(5_000);
            assertThat(blocking).isNotDone();
            assertThat(server.requests()).hasSize(1);
        }
    }

    @Test
    void rejectsNullRequestSynchronously() {
        assertThatThrownBy(() -> transport.execute(null)).isInstanceOf(NullPointerException.class);
    }

    private void awaitRequests(int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (server.requests().size() < count) {
            if (System.nanoTime() > deadline) {
                fail("server did not receive " + count + " request(s)");
            }
            Thread.sleep(10);
        }
    }

    private static HttpTransportException failureOf(CompletableFuture<HttpResponse> future) throws Exception {
        try {
            future.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(HttpTransportException.class);
            return (HttpTransportException) e.getCause();
        }
        throw new AssertionError("expected the request to fail");
    }

    private static void assertNoSecretInChain(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            assertThat(String.valueOf(current.getMessage())).doesNotContain(SECRET);
            assertThat(current.toString()).doesNotContain(SECRET);
        }
    }
}
