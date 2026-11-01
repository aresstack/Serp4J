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
                WaitCondition.domQuiet(Duration.ofMillis(250))), "https://example.org/x");

        assertThat(spec).containsEntry("kind", "all");
        List<?> children = (List<?>) spec.get("children");
        assertThat(child(children, 0)).containsEntry("kind", "count").containsEntry("count", 3)
                .containsEntry("css", "div.result");
        assertThat(child(children, 1)).containsEntry("kind", "url").containsEntry("matches", true);
        assertThat(child(children, 2)).containsEntry("kind", "quiet").containsEntry("ms", 250);
    }

    @Test
    void the_predicate_is_a_constant_and_never_contains_caller_input() {
        Map<String, Object> spec = WaitScript.spec(WaitCondition.present(ElementSelector.css("a[href*=\"'); x('\"]")), "");
        assertThat(spec).containsEntry("css", "a[href*=\"'); x('\"]");
        assertThat(WaitScript.PREDICATE).doesNotContain("x('");
    }

    @Test
    void url_conditions_use_java_regex_semantics() {
        assertThat(WaitScript.spec(WaitCondition.urlMatches("(?i)EXAMPLE\\.ORG"), "https://example.org/"))
                .containsEntry("matches", true);
        assertThat(WaitScript.spec(WaitCondition.urlMatches(java.util.regex.Pattern.quote("?q=a+b")),
                "https://example.org/s?q=a+b")).containsEntry("matches", true);
        assertThat(WaitScript.spec(WaitCondition.urlMatches("^https://other\\."), "https://example.org/"))
                .containsEntry("matches", false);
        assertThat(WaitScript.hasUrlCondition(WaitCondition.allOf(
                WaitCondition.present(ElementSelector.css("a")), WaitCondition.urlMatches("x")))).isTrue();
        assertThat(WaitScript.hasUrlCondition(WaitCondition.present(ElementSelector.css("a")))).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(List<?> children, int index) {
        return (Map<String, Object>) children.get(index);
    }
}
