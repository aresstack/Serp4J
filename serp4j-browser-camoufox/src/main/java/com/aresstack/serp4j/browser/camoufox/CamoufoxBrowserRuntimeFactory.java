package com.aresstack.serp4j.browser.camoufox;

import com.aresstack.serp4j.browser.BrowserDiscovery;
import com.aresstack.serp4j.browser.BrowserInstallation;
import com.aresstack.serp4j.browser.BrowserProduct;
import com.aresstack.serp4j.browser.BrowserRuntime;
import com.aresstack.serp4j.browser.BrowserRuntimeFactory;
import com.aresstack.serp4j.browser.BrowserSpec;
import com.aresstack.serp4j.browser.playwright.PlaywrightBrowserRuntime;
import com.aresstack.serp4j.browser.playwright.PlaywrightBrowserType;
import com.aresstack.serp4j.browser.playwright.RemotePlaywrightRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link BrowserRuntimeFactory} for Camoufox (ADR-020, E-5/E-6). Camoufox is a patched Firefox that Playwright drives
 * like its own Firefox build; this factory launches it through the Playwright adapter with
 * {@link PlaywrightBrowserType#FIREFOX} and the Camoufox executable.
 *
 * <p>Nothing is downloaded or installed: the host passes the executable ({@code BrowserSpec.executable(CAMOUFOX, …)})
 * or {@link BrowserDiscovery} finds a portable installation ({@code BrowserSpec.installed(CAMOUFOX)}). A fingerprint
 * configuration (Camoufox JSON) is passed through unchanged from option {@value #OPTION_CONFIG} as the environment
 * variables {@code CAMOU_CONFIG_1..n}; generating fingerprints is the job of the shared Camoufox library (E-5).</p>
 */
public final class CamoufoxBrowserRuntimeFactory implements BrowserRuntimeFactory {

    /** Backend id. */
    public static final String BACKEND_ID = "camoufox";
    /** Option carrying the Camoufox configuration JSON. */
    public static final String OPTION_CONFIG = "camoufox.config";
    /** Chunk size of {@code CAMOU_CONFIG_n}; Windows limits a single environment value. */
    static final int CONFIG_CHUNK = 2047;

    private final BrowserDiscovery discovery;

    /** ServiceLoader constructor using {@link BrowserDiscovery#system()}. */
    public CamoufoxBrowserRuntimeFactory() {
        this(BrowserDiscovery.system());
    }

    /**
     * @param discovery discovery for {@code installed(CAMOUFOX)}
     */
    public CamoufoxBrowserRuntimeFactory(BrowserDiscovery discovery) {
        if (discovery == null) {
            throw new NullPointerException("discovery must not be null");
        }
        this.discovery = discovery;
    }

    @Override
    public String getBackendId() {
        return BACKEND_ID;
    }

    @Override
    public String unsupportedReason(BrowserSpec spec) {
        if (!BrowserProduct.CAMOUFOX.equals(spec.getProduct())) {
            return "only " + BrowserProduct.CAMOUFOX;
        }
        switch (spec.getSource()) {
            case CONNECT:
                String e = spec.getEndpoint().get();
                return e.startsWith("ws://") || e.startsWith("wss://") ? null
                        : "Camoufox is reachable only through a Playwright server endpoint (ws://, wss://)";
            case EXECUTABLE:
                return Files.isRegularFile(spec.getExecutable().get()) ? null
                        : "Camoufox executable does not exist: " + spec.getExecutable().get();
            default:
                return discovery.find(BrowserProduct.CAMOUFOX).isPresent() ? null
                        : "no portable Camoufox found at " + discovery.candidates(BrowserProduct.CAMOUFOX);
        }
    }

    @Override
    public BrowserRuntime create(BrowserSpec spec) {
        String reason = unsupportedReason(spec);
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
        PlaywrightBrowserRuntime.Builder b;
        if (spec.getSource() == BrowserSpec.Source.CONNECT) {
            b = RemotePlaywrightRuntime.builder(spec.getEndpoint().get());
        } else {
            Path exe = spec.getSource() == BrowserSpec.Source.EXECUTABLE ? spec.getExecutable().get()
                    : installed().getExecutable();
            b = PlaywrightBrowserRuntime.builder().executablePath(exe);
            for (String arg : spec.getLaunchArguments()) {
                b.launchArgument(arg);
            }
            for (Map.Entry<String, String> env : environment(spec).entrySet()) {
                b.browserEnvironment(env.getKey(), env.getValue());
            }
            if (spec.getProfileRoot().isPresent()) {
                b.profileDirectory(spec.getProfileRoot().get());
            }
        }
        b.browserType(PlaywrightBrowserType.FIREFOX).headless(spec.isHeadless());
        if (spec.getMaxConcurrentSessions() > 0) {
            b.maxConcurrentSessions(spec.getMaxConcurrentSessions());
        }
        if (spec.getStartTimeout().isPresent()) {
            b.startTimeout(spec.getStartTimeout().get());
        }
        if (spec.getCloseTimeout().isPresent()) {
            b.closeTimeout(spec.getCloseTimeout().get());
        }
        return b.build();
    }

    private BrowserInstallation installed() {
        Optional<BrowserInstallation> found = discovery.find(BrowserProduct.CAMOUFOX);
        if (!found.isPresent()) {
            throw new IllegalStateException("Camoufox disappeared after the support check");
        }
        return found.get();
    }

    /** Spec environment plus the configuration split into {@code CAMOU_CONFIG_1..n}. */
    static Map<String, String> environment(BrowserSpec spec) {
        Map<String, String> env = new LinkedHashMap<String, String>(spec.getEnvironment());
        Optional<String> config = spec.getOption(OPTION_CONFIG);
        if (config.isPresent()) {
            String json = config.get();
            int n = 1;
            for (int i = 0; i < json.length(); i += CONFIG_CHUNK) {
                env.put("CAMOU_CONFIG_" + n++, json.substring(i, Math.min(json.length(), i + CONFIG_CHUNK)));
            }
        }
        return env;
    }
}
