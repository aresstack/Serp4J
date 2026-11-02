package com.aresstack.serp4j.browser;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserSpecAndDiscoveryTest {

    @Test
    void productIsOpenButKnownIdsKeepTheirEngine() {
        assertSame(BrowserProduct.EDGE, BrowserProduct.of("Edge", EngineFamily.CHROMIUM));
        assertEquals("brave", BrowserProduct.of("brave", EngineFamily.CHROMIUM).getId());
        assertThrows(IllegalArgumentException.class, () -> BrowserProduct.of("camoufox", EngineFamily.CHROMIUM));
        assertEquals(EngineFamily.FIREFOX, BrowserProduct.known("CAMOUFOX").get().getEngineFamily());
        assertFalse(BrowserProduct.known("opera").isPresent());
    }

    @Test
    void specSourcesAndConnectRestrictions() {
        BrowserSpec edge = BrowserSpec.installed(BrowserProduct.EDGE).headless(false).build();
        assertEquals(BrowserSpec.Source.INSTALLED, edge.getSource());
        assertFalse(edge.isHeadless());

        BrowserSpec camoufox = BrowserSpec.executable(BrowserProduct.CAMOUFOX, Paths.get("/opt/camoufox/camoufox"))
                .option("camoufox.config", "{}").environment("SECRET", "x").build();
        assertEquals(Paths.get("/opt/camoufox/camoufox"), camoufox.getExecutable().get());
        assertFalse(camoufox.toString().contains("x}"), "values are not printed");

        assertThrows(IllegalArgumentException.class, () -> BrowserSpec.connect(BrowserProduct.CHROMIUM, "file:///x"));
        assertThrows(IllegalStateException.class, () -> BrowserSpec.connect(BrowserProduct.CHROMIUM,
                "ws://127.0.0.1:3000/").launchArgument("--foo").build());
    }

    @Test
    void windowsDiscoveryUsesProgramFilesAndLocalAppData() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("ProgramFiles", "C:\\Program Files");
        env.put("ProgramFiles(x86)", "C:\\Program Files (x86)");
        env.put("LOCALAPPDATA", "C:\\Users\\a\\AppData\\Local");
        final Path edge = Paths.get("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe");
        final Path camoufox = Paths.get("C:\\Users\\a\\AppData\\Local\\camoufox\\camoufox.exe");
        BrowserDiscovery d = BrowserDiscovery.of(BrowserDiscovery.Os.WINDOWS, env,
                exists(edge, camoufox));
        assertEquals(Arrays.asList(new BrowserInstallation(BrowserProduct.EDGE, edge,
                        BrowserInstallation.Origin.INSTALLED),
                new BrowserInstallation(BrowserProduct.CAMOUFOX, camoufox, BrowserInstallation.Origin.PORTABLE)),
                d.findAll());
    }

    @Test
    void linuxDiscoveryPrefersPath() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("PATH", "/usr/local/bin:/usr/bin");
        env.put("HOME", "/home/a");
        Path chrome = Paths.get("/usr/bin/google-chrome");
        BrowserDiscovery d = BrowserDiscovery.of(BrowserDiscovery.Os.LINUX, env,
                exists(chrome, Paths.get("/opt/google/chrome/chrome")));
        assertEquals(chrome, d.find(BrowserProduct.CHROME).get().getExecutable());
        assertTrue(d.candidates(BrowserProduct.CAMOUFOX).contains(Paths.get("/home/a/.cache/camoufox/camoufox-bin")));
        assertFalse(d.find(BrowserProduct.EDGE).isPresent());
    }

    @Test
    void runtimesPicksFirstSupportingFactoryOrExplains() {
        BrowserSpec spec = BrowserSpec.installed(BrowserProduct.EDGE).build();
        BrowserUnavailableException none = assertThrows(BrowserUnavailableException.class,
                () -> BrowserRuntimes.create(spec, Collections.<BrowserRuntimeFactory>emptyList()));
        assertEquals(BrowserReadiness.Status.NO_BACKEND, none.getReadiness().getStatus());

        BrowserUnavailableException rejected = assertThrows(BrowserUnavailableException.class,
                () -> BrowserRuntimes.create(spec, Collections.singletonList(factory("a", "nope"))));
        assertTrue(rejected.getReadiness().getDetail().contains("a: nope"));
    }

    private static BrowserDiscovery.FileCheck exists(Path... paths) {
        final Set<Path> set = new HashSet<Path>(Arrays.asList(paths));
        return set::contains;
    }

    private static BrowserRuntimeFactory factory(final String id, final String reason) {
        return new BrowserRuntimeFactory() {
            @Override
            public String getBackendId() {
                return id;
            }

            @Override
            public String unsupportedReason(BrowserSpec spec) {
                return reason;
            }

            @Override
            public BrowserRuntime create(BrowserSpec spec) {
                throw new AssertionError();
            }
        };
    }
}
