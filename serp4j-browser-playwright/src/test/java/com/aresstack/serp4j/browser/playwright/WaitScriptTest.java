package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.ElementSelector;
import com.aresstack.serp4j.browser.WaitCondition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WaitScriptTest {

    @Test
    void conditions_become_plain_data_for_the_fixed_predicate() {
        Map<String, Object> spec = WaitScript.spec(WaitCondition.allOf(
                WaitCondition.countAtLeast(ElementSelector.css("div.result"), 3),
                WaitCondition.urlMatches("^https://example\\.org/"),
                WaitCondition.domQuiet(Duration.ofMillis(250))));

        assertThat(spec).containsEntry("kind", "all");
        List<?> children = (List<?>) spec.get("children");
        assertThat(child(children, 0)).containsEntry("kind", "count").containsEntry("count", 3)
                .containsEntry("css", "div.result");
        assertThat(child(children, 1)).containsEntry("kind", "url")
                .containsEntry("pattern", "^https://example\\.org/");
        assertThat(child(children, 2)).containsEntry("kind", "quiet").containsEntry("ms", 250);
    }

    @Test
    void the_predicate_is_a_constant_and_never_contains_caller_input() {
        Map<String, Object> spec = WaitScript.spec(WaitCondition.present(ElementSelector.css("a[href*=\"'); x('\"]")));
        assertThat(spec).containsEntry("css", "a[href*=\"'); x('\"]");
        assertThat(WaitScript.PREDICATE).doesNotContain("x('");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(List<?> children, int index) {
        return (Map<String, Object>) children.get(index);
    }
}
