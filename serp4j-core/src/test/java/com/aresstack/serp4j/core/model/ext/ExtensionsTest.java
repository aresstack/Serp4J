package com.aresstack.serp4j.core.model.ext;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtensionsTest {

    private static final ExtensionKey<String> ZONE = ExtensionKey.of("brightdata", "zone", String.class);
    private static final ExtensionKey<Integer> DEPTH = ExtensionKey.of("dataforseo", "depth", Integer.class);

    @Test
    void typedKeysStoreAndRetrieveValues() {
        Extensions extensions = Extensions.builder().put(ZONE, "serp-de").put(DEPTH, 20).build();

        assertThat(extensions.get(ZONE)).contains("serp-de");
        assertThat(extensions.get(DEPTH)).contains(20);
        assertThat(extensions.get(ExtensionKey.of("brightdata", "zone", Integer.class))).isEmpty();
        assertThat(extensions.contains(ZONE)).isTrue();
        assertThat(extensions.isEmpty()).isFalse();
        assertThat(Extensions.empty().isEmpty()).isTrue();
    }

    @Test
    void mergeLetsThisWinAndKeepsUnmappedPerNamespace() {
        StructuredValue remainder = StructuredValue.objectBuilder().put("raw_field", "x").build();
        Extensions a = Extensions.builder().put(ZONE, "a").unmapped("brightdata", remainder).build();
        Extensions b = Extensions.builder().put(ZONE, "b").put(DEPTH, 1).build();

        Extensions merged = a.merge(b);

        assertThat(merged.get(ZONE)).contains("a");
        assertThat(merged.get(DEPTH)).contains(1);
        assertThat(merged.getUnmapped("brightdata")).contains(remainder);
        assertThat(merged.getUnmappedNamespaces()).containsExactly("brightdata");
        assertThat(a.with(DEPTH, 5).get(DEPTH)).contains(5);
        assertThat(a.get(DEPTH)).isEmpty();
    }

    @Test
    void structuredValueNavigatesPathsAndComparesNumbersByValue() {
        StructuredValue tree = StructuredValue.objectBuilder()
                .put("items", StructuredValue.arrayBuilder()
                        .add(StructuredValue.objectBuilder().put("rank", 1).put("ok", true).build())
                        .add("plain")
                        .build())
                .put("price", StructuredValue.of(new BigDecimal("1.50")))
                .putNull("nothing")
                .build();

        assertThat(tree.path("items", "0", "rank").flatMap(StructuredValue::asNumber)).contains(BigDecimal.ONE);
        assertThat(tree.path("items", "0", "ok").flatMap(StructuredValue::asBoolean)).contains(true);
        assertThat(tree.path("items", "1").flatMap(StructuredValue::asText)).contains("plain");
        assertThat(tree.path("items", "7")).isEmpty();
        assertThat(tree.path("nothing").get().isNull()).isTrue();
        assertThat(tree.get("price")).contains(StructuredValue.of(1.5));
        assertThat(StructuredValue.of(2L)).isEqualTo(StructuredValue.of(new BigDecimal("2.0")));
        assertThat(tree.toString()).contains("\"items\"").contains("\"price\"");
    }

    @Test
    void rawPayloadStripsSensitiveHeadersAndCopiesBody() {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        RawPayload payload = RawPayload.builder("application/json", body)
                .header("Authorization", "Bearer secret-token")
                .header("X-Subscription-Token", "brave-key")
                .header("Content-Length", "7")
                .httpStatus(200)
                .build();
        body[0] = 'X';

        assertThat(payload.getHeader("Authorization")).isEmpty();
        assertThat(payload.getHeader("x-subscription-token")).isEmpty();
        assertThat(payload.getHeader("content-length")).contains("7");
        assertThat(payload.getBodyAsText()).isEqualTo("{\"a\":1}");
        assertThat(payload.getHttpStatus()).hasValue(200);
        assertThat(payload.toString()).doesNotContain("secret-token").doesNotContain("\"a\"");
        assertThat(RawPayload.isSensitiveHeader("COOKIE")).isTrue();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RawPayload.builder("text/plain", new byte[0]).httpStatus(99));
    }

    @Test
    void rawPayloadBuilderStaysUsableAndBuiltPayloadsDoNotChange() {
        RawPayload.Builder builder = RawPayload.builder("text/plain", new byte[0]).header("X-Trace", "1");
        RawPayload first = builder.build();

        RawPayload second = builder.header("X-Trace", "2").header("X-Other", "3").build();

        assertThat(first.getHeaders()).containsOnlyKeys("X-Trace");
        assertThat(first.getHeaders().get("x-trace")).containsExactly("1");
        assertThat(second.getHeaders().get("X-Trace")).containsExactly("1", "2");
        assertThat(second.getHeader("X-Other")).contains("3");
        assertThat(first).isNotEqualTo(second);
        assertThatThrownBy(() -> first.getHeaders().get("X-Trace").add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rawPayloadDropsCustomSensitiveHeaders() {
        String secret = "zone-9a8b7c6d-unique";
        RawPayload payload = RawPayload.builder("text/plain", new byte[0])
                .header("X-Zone-Credential", secret)
                .header("X-Trace", "1")
                .sensitiveHeader("x-zone-credential")
                .header("X-ZONE-CREDENTIAL", secret)
                .header("Authorization", "Bearer " + secret)
                .build();

        assertThat(payload.getHeaders()).containsOnlyKeys("X-Trace");
        assertThat(payload.toString()).doesNotContain(secret);
    }

    /** #8: equals compares header names case-insensitively, so hashCode must as well. */
    @Test
    void rawPayloadHashCodeIsConsistentWithCaseInsensitiveHeaderEquality() {
        RawPayload upper = RawPayload.builder("text/plain", new byte[0]).header("X-Trace", "1").build();
        RawPayload lower = RawPayload.builder("text/plain", new byte[0]).header("x-trace", "1").build();

        assertThat(upper).isEqualTo(lower);
        assertThat(upper.hashCode()).isEqualTo(lower.hashCode());
        assertThat(upper).isNotEqualTo(RawPayload.builder("text/plain", new byte[0]).header("x-trace", "2").build());
    }

    /** #9: a header name with surrounding whitespace must not bypass the sensitive-header filter. */
    @Test
    void rawPayloadFiltersSensitiveHeadersWithSurroundingWhitespace() {
        String secret = "token-5e4d3c2b-unique";
        RawPayload payload = RawPayload.builder("text/plain", new byte[0])
                .sensitiveHeader("x-zone-credential")
                .header(" Authorization", "Bearer " + secret)
                .header("Set-Cookie ", "session=" + secret)
                .header(" x-zone-credential", secret)
                .header(" X-Trace ", "1")
                .build();

        assertThat(payload.getHeaders()).containsOnlyKeys("X-Trace");
        assertThat(payload.getHeader("X-Trace")).contains("1");
        assertThat(payload.toString()).doesNotContain(secret);
        assertThat(RawPayload.isSensitiveHeader(" Authorization ")).isTrue();
    }

    /** #10: toBuilder() round-trips body, charset, status, headers and the registered sensitive names. */
    @Test
    void rawPayloadToBuilderRoundTripsAndKeepsSensitiveNames() {
        String secret = "zone-1f2e3d4c-unique";
        RawPayload original = RawPayload.builder("application/json", "{}".getBytes(StandardCharsets.UTF_8))
                .charset(StandardCharsets.UTF_8)
                .httpStatus(201)
                .header("X-Trace", "1")
                .header("X-Trace", "2")
                .sensitiveHeader("x-zone-credential")
                .build();

        RawPayload copy = original.toBuilder().build();
        RawPayload changed = original.toBuilder().httpStatus(500).build();
        RawPayload refilled = original.toBuilder().header("X-Zone-Credential", secret).build();

        assertThat(copy).isEqualTo(original);
        assertThat(copy.hashCode()).isEqualTo(original.hashCode());
        assertThat(copy.getHeaders().get("x-trace")).containsExactly("1", "2");
        assertThat(copy.getCharset()).contains(StandardCharsets.UTF_8);
        assertThat(changed).isNotEqualTo(original);
        assertThat(changed.getHttpStatus()).hasValue(500);
        assertThat(refilled.getHeaders()).containsOnlyKeys("X-Trace");
        assertThat(refilled.toString()).doesNotContain(secret);
    }
}
