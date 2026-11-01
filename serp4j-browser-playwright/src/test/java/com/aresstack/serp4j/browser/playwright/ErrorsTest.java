package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserException;
import com.aresstack.serp4j.browser.BrowserReadiness;
import com.aresstack.serp4j.browser.BrowserSessionLostException;
import com.microsoft.playwright.PlaywrightException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorsTest {

    @Test
    void lost_connections_are_recognised_for_both_drivers() {
        assertThat(Errors.map(new PlaywrightException("Target page, context or browser has been closed"), "x"))
                .isInstanceOf(BrowserSessionLostException.class);
        assertThat(Errors.map(new PlaywrightException("Error {\n  message='java.io.IOException: Output closed"), "x"))
                .isInstanceOf(BrowserSessionLostException.class);
        assertThat(Errors.map(new PlaywrightException("net::ERR_NAME_NOT_RESOLVED"), "x"))
                .isExactlyInstanceOf(BrowserException.class);
    }

    @Test
    void messages_never_carry_the_driver_text() {
        PlaywrightException raw = new PlaywrightException("Error: TypeError: token=s3cr3t is not a function\nat x");
        BrowserException e = Errors.map(raw, "script s");
        assertThat(e.getMessage()).isEqualTo("script s failed (TypeError)").doesNotContain("s3cr3t");
        assertThat(e.getCause()).isSameAs(raw);
        assertThat(Errors.map(new PlaywrightException("page.goto: net::ERR_NAME_NOT_RESOLVED at https://x/?k=v"),
                "navigation").getMessage()).isEqualTo("navigation failed (net::ERR_NAME_NOT_RESOLVED)");
        assertThat(Errors.map(new PlaywrightException("cookie=abc"), "click").getMessage()).isEqualTo("click failed");
    }

    @Test
    void urls_in_messages_lose_credentials_query_and_fragment() {
        assertThat(Errors.describeUrl("https://user:pw@example.org:8443/a/b?key=secret#frag"))
                .isEqualTo("https://example.org:8443/a/b");
        assertThat(Errors.describeUrl("not a url")).isEqualTo("<url>");
    }

    @Test
    void missing_browser_is_reported_as_not_installed_without_download() {
        BrowserReadiness readiness = Errors.startFailure(new PlaywrightException(
                "Executable doesn't exist at /root/.cache/ms-playwright/chromium-1/chrome"));
        assertThat(readiness.getStatus()).isEqualTo(BrowserReadiness.Status.BROWSER_NOT_INSTALLED);
        assertThat(readiness.getDetail()).contains("never downloads");
    }
}
