package com.aresstack.serp4j.testkit.http;

import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.testkit.fixture.Fixture;
import com.aresstack.serp4j.testkit.fixture.Fixtures;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeHttpTransportTest {

    private static final String BASE = "https://api.example.invalid";

    private static HttpResponse get(CompletableFuture<HttpResponse> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    private static HttpTransportException.Kind failureKind(CompletableFuture<HttpResponse> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(HttpTransportException.class);
            return ((HttpTransportException) e.getCause()).getKind();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("expected a transport failure");
    }

    @Test
    void answersMatchingRequestAndRecordsIt() throws Exception {
        FakeHttpTransport http = FakeHttpTransport.create()
                .on(RequestMatcher.get(BASE + "/search")).reply(FakeHttpStep.json(200, "{\"ok\":true}"));
        HttpRequest request = HttpRequest.get(BASE + "/search?q=serp4j").build();

        HttpResponse response = get(http.execute(request));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getBodyAsText()).isEqualTo("{\"ok\":true}");
        assertThat(http.getRequests()).containsExactly(request);
        assertThat(http.getLastRequest()).isSameAs(request);
        assertThat(http.getUses()).isEqualTo(1);
        http.assertNoUnmatchedRequests();
    }

    @Test
    void firstMatchingStubWins() throws Exception {
        FakeHttpTransport http = FakeHttpTransport.create()
                .on(RequestMatcher.urlContains("special")).reply(FakeHttpStep.status(418))
                .onAnyRequest(FakeHttpStep.status(200));

        assertThat(get(http.execute(HttpRequest.get(BASE + "/special").build())).getStatus()).isEqualTo(418);
        assertThat(get(http.execute(HttpRequest.get(BASE + "/other").build())).getStatus()).isEqualTo(200);
    }

    @Test
    void sequencePendingThenReadyRepeatsTheLastStep() throws Exception {
        Fixture pending = Fixtures.load("testkit-synthetic", "task-pending.json");
        Fixture ready = Fixtures.load("testkit-synthetic", "search-ok.json");
        FakeHttpTransport http = FakeHttpTransport.create()
                .on(RequestMatcher.get(BASE + "/task")).reply(FakeHttpStep.fixture(pending.getStatus(), pending),
                        FakeHttpStep.fixture(ready));
        HttpRequest poll = HttpRequest.get(BASE + "/task/1").build();

        assertThat(get(http.execute(poll)).getStatus()).isEqualTo(202);
        assertThat(get(http.execute(poll)).getBodyAsText()).contains("Synthetic result 1");
        assertThat(get(http.execute(poll)).getStatus()).isEqualTo(200);
        http.assertRequestCount(RequestMatcher.get(BASE + "/task"), 3);
    }

    @Test
    void errorStatusIsAResponseNotATransportFailure() throws Exception {
        FakeHttpTransport http = FakeHttpTransport.create().onAnyRequest(FakeHttpStep.json(401, "{}"));

        HttpResponse response = get(http.execute(HttpRequest.get(BASE).build()));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.isSuccessful()).isFalse();
    }

    @Test
    void transportFailureFailsTheFuture() {
        FakeHttpTransport http = FakeHttpTransport.create()
                .onAnyRequest(FakeHttpStep.fail(HttpTransportException.Kind.CONNECT));

        assertThat(failureKind(http.execute(HttpRequest.get(BASE).build())))
                .isEqualTo(HttpTransportException.Kind.CONNECT);
    }

    @Test
    void delayPostponesTheAnswer() throws Exception {
        FakeHttpTransport http = FakeHttpTransport.create()
                .onAnyRequest(FakeHttpStep.status(200).after(Duration.ofMillis(150)));
        long start = System.nanoTime();

        CompletableFuture<HttpResponse> future = http.execute(HttpRequest.get(BASE).build());
        assertThat(future).isNotDone();
        get(future);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(140));
    }

    @Test
    void requestTimeoutShorterThanDelayFailsWithTimeout() {
        FakeHttpTransport http = FakeHttpTransport.create()
                .onAnyRequest(FakeHttpStep.status(200).after(Duration.ofSeconds(5)));
        HttpRequest request = HttpRequest.get(BASE).timeout(Duration.ofMillis(50)).build();

        assertThat(failureKind(http.execute(request))).isEqualTo(HttpTransportException.Kind.TIMEOUT);
    }

    @Test
    void hangWithTimeoutFailsWithTimeout() {
        FakeHttpTransport http = FakeHttpTransport.create().onAnyRequest(FakeHttpStep.hang());
        HttpRequest request = HttpRequest.get(BASE).timeout(Duration.ofMillis(50)).build();

        assertThat(failureKind(http.execute(request))).isEqualTo(HttpTransportException.Kind.TIMEOUT);
    }

    @Test
    void closeCancelsRunningRequestsAndRejectsNewOnes() {
        FakeHttpTransport http = FakeHttpTransport.create().onAnyRequest(FakeHttpStep.hang());
        CompletableFuture<HttpResponse> running = http.execute(HttpRequest.get(BASE).build());
        assertThat(http.getRunningCount()).isEqualTo(1);

        http.close();
        http.close();

        assertThat(failureKind(running)).isEqualTo(HttpTransportException.Kind.CANCELLED);
        assertThat(http.isClosed()).isTrue();
        assertThat(http.getCloseCalls()).isEqualTo(2);
        assertThatThrownBy(() -> http.execute(HttpRequest.get(BASE).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unmatchedRequestFailsLoudlyAndIsReported() {
        FakeHttpTransport http = FakeHttpTransport.create()
                .on(RequestMatcher.post(BASE + "/known")).reply(FakeHttpStep.status(200));
        HttpRequest request = HttpRequest.get(BASE + "/unknown?api_key=top-secret-value")
                .sensitiveQueryParameter("api_key").build();

        CompletableFuture<HttpResponse> future = http.execute(request);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(UnexpectedHttpRequestError.class);
        assertThat(http.getUnmatchedRequests()).containsExactly(request);
        assertThatThrownBy(http::assertNoUnmatchedRequests)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("/unknown")
                .hasMessageNotContaining("top-secret-value");
    }

    @Test
    void matchersCombineMethodHeaderAndBody() throws Exception {
        FakeHttpTransport http = FakeHttpTransport.create()
                .on(RequestMatcher.post(BASE).and(RequestMatcher.header("X-Tenant", "a"))
                        .and(RequestMatcher.bodyContains("serp4j")))
                .reply(FakeHttpStep.status(201))
                .onAnyRequest(FakeHttpStep.status(400));

        HttpRequest match = HttpRequest.post(BASE + "/tasks").header("X-Tenant", "a").jsonBody("{\"q\":\"serp4j\"}")
                .build();
        HttpRequest other = HttpRequest.post(BASE + "/tasks").header("X-Tenant", "b").jsonBody("{\"q\":\"serp4j\"}")
                .build();

        assertThat(get(http.execute(match)).getStatus()).isEqualTo(201);
        assertThat(get(http.execute(other)).getStatus()).isEqualTo(400);
        assertThat(http.getRequests(RequestMatcher.header("X-Tenant", "b"))).containsExactly(other);
    }

    @Test
    void pathMatcherIgnoresQuery() {
        RequestMatcher matcher = RequestMatcher.path(BASE + "/search");
        assertThat(matcher.matches(HttpRequest.get(BASE + "/search?q=1").build())).isTrue();
        assertThat(matcher.matches(HttpRequest.get(BASE + "/search/more").build())).isFalse();
    }

    @Test
    void matcherDescriptionsRedactCredentialQueryParameters() {
        FakeHttpTransport http = FakeHttpTransport.create()
                .on(RequestMatcher.urlEquals(BASE + "/search?q=x&api_key=stub-secret-value&key=other-secret"))
                .reply(FakeHttpStep.status(200));

        CompletableFuture<HttpResponse> future = http.execute(HttpRequest.get(BASE + "/elsewhere").build());

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(UnexpectedHttpRequestError.class)
                .satisfies(e -> assertThat(e.getCause().getMessage())
                        .contains("q=****").contains("api_key=****")
                        .doesNotContain("stub-secret-value").doesNotContain("other-secret"));
    }

    @Test
    void matcherDescriptionsRedactFragmentsCustomNamesUserInfoAndBodies() {
        RequestMatcher fragment = RequestMatcher.urlContains("api_key=fragment-secret");
        RequestMatcher custom = RequestMatcher.urlStartsWith(BASE + "/search?custom_token=custom-secret&x=1");
        RequestMatcher userInfo = RequestMatcher.urlStartsWith("https://user:userinfo-secret@api.example.invalid/");
        RequestMatcher body = RequestMatcher.bodyContains("\"password\":\"body-secret\"");

        assertThat(fragment.toString()).isEqualTo("url contains api_key=****");
        assertThat(custom.toString()).contains("custom_token=****").contains("x=****").doesNotContain("custom-secret");
        assertThat(userInfo.toString()).contains("https://****@api.example.invalid/")
                .doesNotContain("userinfo-secret");
        assertThat(body.toString()).doesNotContain("body-secret");

        FakeHttpTransport http = FakeHttpTransport.create()
                .on(fragment).reply(FakeHttpStep.status(200))
                .on(custom).reply(FakeHttpStep.status(200))
                .on(userInfo).reply(FakeHttpStep.status(200))
                .on(body).reply(FakeHttpStep.status(200));
        CompletableFuture<HttpResponse> future = http.execute(HttpRequest.get(BASE + "/elsewhere").build());

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(UnexpectedHttpRequestError.class)
                .satisfies(e -> assertThat(e.getCause().getMessage())
                        .doesNotContain("fragment-secret").doesNotContain("custom-secret")
                        .doesNotContain("userinfo-secret").doesNotContain("body-secret"));
    }

    @Test
    void redactedDescriptionsStillMatchTheOriginalUrl() {
        assertThat(RequestMatcher.urlContains("api_key=fragment-secret")
                .matches(HttpRequest.get(BASE + "/s?api_key=fragment-secret").build())).isTrue();
        assertThat(RequestMatcher.urlContains("api_key=fragment-secret")
                .matches(HttpRequest.get(BASE + "/s?api_key=other").build())).isFalse();
    }
}
