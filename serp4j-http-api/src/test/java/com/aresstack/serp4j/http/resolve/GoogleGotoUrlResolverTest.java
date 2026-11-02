package com.aresstack.serp4j.http.resolve;

import com.aresstack.serp4j.http.HttpMethod;
import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransport;
import com.aresstack.serp4j.http.HttpTransportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class GoogleGotoUrlResolverTest {

    static final String GOTO = "https://www.google.com/goto?url=CAESkQEBTOKEN";
    static final String TARGET =
            "https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-search.html";

    /** Minimal recording transport; never follows redirects. */
    static final class StubTransport implements HttpTransport {
        final List<HttpRequest> requests = new CopyOnWriteArrayList<HttpRequest>();
        final List<CompletableFuture<HttpResponse>> calls = new CopyOnWriteArrayList<CompletableFuture<HttpResponse>>();
        volatile Function<HttpRequest, CompletableFuture<HttpResponse>> answer;
        volatile boolean closed;

        StubTransport(Function<HttpRequest, CompletableFuture<HttpResponse>> answer) {
            this.answer = answer;
        }

        static StubTransport replying(HttpResponse response) {
            return new StubTransport(r -> CompletableFuture.completedFuture(response));
        }

        @Override
        public CompletableFuture<HttpResponse> execute(HttpRequest request) {
            requests.add(request);
            CompletableFuture<HttpResponse> f = answer.apply(request);
            calls.add(f);
            return f;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    static HttpResponse redirect(int status, String location) {
        HttpResponse.Builder b = HttpResponse.builder(status);
        if (location != null) {
            b.header("Location", location);
        }
        return b.build();
    }

    static String resolve(StubTransport t, String url) throws Exception {
        return new GoogleGotoUrlResolver(t).resolve(url, Duration.ofSeconds(3)).get(5, TimeUnit.SECONDS);
    }

    static String failureReason(StubTransport t, String url) {
        Throwable e = catchThrowable(() -> new GoogleGotoUrlResolver(t).resolve(url, Duration.ofSeconds(3)).join());
        assertThat(e).isInstanceOf(CompletionException.class);
        assertThat(e.getCause()).isInstanceOf(UrlResolutionException.class);
        assertThat(e.getCause().getMessage()).doesNotContain("CAESkQEBTOKEN");
        return ((UrlResolutionException) e.getCause()).getReason();
    }

    @Test
    void found302WithLocationResolvesWithOneGetWithoutFollowingAndWithoutCredentials() throws Exception {
        StubTransport t = StubTransport.replying(redirect(302, TARGET));
        assertThat(resolve(t, GOTO)).isEqualTo(TARGET);
        assertThat(t.requests).hasSize(1);
        HttpRequest sent = t.requests.get(0);
        assertThat(sent.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(sent.getUrl()).isEqualTo(GOTO);
        assertThat(sent.isFollowRedirects()).isFalse();
        assertThat(sent.getHeaders().getFirst("Authorization")).isEmpty();
        assertThat(sent.getHeaders().getFirst("Cookie")).isEmpty();
        assertThat(sent.getHeaders().getFirst("Referer")).isEmpty();
        assertThat(sent.hasBody()).isFalse();
        assertThat(t.closed).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 303, 307, 308})
    void otherRedirectStatusesResolve(int status) throws Exception {
        assertThat(resolve(StubTransport.replying(redirect(status, TARGET)), GOTO)).isEqualTo(TARGET);
    }

    @Test
    void googleComWithoutWwwIsSupported() throws Exception {
        assertThat(resolve(StubTransport.replying(redirect(302, "http://example.org/a")),
                "https://google.com/goto?url=abc")).isEqualTo("http://example.org/a");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://www.google.com/goto?url=abc",
            "https://evilgoogle.com/goto?url=abc",
            "https://google.com.evil.example/goto?url=abc",
            "https://evil.example/google.com/goto?url=abc",
            "https://maps.google.com/goto?url=abc",
            "https://www.google.de/goto?url=abc",
            "https://user@www.google.com/goto?url=abc",
            "https://www.google.com:8443/goto?url=abc",
            "https://www.google.com/gotox?url=abc",
            "https://www.google.com/url?q=https://example.org",
            "https://www.google.com/goto",
            "https://www.google.com/goto?url=",
            "https://www.google.com/goto?q=abc",
            "https://github.com/x",
            "not a url",
            ""})
    void foreignOrBrokenUrlsAreNotSupportedAndCauseNoRequest(String url) {
        StubTransport t = StubTransport.replying(redirect(302, TARGET));
        assertThat(new GoogleGotoUrlResolver(t).supports(url)).isFalse();
        assertThat(failureReason(t, url)).isEqualTo(UrlResolutionException.UNSUPPORTED);
        assertThat(t.requests).isEmpty();
    }

    @Test
    void missingLocationFails() {
        assertThat(failureReason(StubTransport.replying(redirect(302, null)), GOTO))
                .isEqualTo(UrlResolutionException.INVALID_LOCATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "/relative/path", "relative", "//example.org/x", "ftp://example.org/x",
            "javascript:alert(1)", "https://", "https://user:pw@example.org/", "http://exa mple.org/"})
    void invalidOrRelativeLocationFails(String location) {
        assertThat(failureReason(StubTransport.replying(redirect(302, location)), GOTO))
                .isEqualTo(UrlResolutionException.INVALID_LOCATION);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204, 304, 400, 403, 404, 429, 500, 503})
    void nonRedirectAnswersFail(int status) {
        assertThat(failureReason(StubTransport.replying(redirect(status, TARGET)), GOTO))
                .isEqualTo(UrlResolutionException.NOT_A_REDIRECT);
    }

    @Test
    void timeoutAndNetworkErrorsFail() {
        StubTransport timeout = new StubTransport(r -> failedWith(HttpTransportException.Kind.TIMEOUT));
        assertThat(failureReason(timeout, GOTO)).isEqualTo(UrlResolutionException.TIMEOUT);
        StubTransport connect = new StubTransport(r -> failedWith(HttpTransportException.Kind.CONNECT));
        assertThat(failureReason(connect, GOTO)).isEqualTo(UrlResolutionException.NETWORK);
    }

    @Test
    void timeoutIsCappedAtThreeSecondsAndShortenedByCaller() throws Exception {
        StubTransport t = StubTransport.replying(redirect(302, TARGET));
        GoogleGotoUrlResolver resolver = new GoogleGotoUrlResolver(t);
        resolver.resolve(GOTO, Duration.ofSeconds(30)).get(5, TimeUnit.SECONDS);
        resolver.resolve(GOTO, Duration.ofMillis(250)).get(5, TimeUnit.SECONDS);
        assertThat(t.requests.get(0).getTimeout()).contains(Duration.ofSeconds(3));
        assertThat(t.requests.get(1).getTimeout()).contains(Duration.ofMillis(250));
        Throwable e = catchThrowable(() -> resolver.resolve(GOTO, Duration.ZERO).join());
        assertThat(((UrlResolutionException) e.getCause()).getReason()).isEqualTo(UrlResolutionException.TIMEOUT);
        assertThat(t.requests).hasSize(2);
    }

    @Test
    void cancellingTheResolutionCancelsTheExchange() {
        StubTransport t = new StubTransport(r -> new CompletableFuture<HttpResponse>());
        CompletableFuture<String> f = new GoogleGotoUrlResolver(t).resolve(GOTO, Duration.ofSeconds(3));
        f.cancel(true);
        assertThat(t.calls.get(0)).isCancelled();
        assertThat(t.closed).isFalse();
    }

    private static CompletableFuture<HttpResponse> failedWith(HttpTransportException.Kind kind) {
        CompletableFuture<HttpResponse> f = new CompletableFuture<HttpResponse>();
        f.completeExceptionally(new HttpTransportException(kind, "simulated " + kind));
        return f;
    }
}
