package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserDiscovery;
import com.aresstack.serp4j.browser.BrowserProduct;
import com.aresstack.serp4j.browser.BrowserRuntime;
import com.aresstack.serp4j.browser.BrowserSpec;
import com.aresstack.serp4j.browser.EngineFamily;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class PlaywrightBrowserRuntimeFactoryTest {

    private final PlaywrightBrowserRuntimeFactory factory = new PlaywrightBrowserRuntimeFactory(
            BrowserDiscovery.of(BrowserDiscovery.Os.LINUX, Collections.<String, String>emptyMap(),
                    (Path p) -> false));

    @Test
    void supportsChromiumFamilyAndRejectsCamoufoxAndStockFirefox() {
        assertThat(factory.unsupportedReason(BrowserSpec.installed(BrowserProduct.EDGE).build())).isNull();
        assertThat(factory.unsupportedReason(BrowserSpec.installed(BrowserProduct.CHROME).build())).isNull();
        assertThat(factory.unsupportedReason(BrowserSpec.installed(BrowserProduct.CAMOUFOX).build())).isNotNull();
        assertThat(factory.unsupportedReason(BrowserSpec.installed(BrowserProduct.FIREFOX).build()))
                .contains("stock Firefox");
        assertThat(factory.unsupportedReason(BrowserSpec.installed(BrowserProduct.of("brave",
                EngineFamily.CHROMIUM)).build())).contains("BrowserSpec.executable");
        assertThat(factory.unsupportedReason(BrowserSpec.executable(BrowserProduct.of("brave",
                EngineFamily.CHROMIUM), Paths.get("/opt/brave/brave")).build())).isNull();
        assertThat(factory.unsupportedReason(BrowserSpec.connect(BrowserProduct.CHROMIUM,
                "http://127.0.0.1:9222").build())).contains("CDP");
    }

    @Test
    void createsRuntimeWithoutStartingTheBrowser() {
        try (BrowserRuntime runtime = factory.create(BrowserSpec.installed(BrowserProduct.EDGE).headless(false)
                .build())) {
            assertThat(runtime.getInfo().getEngineFamily()).isEqualTo(EngineFamily.CHROMIUM);
            assertThat(runtime.getInfo().isHeadless()).isFalse();
        }
        try (BrowserRuntime runtime = factory.create(BrowserSpec.executable(BrowserProduct.FIREFOX,
                Paths.get("/opt/ms-playwright/firefox/firefox")).build())) {
            assertThat(runtime.getInfo().getEngineFamily()).isEqualTo(EngineFamily.FIREFOX);
        }
    }
}
