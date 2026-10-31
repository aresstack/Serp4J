package com.aresstack.serp4j.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpTypesTest {

    @Test
    void headersAreCaseInsensitiveMultiValuedAndMaskSecretsInToString() {
        HttpHeaders headers = HttpHeaders.builder()
                .add("Accept", "application/json")
                .add("accept", "text/html")
                .add("Authorization", "Bearer very-secret")
                .build();

        assertThat(headers.getFirst("ACCEPT")).contains("application/json");
        assertThat(headers.getAll("Accept")).containsExactly("application/json", "text/html");
        assertThat(headers.contains("authorization")).isTrue();
        assertThat(headers.toString()).doesNotContain("very-secret").contains("[****]");
        assertThat(headers.toBuilder().set("Accept", "*/*").build().getAll("accept")).containsExactly("*/*");
        assertThat(headers).isEqualTo(headers.toBuilder().build());
        assertThat(HttpHeaders.isSensitive("x-api-key")).isTrue();
        assertThat(HttpHeaders.isSensitive("Accept")).isFalse();
    }

    @Test
    void requestCopiesBodyAndHidesItInToString() {
        byte[] body = "{\"q\":1}".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.post("https://api.example/search")
                .body(body, "application/json")
                .header("X-Subscription-Token", "token-123")
                .timeout(Duration.ofSeconds(5))
                .build();
        body[0] = 'X';

        assertThat(request.getBody()).isEqualTo("{\"q\":1}".getBytes(StandardCharsets.UTF_8));
        assertThat(request.hasBody()).isTrue();
        assertThat(request.getContentType()).contains("application/json");
        assertThat(request.getTimeout()).contains(Duration.ofSeconds(5));
        assertThat(request.toString()).doesNotContain("token-123").doesNotContain("\"q\"");
        assertThat(request.toBuilder().build()).isEqualTo(request);
        assertThat(HttpRequest.get("https://e.example").build().hasBody()).isFalse();
        assertThat(HttpRequest.get("https://e.example").jsonBody("{}").build().getContentType())
                .contains("application/json; charset=UTF-8");
        assertThatIllegalArgumentException().isThrownBy(() -> HttpRequest.get(" "));
    }

    @Test
    void responseDecodesBodyWithDeclaredCharset() {
        HttpResponse latin = HttpResponse.builder(200)
                .header("Content-Type", "text/plain; charset=ISO-8859-1")
                .body("grüß".getBytes(StandardCharsets.ISO_8859_1))
                .build();
        HttpResponse error = HttpResponse.builder(503).textBody("busy", "text/plain").build();

        assertThat(latin.getBodyAsText()).isEqualTo("grüß");
        assertThat(latin.isSuccessful()).isTrue();
        assertThat(error.isSuccessful()).isFalse();
        assertThat(error.getBodyAsText()).isEqualTo("busy");
        assertThat(error.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(error.toString()).doesNotContain("busy");
        assertThatIllegalArgumentException().isThrownBy(() -> HttpResponse.builder(600));
    }

    @Test
    void emptyHeadersAreAnImmutableSingleton() {
        HttpHeaders empty = HttpHeaders.empty();

        assertThatThrownBy(() -> empty.asMap().put("X-Injected", Collections.singletonList("1")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(HttpHeaders.builder().build()).isSameAs(empty);
        assertThat(HttpRequest.get("https://e.example").build().getHeaders().isEmpty()).isTrue();
    }

    @Test
    void redactedUrlMasksCredentialQueryParametersOnly() {
        HttpRequest request = HttpRequest
                .get("https://e.example/v1?q=serp4j&api_key=secret-1&Token=secret-2&brd_key=secret-3&page=2#top")
                .sensitiveQueryParameter("BRD_KEY")
                .build();

        assertThat(request.getRedactedUrl())
                .isEqualTo("https://e.example/v1?q=serp4j&api_key=****&Token=****&brd_key=****&page=2#top");
        assertThat(request.toString()).contains("q=serp4j").doesNotContain("secret");
        assertThat(request.getUrl()).contains("secret-1");
        assertThat(request.getSensitiveQueryParameters()).containsExactly("brd_key");
        assertThat(request.toBuilder().build()).isEqualTo(request);
        assertThat(HttpRequest.get("https://e.example/plain").build().getRedactedUrl())
                .isEqualTo("https://e.example/plain");
        assertThat(HttpRequest.isSensitiveQueryParameter("API-KEY")).isTrue();
        assertThat(HttpRequest.isSensitiveQueryParameter("q")).isFalse();
    }

    @Test
    void textBodyHonoursTheDeclaredCharset() {
        HttpResponse response = HttpResponse.builder(200)
                .textBody("café", "text/plain; charset=ISO-8859-1").build();
        HttpRequest request = HttpRequest.post("https://e.example")
                .textBody("café", "text/plain; charset=ISO-8859-1").build();

        assertThat(response.getBodyLength()).isEqualTo(4);
        assertThat(response.getBodyAsText()).isEqualTo("café");
        assertThat(request.getBody()).isEqualTo("café".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(request.getContentType()).contains("text/plain; charset=ISO-8859-1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> HttpResponse.builder(200).textBody("x", "text/plain; charset=no-such-charset"));
        assertThat(HttpResponse.builder(200).header("Content-Type", "text/plain; charset=no-such-charset").build()
                .getCharset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void transportExceptionClassifiesRetryability() {
        assertThat(new HttpTransportException(HttpTransportException.Kind.TIMEOUT, "timed out").isRetryable()).isTrue();
        assertThat(new HttpTransportException(HttpTransportException.Kind.TLS, "handshake").isRetryable()).isFalse();
    }
}
