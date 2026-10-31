package com.aresstack.serp4j.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
    void transportExceptionClassifiesRetryability() {
        assertThat(new HttpTransportException(HttpTransportException.Kind.TIMEOUT, "timed out").isRetryable()).isTrue();
        assertThat(new HttpTransportException(HttpTransportException.Kind.TLS, "handshake").isRetryable()).isFalse();
    }
}
