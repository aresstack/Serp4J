package com.aresstack.serp4j.mapping.path;

import com.aresstack.serp4j.core.model.ext.StructuredValue;
import com.aresstack.serp4j.json.JsonTrees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TreePathTest {

    private final StructuredValue tree = JsonTrees.read("{\"a\":{\"b\":[{\"c\":1},{\"c\":2},{\"d\":3}]},"
            + "\"@type\":\"x\",\"list\":[[1,2],[3]],\"n\":null}");

    @Test
    void names_indices_and_wildcards() {
        assertThat(TreePath.parse("$.a.b[0].c").select(tree)).containsExactly(StructuredValue.of(1));
        assertThat(TreePath.parse("a.b[*].c").select(tree)).containsExactly(StructuredValue.of(1), StructuredValue.of(2));
        assertThat(TreePath.parse("$.list[*][*]").select(tree)).hasSize(3);
        assertThat(TreePath.parse("$.list[1][0]").select(tree)).containsExactly(StructuredValue.of(3));
        assertThat(TreePath.parse("$.'@type'").select(tree)).containsExactly(StructuredValue.of("x"));
        assertThat(TreePath.parse("$").select(tree)).containsExactly(tree);
        assertThat(TreePath.parse("[1]").select(JsonTrees.read("[\"x\",\"y\"]"))).containsExactly(StructuredValue.of("y"));
    }

    @Test
    void unresolvable_steps_yield_no_match() {
        assertThat(TreePath.parse("$.a.b[9].c").select(tree)).isEmpty();
        assertThat(TreePath.parse("$.a[0]").select(tree)).isEmpty();
        assertThat(TreePath.parse("$.list.x").select(tree)).isEmpty();
        assertThat(TreePath.parse("$.missing[*]").select(tree)).isEmpty();
        assertThat(TreePath.parse("$.n").select(tree)).containsExactly(StructuredValue.nullValue());
    }

    @Test
    void locations_are_concrete() {
        assertThat(TreePath.parse("$.a.b[*].c").locate(tree)).extracting(m -> m.getLocation().toString())
                .containsExactly("$.a.b[0].c", "$.a.b[1].c");
        assertThat(TreePath.parse("$.'@type'").locate(tree).get(0).getLocation().toString()).isEqualTo("$.'@type'");
    }

    @Test
    void equality_ignores_the_optional_dollar() {
        assertThat(TreePath.parse("$.a.b")).isEqualTo(TreePath.parse("a.b"));
        assertThat(TreePath.parse("a[*]").isMultiValued()).isTrue();
        assertThat(TreePath.parse("a[0]").isMultiValued()).isFalse();
        assertThat(TreePath.parse(" $ ").isRoot()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "$.", "$..a", "a.", "a[", "a[x]", "a[-1]", "a[?(@.x)]", "$.''", "$.'open", "a b", "$.[0]",
            "a.*", "$a"})
    void invalid_paths_are_rejected(String path) {
        assertThatThrownBy(() -> TreePath.parse(path)).isInstanceOf(IllegalArgumentException.class);
    }
}
