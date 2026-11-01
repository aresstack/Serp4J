package com.aresstack.serp4j.http;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RequestAuthenticationTest {

    /** Hands out a copy of the secret and remembers it so the test can check that it was wiped. */
    private static final class TrackingSecret implements Supplier<char[]> {
        private final String value;
        private char[] lastHandedOut;

        TrackingSecret(String value) {
            this.value = value;
        }

        @Override
        public char[] get() {
            lastHandedOut = value.toCharArray();
            return lastHandedOut;
        }

        boolean wasWiped() {
            for (char c : lastHandedOut) {
                if (c != '\0') {
                    return false;
                }
            }
            return true;
        }
    }

    private static HttpRequest apply(RequestAuthentication authentication) {
        HttpRequest.Builder builder = HttpRequest.get("https://api.example/v1?x=1");
        authentication.apply(builder);
        return builder.build();
    }

    @Test
    void bearerSetsAuthorizationHeaderAndWipesTheSecret() {
        TrackingSecret token = new TrackingSecret("tok");
        HttpRequest request = apply(RequestAuthentication.bearer(token));

        assertThat(request.getHeaders().getFirst("Authorization")).contains("Bearer tok");
        assertThat(token.wasWiped()).isTrue();
    }

    @Test
    void customHeaderWithoutPrefix() {
        HttpRequest request = apply(RequestAuthentication.header("X-Subscription-Token", "", new TrackingSecret("brave")));
        assertThat(request.getHeaders().getFirst("x-subscription-token")).contains("brave");
    }

    @Test
    void basicEncodesUsernameAndPassword() {
        TrackingSecret password = new TrackingSecret("päss:word");
        HttpRequest request = apply(RequestAuthentication.basic("user", password));

        String expected = Base64.getEncoder().encodeToString(
                "user:päss:word".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(request.getHeaders().getFirst("Authorization")).contains("Basic " + expected);
        assertThat(password.wasWiped()).isTrue();
    }

    @Test
    void queryParameterAppendsToExistingQueryStringAndIsMaskedInLogs() {
        HttpRequest request = apply(RequestAuthentication.queryParameter("key", new TrackingSecret("a b&c")));
        HttpRequest custom = apply(RequestAuthentication.queryParameter("zoneSecret", new TrackingSecret("s")));

        assertThat(request.getUrl()).isEqualTo("https://api.example/v1?x=1&key=a%20b%26c");
        assertThat(request.getRedactedUrl()).isEqualTo("https://api.example/v1?x=1&key=****");
        assertThat(request.toString()).doesNotContain("a%20b%26c");
        assertThat(custom.getUrl()).isEqualTo("https://api.example/v1?x=1&zoneSecret=s");
        assertThat(custom.getRedactedUrl()).isEqualTo("https://api.example/v1?x=1&zoneSecret=****");
    }

    @Test
    void noneAndAllCompose() {
        HttpRequest request = apply(RequestAuthentication.all(RequestAuthentication.none(),
                RequestAuthentication.bearer(new TrackingSecret("t")),
                RequestAuthentication.header("X-Trace", "", new TrackingSecret("1"))));

        assertThat(request.getHeaders().getFirst("Authorization")).contains("Bearer t");
        assertThat(request.getHeaders().getFirst("X-Trace")).contains("1");
    }

    @Test
    void urlQueryBuilderEncodesPerRfc3986() {
        String url = UrlQueryBuilder.of("https://api.example/search")
                .param("q", "serp4j java 8 & more")
                .param("count", 20)
                .param("safe", true)
                .paramIfPresent("country", null)
                .paramIfText("lang", " ")
                .param("umlaut", "ä~_-.")
                .build();

        assertThat(url).isEqualTo("https://api.example/search?q=serp4j%20java%208%20%26%20more&count=20&safe=true"
                + "&umlaut=%C3%A4~_-.");
        assertThat(UrlQueryBuilder.of("https://e.example/?").param("a", "1").build()).isEqualTo("https://e.example/?a=1");
        assertThat(UrlQueryBuilder.of("https://e.example/?a=1&").param("b", "2").build())
                .isEqualTo("https://e.example/?a=1&b=2");
        assertThat(UrlQueryBuilder.of("https://e.example/").build()).isEqualTo("https://e.example/");
        assertThat(UrlQueryBuilder.of("https://e.example/search#section").param("q", "test").build())
                .isEqualTo("https://e.example/search?q=test#section");
        assertThat(UrlQueryBuilder.of("https://e.example/s?a=1#f").param("b", "2").build())
                .isEqualTo("https://e.example/s?a=1&b=2#f");
    }

    /** Finding #4: optional parameters are nullable, never {@code Optional} (architecture.md §3, ADR-004). */
    @Test
    void paramIfPresentOmitsNullAndAddsNonNullValues() {
        String url = UrlQueryBuilder.of("https://api.example/search")
                .paramIfPresent("country", null)
                .paramIfPresent("lang", "de")
                .paramIfPresent("empty", "")
                .build();

        assertThat(url).isEqualTo("https://api.example/search?lang=de&empty=");
        assertThat(UrlQueryBuilder.of("https://e.example/").paramIfPresent("a", null).build())
                .isEqualTo("https://e.example/");
    }

    /** Finding #10 (header injection): a secret with CR/LF is rejected without leaking the secret. */
    @Test
    void bearerTokenWithLineBreakIsRejectedWithoutLeakingTheSecret() {
        String secret = "abc\n";
        TrackingSecret token = new TrackingSecret(secret);
        HttpRequest.Builder request = HttpRequest.get("https://e.example");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> RequestAuthentication.bearer(token).apply(request))
                .withMessageContaining("Authorization")
                .withMessageNotContaining("abc")
                .withMessageNotContaining(secret)
                .withMessageNotContaining("Bearer abc");
        assertThat(token.wasWiped()).isTrue();
        assertThat(request.build().getHeaders().contains("Authorization")).isFalse();
    }

    @Test
    void customAuthenticationHeaderIsMaskedWhateverItsName() {
        String secret = "s3cr3t-7f1e9a2b-unique";
        HttpRequest request = apply(RequestAuthentication.header("X-Zone-Credential", "", new TrackingSecret(secret)));

        assertThat(request.getHeaders().getFirst("x-zone-credential")).contains(secret);
        assertThat(request.getHeaders().isSensitiveHeader("X-ZONE-CREDENTIAL")).isTrue();
        assertThat(request.getHeaders().toString()).doesNotContain(secret).contains("X-Zone-Credential=[****]");
        assertThat(request.toString()).doesNotContain(secret);

        HttpRequest copy = request.toBuilder().header("X-Trace", "1").build();
        assertThat(copy.toString()).doesNotContain(secret).contains("X-Trace=[1]");
        HttpHeaders merged = HttpHeaders.builder().addAll(request.getHeaders()).build();
        assertThat(merged.toString()).doesNotContain(secret);
        assertThat(copy.getHeaders()).isNotEqualTo(HttpHeaders.builder()
                .add("X-Zone-Credential", secret).add("X-Trace", "1").build());
    }

    @Test
    void basicAndBearerAreMaskedToo() {
        String secret = "t0ken-3c4d5e6f-unique";
        assertThat(apply(RequestAuthentication.bearer(new TrackingSecret(secret))).toString()).doesNotContain(secret);
        String encoded = Base64.getEncoder().encodeToString(("u:" + secret).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(apply(RequestAuthentication.basic("u", new TrackingSecret(secret))).toString()).doesNotContain(encoded);
    }
}
