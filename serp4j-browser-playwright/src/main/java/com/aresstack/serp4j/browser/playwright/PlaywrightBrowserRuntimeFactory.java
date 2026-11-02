package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserDiscovery;
import com.aresstack.serp4j.browser.BrowserInstallation;
import com.aresstack.serp4j.browser.BrowserProduct;
import com.aresstack.serp4j.browser.BrowserRuntime;
import com.aresstack.serp4j.browser.BrowserRuntimeFactory;
import com.aresstack.serp4j.browser.BrowserSpec;

import java.util.Map;
import java.util.Optional;

/**
 * {@link BrowserRuntimeFactory} for Chrome, Edge, Chromium, Playwright-Firefox and WebKit (ADR-020). Maps the
 * backend-neutral {@link BrowserSpec} onto {@link PlaywrightBrowserRuntime.Builder}; Camoufox has its own factory.
 */
public final class PlaywrightBrowserRuntimeFactory implements BrowserRuntimeFactory {

    /** Backend id. */
    public static final String BACKEND_ID = "playwright";

    private final BrowserDiscovery discovery;

    /** ServiceLoader constructor using {@link BrowserDiscovery#system()}. */
    public PlaywrightBrowserRuntimeFactory() {
        this(BrowserDiscovery.system());
    }

    /**
     * @param discovery discovery used for installed browsers without a Playwright channel
     */
    public PlaywrightBrowserRuntimeFactory(BrowserDiscovery discovery) {
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
        BrowserProduct p = spec.getProduct();
        if (BrowserProduct.CAMOUFOX.equals(p)) {
            return "Camoufox is handled by serp4j-browser-camoufox";
        }
        if (type(p) == null) {
            return "unknown product " + p;
        }
        switch (spec.getSource()) {
            case CONNECT:
                String e = spec.getEndpoint().get();
                return e.startsWith("ws://") || e.startsWith("wss://") ? null
                        : "only Playwright server endpoints (ws://, wss://) are supported, CDP follows";
            case INSTALLED:
                if (!(BrowserProduct.CHROME.equals(p) || BrowserProduct.EDGE.equals(p)
                        || BrowserProduct.CHROMIUM.equals(p) || BrowserProduct.FIREFOX.equals(p)
                        || BrowserProduct.WEBKIT.equals(p))) {
                    // An open product (e.g. brave) has no channel here; launching bundled Chromium instead would
                    // silently ignore the host's choice. Use BrowserSpec.executable or a product-specific factory.
                    return "no installed-browser lookup for " + p + "; pass BrowserSpec.executable";
                }
                if (BrowserProduct.FIREFOX.equals(p)) {
                    return "a stock Firefox cannot be driven by Playwright; use BrowserSpec.executable with "
                            + "Playwright's Firefox build or Camoufox";
                }
                return null;
            default:
                return null;
        }
    }

    @Override
    public BrowserRuntime create(BrowserSpec spec) {
        String reason = unsupportedReason(spec);
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
        BrowserProduct p = spec.getProduct();
        PlaywrightBrowserRuntime.Builder b = PlaywrightBrowserRuntime.builder().browserType(type(p))
                .headless(spec.isHeadless());
        switch (spec.getSource()) {
            case CONNECT:
                b.wsEndpoint(spec.getEndpoint().get());
                break;
            case EXECUTABLE:
                b.executablePath(spec.getExecutable().get());
                break;
            default:
                if (BrowserProduct.CHROME.equals(p)) {
                    b.channel("chrome");
                } else if (BrowserProduct.EDGE.equals(p)) {
                    b.channel("msedge");
                } else if (BrowserProduct.CHROMIUM.equals(p)) {
                    // Prefer a system Chromium; otherwise Playwright's own build.
                    Optional<BrowserInstallation> found = discovery.find(p);
                    if (found.isPresent()) {
                        b.executablePath(found.get().getExecutable());
                    }
                }
                break;
        }
        for (String arg : spec.getLaunchArguments()) {
            b.launchArgument(arg);
        }
        for (Map.Entry<String, String> env : spec.getEnvironment().entrySet()) {
            b.browserEnvironment(env.getKey(), env.getValue());
        }
        if (spec.getProfileRoot().isPresent()) {
            b.profileDirectory(spec.getProfileRoot().get());
        }
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

    static PlaywrightBrowserType type(BrowserProduct p) {
        switch (p.getEngineFamily()) {
            case CHROMIUM:
                return PlaywrightBrowserType.CHROMIUM;
            case FIREFOX:
                return PlaywrightBrowserType.FIREFOX;
            case WEBKIT:
                return PlaywrightBrowserType.WEBKIT;
            default:
                return null;
        }
    }
}
