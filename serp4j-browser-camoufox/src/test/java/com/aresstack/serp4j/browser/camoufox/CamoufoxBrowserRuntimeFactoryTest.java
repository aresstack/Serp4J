package com.aresstack.serp4j.browser.camoufox;

import com.aresstack.serp4j.browser.BrowserDiscovery;
import com.aresstack.serp4j.browser.BrowserProduct;
import com.aresstack.serp4j.browser.BrowserSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CamoufoxBrowserRuntimeFactoryTest {

    private final CamoufoxBrowserRuntimeFactory factory = new CamoufoxBrowserRuntimeFactory(
            BrowserDiscovery.of(BrowserDiscovery.Os.LINUX, Collections.<String, String>emptyMap(),
                    (Path p) -> false));

    @Test
    void onlyCamoufox() {
        assertThat(factory.unsupportedReason(BrowserSpec.installed(BrowserProduct.EDGE).build())).isNotNull();
        assertThat(factory.unsupportedReason(BrowserSpec.installed(BrowserProduct.CAMOUFOX).build()))
                .startsWith("no portable Camoufox found");
        assertThat(factory.unsupportedReason(BrowserSpec.executable(BrowserProduct.CAMOUFOX,
                Paths.get("/does/not/exist")).build())).contains("does not exist");
        assertThat(factory.unsupportedReason(BrowserSpec.connect(BrowserProduct.CAMOUFOX,
                "ws://127.0.0.1:3000/").build())).isNull();
    }

    @Test
    void configIsSplitIntoCamouConfigChunks() {
        StringBuilder json = new StringBuilder("{\"a\":\"");
        for (int i = 0; i < 3000; i++) {
            json.append('x');
        }
        json.append("\"}");
        Map<String, String> env = CamoufoxBrowserRuntimeFactory.environment(BrowserSpec.executable(
                BrowserProduct.CAMOUFOX, Paths.get("camoufox")).option(CamoufoxBrowserRuntimeFactory.OPTION_CONFIG,
                json.toString()).environment("TZ", "Europe/Berlin").build());
        assertThat(env).containsKeys("TZ", "CAMOU_CONFIG_1", "CAMOU_CONFIG_2").doesNotContainKey("CAMOU_CONFIG_3");
        assertThat(env.get("CAMOU_CONFIG_1") + env.get("CAMOU_CONFIG_2")).isEqualTo(json.toString());
    }
}
