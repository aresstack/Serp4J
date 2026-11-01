package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserReadiness;
import com.aresstack.serp4j.browser.BrowserSessionOptions;
import com.aresstack.serp4j.browser.BrowserUnavailableException;
import com.aresstack.serp4j.browser.EngineFamily;
import com.aresstack.serp4j.browser.IsolationLevel;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Everything that works without a driver and without a browser. */
class RuntimeWithoutBrowserTest {

    @Test
    void builder_rejects_invalid_values() {
        PlaywrightBrowserRuntime.Builder builder = PlaywrightBrowserRuntime.builder();
        assertThatThrownBy(() -> builder.workers(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.maxConcurrentSessions(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.launchArgument(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.startTimeout(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.driverEnvironment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RemotePlaywrightRuntime.builder(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remote_runtime_does_not_accept_local_launch_settings() {
        assertThatThrownBy(() -> RemotePlaywrightRuntime.builder("ws://127.0.0.1:1/x")
                .executablePath(Paths.get("/usr/bin/chromium")).build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void info_is_available_without_starting_anything() {
        PlaywrightBrowserRuntime runtime = PlaywrightBrowserRuntime.builder().maxConcurrentSessions(3).build();
        try {
            assertThat(runtime.getInfo().getEngineFamily()).isEqualTo(EngineFamily.CHROMIUM);
            assertThat(runtime.getInfo().getIsolationLevel()).isEqualTo(IsolationLevel.CONTEXT);
            assertThat(runtime.getInfo().getMaxConcurrentSessions()).isEqualTo(3);
        } finally {
            runtime.close();
        }
    }

    @Test
    void without_driver_module_probe_and_open_session_fail_typed() {
        PlaywrightBrowserRuntime runtime = PlaywrightBrowserRuntime.builder().build();
        try {
            assertThat(runtime.probe().getStatus()).isEqualTo(BrowserReadiness.Status.DRIVER_NOT_FOUND);
            assertThatThrownBy(() -> runtime.openSession(BrowserSessionOptions.defaults()))
                    .isInstanceOf(BrowserUnavailableException.class)
                    .satisfies(e -> assertThat(((BrowserUnavailableException) e).getReadiness().getStatus())
                            .isEqualTo(BrowserReadiness.Status.DRIVER_NOT_FOUND));
        } finally {
            runtime.close();
        }
    }

    @Test
    void close_is_idempotent_and_probe_reports_closed() {
        PlaywrightBrowserRuntime runtime = PlaywrightBrowserRuntime.builder().build();
        runtime.close();
        runtime.close();
        assertThat(runtime.probe().getStatus()).isEqualTo(BrowserReadiness.Status.CLOSED);
    }
}
