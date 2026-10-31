package com.aresstack.serp4j.core.model.ext;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
}
