package com.aresstack.serp4j.browser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlPolicyTest {

    @Test
    void allow_all_web_allows_http_and_https_only() {
        UrlPolicy policy = UrlPolicy.allowAllWeb();
        assertThat(policy.isAllowed("https://www.google.com/search?q=x")).isTrue();
        assertThat(policy.isAllowed("http://localhost:8080/")).isTrue();
        assertThat(policy.isAllowed("about:blank")).isTrue();
        assertThat(policy.isAllowed("file:///etc/passwd")).isFalse();
        assertThat(policy.isAllowed("data:text/html,<b>x</b>")).isFalse();
        assertThat(policy.isAllowed("javascript:alert(1)")).isFalse();
        assertThat(policy.isAllowed("ftp://example.com/")).isFalse();
        assertThat(policy.isAllowed("not a url")).isFalse();
        assertThat(policy.isAllowed(null)).isFalse();
    }

    @Test
    void host_globs_match_case_insensitively() {
        UrlPolicy policy = UrlPolicy.allowHosts("*.google.com", "google.*", "consent.google.com");
        assertThat(policy.isAllowed("https://www.google.com/search")).isTrue();
        assertThat(policy.isAllowed("https://WWW.Google.COM/")).isTrue();
        assertThat(policy.isAllowed("https://google.de/")).isTrue();
        assertThat(policy.isAllowed("https://google.co.uk/")).as("* is exactly one label").isFalse();
        assertThat(policy.isAllowed("https://consent.google.com/ml")).isTrue();
        assertThat(policy.isAllowed("https://google.com.evil.example/")).isFalse();
        assertThat(policy.isAllowed("https://a.b.google.com/")).as("leading *. spans several labels").isTrue();
        assertThat(policy.isAllowed("https://evil.example/?u=www.google.com")).isFalse();
        assertThat(policy.isAllowed("https://notgoogle.com/")).isFalse();
        assertThat(policy.isAllowed("file:///www.google.com")).isFalse();
    }

    @Test
    void google_domains_with_multi_part_tlds_are_configured_explicitly() {
        UrlPolicy policy = UrlPolicy.allowHosts("google.co.uk", "*.google.co.uk", "google.com.au", "*.google.com.au",
                "google.de", "*.google.de");
        assertThat(policy.isAllowed("https://www.google.co.uk/search?q=x")).isTrue();
        assertThat(policy.isAllowed("https://google.co.uk/")).isTrue();
        assertThat(policy.isAllowed("https://consent.google.co.uk/ml")).isTrue();
        assertThat(policy.isAllowed("https://www.google.com.au/")).isTrue();
        assertThat(policy.isAllowed("https://www.google.de/")).isTrue();
        assertThat(policy.isAllowed("https://www.google.fr/")).as("not configured").isFalse();
        assertThat(policy.isAllowed("https://google.co.uk.evil.example/")).isFalse();
        assertThat(policy.isAllowed("https://evilgoogle.co.uk/")).isFalse();
    }

    @Test
    void subdomain_glob_does_not_match_the_apex() {
        UrlPolicy policy = UrlPolicy.allowHosts("*.example.com");
        assertThat(policy.isAllowed("https://a.example.com/")).isTrue();
        assertThat(policy.isAllowed("https://example.com/")).isFalse();
        assertThat(policy.isAllowed("https://a.example.com.attacker.test/")).isFalse();
    }

    @Test
    void patterns_must_be_hosts() {
        assertThatThrownBy(() -> UrlPolicy.allowHosts("https://example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlPolicy.allowHosts()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlPolicy.allowHosts(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equality_by_patterns() {
        assertThat(UrlPolicy.allowHosts("A.example")).isEqualTo(UrlPolicy.allowHosts("a.example"));
        assertThat(UrlPolicy.allowAllWeb()).isNotEqualTo(UrlPolicy.allowHosts("a.example"));
    }
}
