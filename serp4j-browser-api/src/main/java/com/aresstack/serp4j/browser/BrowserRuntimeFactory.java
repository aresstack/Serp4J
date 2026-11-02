package com.aresstack.serp4j.browser;

/**
 * SPI turning a {@link BrowserSpec} into a {@link BrowserRuntime} (ADR-020). Implementations are found with
 * {@link java.util.ServiceLoader} by {@link BrowserRuntimes}; the Playwright adapter and the Camoufox module provide
 * one each.
 */
public interface BrowserRuntimeFactory {

    /** @return backend id, for example {@code playwright} or {@code camoufox} */
    String getBackendId();

    /**
     * @param spec browser description
     * @return {@code null} if this factory can create a runtime for the spec, otherwise a short reason
     */
    String unsupportedReason(BrowserSpec spec);

    /**
     * Creates the runtime without starting the browser; workers start lazily as before. The caller owns the
     * returned runtime and closes it (ADR-017). For {@link BrowserSpec.Source#CONNECT} closing never ends the
     * host's browser process.
     *
     * @param spec browser description accepted by {@link #unsupportedReason(BrowserSpec)}
     * @return the runtime
     */
    BrowserRuntime create(BrowserSpec spec);
}
