package com.aresstack.serp4j.browser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrowserValueTest {

    @Test
    void converts_driver_values_and_back() {
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("title", "Serp4J");
        raw.put("count", 3);
        raw.put("ratio", 0.5);
        raw.put("flag", true);
        raw.put("nothing", null);
        raw.put("items", Arrays.asList("a", "b"));

        BrowserValue value = BrowserValue.fromJava(raw);

        assertThat(value.getType()).isEqualTo(BrowserValue.Type.MAP);
        assertThat(value.getString("title")).contains("Serp4J");
        assertThat(value.path("count").get().asLong()).isEqualTo(3L);
        assertThat(value.path("ratio").get().asDouble()).isEqualTo(0.5);
        assertThat(value.path("flag").get().asBoolean()).isTrue();
        assertThat(value.path("nothing").get().isNull()).isTrue();
        assertThat(value.path("items", 1).get().asString()).isEqualTo("b");
        assertThat(value.path("items", 2)).isEmpty();
        assertThat(value.path("title", 0)).isEmpty();
        assertThat(new ArrayList<String>(value.asMap().keySet()))
                .containsExactly("title", "count", "ratio", "flag", "nothing", "items");

        @SuppressWarnings("unchecked")
        Map<String, Object> back = (Map<String, Object>) value.toJava();
        assertThat(back.get("count")).isEqualTo(3.0);
        assertThat(back.get("items")).isEqualTo(Arrays.asList("a", "b"));
        assertThat(BrowserValue.fromJava(back)).isEqualTo(value);
    }

    @Test
    void numbers_compare_by_value_and_integral_checks_are_strict() {
        assertThat(BrowserValue.fromJava(1)).isEqualTo(BrowserValue.fromJava(1.0));
        assertThat(BrowserValue.fromJava(1).hashCode()).isEqualTo(BrowserValue.of(1.0).hashCode());
        assertThat(BrowserValue.of(2.5).toString()).isEqualTo("2.5");
        assertThat(BrowserValue.of(7).toString()).isEqualTo("7");
        assertThatThrownBy(() -> BrowserValue.of(2.5).asLong()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BrowserValue.of(Double.NaN).asLong()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrong_type_access_fails_readably() {
        assertThatThrownBy(() -> BrowserValue.of("x").asList())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("expected LIST but was STRING");
        assertThatThrownBy(() -> BrowserValue.fromJava(new Object()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unsupported value type");
    }

    @Test
    void collections_are_copied_and_unmodifiable() {
        List<BrowserValue> source = new ArrayList<BrowserValue>();
        source.add(BrowserValue.of("a"));
        BrowserValue list = BrowserValue.list(source);
        source.add(BrowserValue.of("b"));
        assertThat(list.asList()).hasSize(1);
        assertThatThrownBy(() -> list.asList().add(BrowserValue.nullValue()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
