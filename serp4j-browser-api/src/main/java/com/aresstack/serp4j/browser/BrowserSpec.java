package com.aresstack.serp4j.browser;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Backend-neutral description of the browser a host wants Serp4J to use (ADR-020). The host decides which browser;
 * a {@link BrowserRuntimeFactory} turns the spec into a {@link BrowserRuntime} via {@link BrowserRuntimes}.
 *
 * <pre>
 * BrowserSpec edge     = BrowserSpec.installed(BrowserProduct.EDGE).headless(false).build();
 * BrowserSpec camoufox = BrowserSpec.executable(BrowserProduct.CAMOUFOX, Paths.get("C:/tools/camoufox/camoufox.exe")).build();
 * BrowserSpec remote   = BrowserSpec.connect(BrowserProduct.CHROMIUM, "ws://127.0.0.1:3000/").build();
 * </pre>
 *
 * <p>Immutable. Secrets do not belong into options or environment values that are logged; {@link #toString()} prints
 * only keys.</p>
 */
public final class BrowserSpec {

    /** Where the browser comes from. */
    public enum Source {
        /** Installed browser, located by {@link BrowserDiscovery} or the backend (for example a Playwright channel). */
        INSTALLED,
        /** Browser at a fixed executable path, for example a portable Camoufox. */
        EXECUTABLE,
        /** Already running browser managed by the host; Serp4J only connects and never ends its process. */
        CONNECT
    }

    private final BrowserProduct product;
    private final Source source;
    private final Path executable;
    private final String endpoint;
    private final boolean headless;
    private final List<String> launchArguments;
    private final Map<String, String> environment;
    private final Path profileRoot;
    private final int maxConcurrentSessions;
    private final Duration startTimeout;
    private final Duration closeTimeout;
    private final Map<String, String> options;

    private BrowserSpec(Builder b) {
        this.product = b.product;
        this.source = b.source;
        this.executable = b.executable;
        this.endpoint = b.endpoint;
        this.headless = b.headless;
        this.launchArguments = Collections.unmodifiableList(new ArrayList<String>(b.launchArguments));
        this.environment = Collections.unmodifiableMap(new LinkedHashMap<String, String>(b.environment));
        this.profileRoot = b.profileRoot;
        this.maxConcurrentSessions = b.maxConcurrentSessions;
        this.startTimeout = b.startTimeout;
        this.closeTimeout = b.closeTimeout;
        this.options = Collections.unmodifiableMap(new LinkedHashMap<String, String>(b.options));
    }

    /**
     * @param product installed browser product
     * @return a builder
     */
    public static Builder installed(BrowserProduct product) {
        return new Builder(product, Source.INSTALLED, null, null);
    }

    /**
     * @param product    product of the executable
     * @param executable browser executable, for example a portable Camoufox
     * @return a builder
     */
    public static Builder executable(BrowserProduct product, Path executable) {
        return new Builder(product, Source.EXECUTABLE, Args.notNull(executable, "executable"), null);
    }

    /**
     * @param installation a detected installation
     * @return a builder launching exactly that executable
     */
    public static Builder of(BrowserInstallation installation) {
        Args.notNull(installation, "installation");
        return executable(installation.getProduct(), installation.getExecutable());
    }

    /**
     * @param product  product of the running browser
     * @param endpoint Playwright server ({@code ws://…}) or, for Chromium, a CDP endpoint ({@code http://…})
     * @return a builder; launch arguments, environment and profile root are not allowed
     */
    public static Builder connect(BrowserProduct product, String endpoint) {
        String e = Args.notBlank(endpoint, "endpoint").trim();
        if (!(e.startsWith("ws://") || e.startsWith("wss://") || e.startsWith("http://")
                || e.startsWith("https://"))) {
            throw new IllegalArgumentException("endpoint must be a ws(s):// or http(s):// URL");
        }
        return new Builder(product, Source.CONNECT, null, e);
    }

    public BrowserProduct getProduct() {
        return product;
    }

    public Source getSource() {
        return source;
    }

    /** @return the executable for {@link Source#EXECUTABLE} */
    public Optional<Path> getExecutable() {
        return Optional.ofNullable(executable);
    }

    /** @return the endpoint for {@link Source#CONNECT} */
    public Optional<String> getEndpoint() {
        return Optional.ofNullable(endpoint);
    }

    /** @return {@code true} if the browser runs without a window (default) */
    public boolean isHeadless() {
        return headless;
    }

    public List<String> getLaunchArguments() {
        return launchArguments;
    }

    /** @return extra environment variables for the browser process */
    public Map<String, String> getEnvironment() {
        return environment;
    }

    /** @return root directory for {@link ProfileMode#persistentExclusive(String)} profiles */
    public Optional<Path> getProfileRoot() {
        return Optional.ofNullable(profileRoot);
    }

    /** @return session limit, {@code 0} for the backend default */
    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }

    public Optional<Duration> getStartTimeout() {
        return Optional.ofNullable(startTimeout);
    }

    public Optional<Duration> getCloseTimeout() {
        return Optional.ofNullable(closeTimeout);
    }

    /** @return backend-specific options, for example {@code camoufox.config} */
    public Map<String, String> getOptions() {
        return options;
    }

    /**
     * @param key option key
     * @return the option value
     */
    public Optional<String> getOption(String key) {
        return Optional.ofNullable(options.get(key));
    }

    @Override
    public String toString() {
        return "BrowserSpec{product=" + product + ", source=" + source
                + (executable != null ? ", executable=" + executable : "")
                + (endpoint != null ? ", endpoint=" + endpoint : "")
                + ", headless=" + headless + ", environmentKeys=" + environment.keySet()
                + ", optionKeys=" + options.keySet() + '}';
    }

    /** Builder for {@link BrowserSpec}. */
    public static final class Builder {
        private final BrowserProduct product;
        private final Source source;
        private final Path executable;
        private final String endpoint;
        private boolean headless = true;
        private final List<String> launchArguments = new ArrayList<String>();
        private final Map<String, String> environment = new LinkedHashMap<String, String>();
        private Path profileRoot;
        private int maxConcurrentSessions;
        private Duration startTimeout;
        private Duration closeTimeout;
        private final Map<String, String> options = new LinkedHashMap<String, String>();

        private Builder(BrowserProduct product, Source source, Path executable, String endpoint) {
            this.product = Args.notNull(product, "product");
            this.source = source;
            this.executable = executable;
            this.endpoint = endpoint;
        }

        public Builder headless(boolean headless) {
            this.headless = headless;
            return this;
        }

        public Builder launchArgument(String argument) {
            launchArguments.add(Args.notBlank(argument, "argument"));
            return this;
        }

        public Builder environment(String name, String value) {
            environment.put(Args.notBlank(name, "name"), Args.notNull(value, "value"));
            return this;
        }

        public Builder profileRoot(Path profileRoot) {
            this.profileRoot = profileRoot;
            return this;
        }

        public Builder maxConcurrentSessions(int maxConcurrentSessions) {
            this.maxConcurrentSessions = Args.positive(maxConcurrentSessions, "maxConcurrentSessions");
            return this;
        }

        public Builder startTimeout(Duration startTimeout) {
            this.startTimeout = Args.positive(startTimeout, "startTimeout");
            return this;
        }

        public Builder closeTimeout(Duration closeTimeout) {
            this.closeTimeout = Args.positive(closeTimeout, "closeTimeout");
            return this;
        }

        public Builder option(String key, String value) {
            options.put(Args.notBlank(key, "key"), Args.notNull(value, "value"));
            return this;
        }

        /**
         * @return the spec
         * @throws IllegalStateException if launch-only settings are combined with {@link Source#CONNECT}
         */
        public BrowserSpec build() {
            if (source == Source.CONNECT && (!launchArguments.isEmpty() || !environment.isEmpty()
                    || profileRoot != null)) {
                throw new IllegalStateException("launch arguments, environment and profile root do not apply to a "
                        + "connected browser");
            }
            return new BrowserSpec(this);
        }
    }
}
