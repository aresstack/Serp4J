package com.aresstack.serp4j.mapping.convert;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueConvertersTest {

    private static Object convert(String name, StructuredValue value) {
        return ValueConverters.standard().get(name).get().convert(value);
    }

    private static StructuredValue s(String text) {
        return StructuredValue.of(text);
    }

    @Test
    void numbers() {
        assertThat(convert("int", s("3"))).isEqualTo(3);
        assertThat(convert("int", s("3.0"))).isEqualTo(3);
        assertThat(convert("int", StructuredValue.of(7))).isEqualTo(7);
        assertThat(convert("int", s(" "))).isNull();
        assertThat(convert("long", s("9999999999"))).isEqualTo(9999999999L);
        assertThat(convert("decimal", s("0.002"))).isEqualTo(new BigDecimal("0.002"));
        assertThatThrownBy(() -> convert("int", s("3.5"))).isInstanceOf(ValueConversionException.class);
        assertThatThrownBy(() -> convert("int", s("abc"))).isInstanceOf(ValueConversionException.class);
        assertThatThrownBy(() -> convert("int", s("9999999999"))).isInstanceOf(ValueConversionException.class);
        assertThatThrownBy(() -> convert("int", StructuredValue.objectBuilder().build()))
                .isInstanceOf(ValueConversionException.class);
    }

    @Test
    void text_boolean_html_host() {
        assertThat(convert("string", StructuredValue.of(new BigDecimal("1E+3")))).isEqualTo("1000");
        assertThat(convert("string", StructuredValue.of(true))).isEqualTo("true");
        assertThat(convert("boolean", s("1"))).isEqualTo(true);
        assertThat(convert("boolean", s("FALSE"))).isEqualTo(false);
        assertThatThrownBy(() -> convert("boolean", s("yes"))).isInstanceOf(ValueConversionException.class);
        assertThat(convert("stripHtml", s("<b>Fish</b> &amp; <i>Chips</i>&nbsp;&lt;3"))).isEqualTo("Fish & Chips <3");
        assertThat(convert("host", s("https://user:pw@Sub.Example.COM:8443/path?q=1"))).isEqualTo("sub.example.com");
        assertThat(convert("host", s("example.org/a"))).isEqualTo("example.org");
        assertThat(convert("host", s("//cdn.example.net/x"))).isEqualTo("cdn.example.net");
        assertThatThrownBy(() -> convert("host", s("https:///nohost"))).isInstanceOf(ValueConversionException.class);
    }

    @Test
    void dates() {
        Instant t = Instant.parse("2019-11-15T12:57:46Z");
        assertThat(convert("isoDate", s("2019-11-15T12:57:46Z"))).isEqualTo(t);
        assertThat(convert("isoDate", s("2019-11-15T14:57:46+02:00"))).isEqualTo(t);
        assertThat(convert("isoDate", s("2019-11-15 12:57:46 +00:00"))).isEqualTo(t);
        assertThat(convert("isoDate", s("2019-11-15 12:57:46"))).isEqualTo(t);
        assertThat(convert("isoDate", s("2019-11-15"))).isEqualTo(Instant.parse("2019-11-15T00:00:00Z"));
        assertThat(convert("epochSeconds", StructuredValue.of(1573822666))).isEqualTo(t);
        assertThat(convert("epochMillis", s("1573822666000"))).isEqualTo(t);
        assertThatThrownBy(() -> convert("isoDate", s("vor 3 Tagen"))).isInstanceOf(ValueConversionException.class);
    }

    @Test
    void raw_keeps_the_tree() {
        StructuredValue tree = StructuredValue.objectBuilder().put("a", 1).build();
        assertThat(convert("raw", tree)).isSameAs(tree);
    }

    @Test
    void registry_is_extensible_but_never_overwrites() {
        ValueConverters custom = ValueConverters.standard().toBuilder().register("upper",
                v -> v.asText().get().toUpperCase(java.util.Locale.ROOT)).build();
        assertThat(custom.contains("upper")).isTrue();
        assertThat(custom.contains("int")).isTrue();
        assertThat(ValueConverters.standard().contains("upper")).isFalse();
        assertThatThrownBy(() -> ValueConverters.standard().toBuilder().register("int", v -> v))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already registered");
        assertThatThrownBy(() -> ValueConverters.builder().register("1bad", v -> v))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
