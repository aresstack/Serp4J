package com.aresstack.serp4j.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/** Entry point creating a {@link BrowserRuntime} from a {@link BrowserSpec} (ADR-020). */
public final class BrowserRuntimes {

    private BrowserRuntimes() {
    }

    /**
     * Uses the factories visible to this class' loader.
     *
     * @param spec browser description chosen by the host
     * @return a runtime owned by the caller
     * @throws BrowserUnavailableException if no factory supports the spec
     */
    public static BrowserRuntime create(BrowserSpec spec) {
        return create(spec, BrowserRuntimes.class.getClassLoader());
    }

    /**
     * @param spec        browser description
     * @param classLoader loader for {@link ServiceLoader}
     * @return a runtime owned by the caller
     */
    public static BrowserRuntime create(BrowserSpec spec, ClassLoader classLoader) {
        List<BrowserRuntimeFactory> factories = new ArrayList<BrowserRuntimeFactory>();
        for (BrowserRuntimeFactory f : ServiceLoader.load(BrowserRuntimeFactory.class, classLoader)) {
            factories.add(f);
        }
        return create(spec, factories);
    }

    /**
     * @param spec      browser description
     * @param factories candidate factories in priority order
     * @return a runtime from the first factory that supports the spec
     * @throws BrowserUnavailableException listing every factory's reason if none supports it
     */
    public static BrowserRuntime create(BrowserSpec spec, List<? extends BrowserRuntimeFactory> factories) {
        Args.notNull(spec, "spec");
        Args.notNull(factories, "factories");
        StringBuilder reasons = new StringBuilder();
        for (BrowserRuntimeFactory f : factories) {
            String reason = f.unsupportedReason(spec);
            if (reason == null) {
                return f.create(spec);
            }
            reasons.append(reasons.length() == 0 ? "" : "; ").append(f.getBackendId()).append(": ").append(reason);
        }
        String detail = factories.isEmpty() ? "no BrowserRuntimeFactory on the class path" : reasons.toString();
        throw new BrowserUnavailableException(BrowserReadiness.of(BrowserReadiness.Status.NO_BACKEND, "no backend for " + spec.getProduct()
                + " (" + spec.getSource() + "): " + detail), null);
    }
}
