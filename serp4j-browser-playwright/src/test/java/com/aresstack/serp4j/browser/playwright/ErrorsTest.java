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
    void messages_are_shortened_to_their_first_line() {
        BrowserException e = Errors.map(new PlaywrightException("first line\nsecond line"), "navigation");
        assertThat(e.getMessage()).isEqualTo("navigation failed: first line");
    }

    @Test
    void missing_browser_is_reported_as_not_installed_without_download() {
        BrowserReadiness readiness = Errors.startFailure(new PlaywrightException(
                "Executable doesn't exist at /root/.cache/ms-playwright/chromium-1/chrome"));
        assertThat(readiness.getStatus()).isEqualTo(BrowserReadiness.Status.BROWSER_NOT_INSTALLED);
        assertThat(readiness.getDetail()).contains("never downloads");
    }
}
