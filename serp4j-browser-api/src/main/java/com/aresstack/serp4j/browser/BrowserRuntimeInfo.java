package com.aresstack.serp4j.browser;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Static description of a {@link BrowserRuntime} (WP3 §5.2). Immutable. */
public final class BrowserRuntimeInfo {

    private final String backendId;
    private final EngineFamily engineFamily;
    private final String browserVersion;
    private final IsolationLevel isolationLevel;
    private final int maxConcurrentSessions;
    private final boolean headless;
    private final Set<BrowserCapability> capabilities;

    private BrowserRuntimeInfo(Builder builder) {
        this.backendId = Args.notBlank(builder.backendId, "backendId");
        this.engineFamily = Args.notNull(builder.engineFamily, "engineFamily");
        this.browserVersion = builder.browserVersion;
        this.isolationLevel = Args.notNull(builder.isolationLevel, "isolationLevel");
        this.maxConcurrentSessions = Args.positive(builder.maxConcurrentSessions, "maxConcurrentSessions");
        this.headless = builder.headless;
        this.capabilities = Collections.unmodifiableSet(builder.capabilities.isEmpty()
                ? EnumSet.noneOf(BrowserCapability.class) : EnumSet.copyOf(builder.capabilities));
    }

    /**
     * @param backendId     backend id such as {@code playwright-chromium}, {@code remote-playwright} or {@code fake}
     * @param engineFamily  engine family
     * @return a builder
     */
    public static Builder builder(String backendId, EngineFamily engineFamily) {
        return new Builder(backendId, engineFamily);
    }

    /**
     * @return backend id such as {@code playwright-chromium}, {@code remote-playwright} or {@code fake}
     */
    public String getBackendId() {
        return backendId;
    }

    /**
     * @return the engine family
     */
    public EngineFamily getEngineFamily() {
        return engineFamily;
    }

    /**
     * @return the browser version once a browser has been started; empty before
     */
    public Optional<String> getBrowserVersion() {
        return Optional.ofNullable(browserVersion);
    }

    /**
     * @return how sessions are isolated
     */
    public IsolationLevel getIsolationLevel() {
        return isolationLevel;
    }

    /**
     * @return hard limit of simultaneously open sessions
     */
    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }

    /**
     * @return whether the browser runs without a visible window
     */
    public boolean isHeadless() {
        return headless;
    }

    /**
     * @return supported capabilities (unmodifiable)
     */
    public Set<BrowserCapability> getCapabilities() {
        return capabilities;
    }

    /**
     * @param capability capability
     * @return whether it is supported
     */
    public boolean supports(BrowserCapability capability) {
        return capabilities.contains(capability);
    }

    /**
     * @param required required capabilities
     * @return the required capabilities this runtime does not support (unmodifiable, possibly empty)
     */
    public Set<BrowserCapability> missing(Set<BrowserCapability> required) {
        EnumSet<BrowserCapability> missing = EnumSet.noneOf(BrowserCapability.class);
        for (BrowserCapability capability : Args.notNull(required, "required")) {
            if (!capabilities.contains(capability)) {
                missing.add(capability);
            }
        }
        return Collections.unmodifiableSet(missing);
    }

    /**
     * @return a builder initialised with this description
     */
    public Builder toBuilder() {
        Builder builder = new Builder(backendId, engineFamily)
                .isolationLevel(isolationLevel)
                .maxConcurrentSessions(maxConcurrentSessions)
                .headless(headless)
                .capabilities(capabilities);
        builder.browserVersion = browserVersion;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BrowserRuntimeInfo)) {
            return false;
        }
        BrowserRuntimeInfo that = (BrowserRuntimeInfo) o;
        return maxConcurrentSessions == that.maxConcurrentSessions && headless == that.headless
                && backendId.equals(that.backendId) && engineFamily == that.engineFamily
                && (browserVersion == null ? that.browserVersion == null : browserVersion.equals(that.browserVersion))
                && isolationLevel == that.isolationLevel && capabilities.equals(that.capabilities);
    }

    @Override
    public int hashCode() {
        int result = backendId.hashCode();
        result = 31 * result + engineFamily.hashCode();
        result = 31 * result + (browserVersion == null ? 0 : browserVersion.hashCode());
        result = 31 * result + isolationLevel.hashCode();
        result = 31 * result + maxConcurrentSessions;
        result = 31 * result + (headless ? 1 : 0);
        return 31 * result + capabilities.hashCode();
    }

    @Override
    public String toString() {
        return "BrowserRuntimeInfo{backend=" + backendId + ", engine=" + engineFamily
                + ", version=" + (browserVersion == null ? "unknown" : browserVersion)
                + ", isolation=" + isolationLevel + ", maxConcurrentSessions=" + maxConcurrentSessions
                + ", headless=" + headless + ", capabilities=" + capabilities + '}';
    }

    /** Builder for {@link BrowserRuntimeInfo}. */
    public static final class Builder {
        private final String backendId;
        private final EngineFamily engineFamily;
        private String browserVersion;
        private IsolationLevel isolationLevel = IsolationLevel.CONTEXT;
        private int maxConcurrentSessions = 1;
        private boolean headless = true;
        private final Set<BrowserCapability> capabilities = EnumSet.noneOf(BrowserCapability.class);

        private Builder(String backendId, EngineFamily engineFamily) {
            this.backendId = backendId;
            this.engineFamily = engineFamily;
        }

        /**
         * @param browserVersion version reported by the started browser, or {@code null} if unknown
         * @return this builder
         */
        public Builder browserVersion(String browserVersion) {
            this.browserVersion = browserVersion == null || browserVersion.trim().isEmpty() ? null : browserVersion;
            return this;
        }

        /**
         * @param isolationLevel isolation level, default {@link IsolationLevel#CONTEXT}
         * @return this builder
         */
        public Builder isolationLevel(IsolationLevel isolationLevel) {
            this.isolationLevel = Args.notNull(isolationLevel, "isolationLevel");
            return this;
        }

        /**
         * @param maxConcurrentSessions hard session limit, default 1
         * @return this builder
         */
        public Builder maxConcurrentSessions(int maxConcurrentSessions) {
            this.maxConcurrentSessions = maxConcurrentSessions;
            return this;
        }

        /**
         * @param headless whether the browser has no visible window, default {@code true}
         * @return this builder
         */
        public Builder headless(boolean headless) {
            this.headless = headless;
            return this;
        }

        /**
         * @param capability supported capability
         * @return this builder
         */
        public Builder capability(BrowserCapability capability) {
            capabilities.add(Args.notNull(capability, "capability"));
            return this;
        }

        /**
         * Replaces the supported capabilities.
         *
         * @param capabilities supported capabilities
         * @return this builder
         */
        public Builder capabilities(Set<BrowserCapability> capabilities) {
            this.capabilities.clear();
            for (BrowserCapability capability : Args.notNull(capabilities, "capabilities")) {
                capability(capability);
            }
            return this;
        }

        /**
         * @return the description
         */
        public BrowserRuntimeInfo build() {
            return new BrowserRuntimeInfo(this);
        }
    }
}
