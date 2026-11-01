package com.aresstack.serp4j.browser;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Options of {@link BrowserRuntime#openSession(BrowserSessionOptions)} (WP3 §5.2, §6.3). Immutable.
 *
 * <p>Budgets: a session is closed with state {@link BrowserSessionState#EXPIRED} when its {@code maxLifetime} or
 * {@code idleTimeout} has passed or when it has used {@code maxNavigations} navigations; the next call fails with
 * {@link BrowserSessionClosedException}. Every budget is unlimited unless set.</p>
 *
 * <p>Profile and obstacle policy imply capabilities: {@link ProfileMode#persistentExclusive(String)} adds
 * {@link BrowserCapability#PERSISTENT_PROFILE}, {@link ObstaclePolicy#waitForHuman(Duration)} adds
 * {@link BrowserCapability#HEADED_MODE} to {@link #getRequiredCapabilities()}; a runtime without them refuses the
 * session with {@link UnsupportedBrowserCapabilityException}.</p>
 */
public final class BrowserSessionOptions {

    /** Default for {@link #getAcquireTimeout()}. */
    public static final Duration DEFAULT_ACQUIRE_TIMEOUT = Duration.ofSeconds(30);
    /** Default for {@link #getDefaultTimeout()}. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private static final BrowserSessionOptions DEFAULTS = builder().build();

    private final Locale locale;
    private final Duration acquireTimeout;
    private final Duration defaultTimeout;
    private final UrlPolicy urlPolicy;
    private final Set<BrowserCapability> requiredCapabilities;
    private final Set<BrowserCapability> explicitCapabilities;
    private final Duration maxLifetime;
    private final Duration idleTimeout;
    private final Integer maxNavigations;
    private final ProfileMode profileMode;
    private final ObstaclePolicy obstaclePolicy;

    private BrowserSessionOptions(Builder builder) {
        this.locale = builder.locale;
        this.acquireTimeout = builder.acquireTimeout;
        this.defaultTimeout = builder.defaultTimeout;
        this.urlPolicy = builder.urlPolicy;
        this.profileMode = builder.profileMode;
        this.obstaclePolicy = builder.obstaclePolicy;
        Set<BrowserCapability> required = EnumSet.noneOf(BrowserCapability.class);
        required.addAll(builder.requiredCapabilities);
        this.explicitCapabilities = Collections.unmodifiableSet(EnumSet.copyOf(required));
        if (profileMode.isPersistent()) {
            required.add(BrowserCapability.PERSISTENT_PROFILE);
        }
        if (obstaclePolicy.isWaitForHuman()) {
            required.add(BrowserCapability.HEADED_MODE);
        }
        this.requiredCapabilities = Collections.unmodifiableSet(required);
        this.maxLifetime = builder.maxLifetime;
        this.idleTimeout = builder.idleTimeout;
        this.maxNavigations = builder.maxNavigations;
    }

    /**
     * @return the default options: no locale, allow all web URLs, 30 s timeouts, no budgets, ephemeral profile,
     * fail fast on obstacles
     */
    public static BrowserSessionOptions defaults() {
        return DEFAULTS;
    }

    /**
     * @return a builder with the defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return UI language and {@code Accept-Language}, applied if the runtime supports
     * {@link BrowserCapability#LOCALE_EMULATION}; empty = browser default
     */
    public Optional<Locale> getLocale() {
        return Optional.ofNullable(locale);
    }

    /**
     * @return maximum wait for a free session slot
     */
    public Duration getAcquireTimeout() {
        return acquireTimeout;
    }

    /**
     * @return timeout of every operation that has no explicit timeout
     */
    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    /**
     * @return the navigation allowlist
     */
    public UrlPolicy getUrlPolicy() {
        return urlPolicy;
    }

    /**
     * @return the profile of the session; {@link ProfileMode#ephemeral()} by default
     */
    public ProfileMode getProfileMode() {
        return profileMode;
    }

    /**
     * @return the obstacle policy; {@link ObstaclePolicy#failFast()} by default
     */
    public ObstaclePolicy getObstaclePolicy() {
        return obstaclePolicy;
    }

    /**
     * @return capabilities the runtime must support, otherwise opening fails, including the ones implied by profile
     * and obstacle policy (unmodifiable)
     */
    public Set<BrowserCapability> getRequiredCapabilities() {
        return requiredCapabilities;
    }

    /**
     * @return maximum lifetime of the session; empty = unlimited
     */
    public Optional<Duration> getMaxLifetime() {
        return Optional.ofNullable(maxLifetime);
    }

    /**
     * @return maximum time without a command; empty = unlimited
     */
    public Optional<Duration> getIdleTimeout() {
        return Optional.ofNullable(idleTimeout);
    }

    /**
     * @return maximum number of {@link BrowserSession#navigate(NavigationRequest)} calls; empty = unlimited
     */
    public OptionalInt getMaxNavigations() {
        return maxNavigations == null ? OptionalInt.empty() : OptionalInt.of(maxNavigations);
    }

    /**
     * @return a builder initialised with these options
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.locale = locale;
        builder.acquireTimeout = acquireTimeout;
        builder.defaultTimeout = defaultTimeout;
        builder.urlPolicy = urlPolicy;
        builder.requiredCapabilities.addAll(explicitCapabilities);
        builder.maxLifetime = maxLifetime;
        builder.idleTimeout = idleTimeout;
        builder.maxNavigations = maxNavigations;
        builder.profileMode = profileMode;
        builder.obstaclePolicy = obstaclePolicy;
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BrowserSessionOptions)) {
            return false;
        }
        BrowserSessionOptions that = (BrowserSessionOptions) o;
        return eq(locale, that.locale) && acquireTimeout.equals(that.acquireTimeout)
                && defaultTimeout.equals(that.defaultTimeout) && urlPolicy.equals(that.urlPolicy)
                && explicitCapabilities.equals(that.explicitCapabilities) && eq(maxLifetime, that.maxLifetime)
                && eq(idleTimeout, that.idleTimeout) && eq(maxNavigations, that.maxNavigations)
                && profileMode.equals(that.profileMode) && obstaclePolicy.equals(that.obstaclePolicy);
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public int hashCode() {
        Object[] parts = {locale, acquireTimeout, defaultTimeout, urlPolicy, explicitCapabilities, maxLifetime,
                idleTimeout, maxNavigations, profileMode, obstaclePolicy};
        return java.util.Arrays.hashCode(parts);
    }

    @Override
    public String toString() {
        return "BrowserSessionOptions{locale=" + locale + ", acquireTimeout=" + acquireTimeout
                + ", defaultTimeout=" + defaultTimeout + ", urlPolicy=" + urlPolicy
                + ", requiredCapabilities=" + requiredCapabilities + ", maxLifetime=" + maxLifetime
                + ", idleTimeout=" + idleTimeout + ", maxNavigations=" + maxNavigations + ", profileMode="
                + profileMode + ", obstaclePolicy=" + obstaclePolicy + '}';
    }

    /** Builder for {@link BrowserSessionOptions}. */
    public static final class Builder {
        private Locale locale;
        private Duration acquireTimeout = DEFAULT_ACQUIRE_TIMEOUT;
        private Duration defaultTimeout = DEFAULT_TIMEOUT;
        private UrlPolicy urlPolicy = UrlPolicy.allowAllWeb();
        private final Set<BrowserCapability> requiredCapabilities = EnumSet.noneOf(BrowserCapability.class);
        private Duration maxLifetime;
        private Duration idleTimeout;
        private Integer maxNavigations;
        private ProfileMode profileMode = ProfileMode.ephemeral();
        private ObstaclePolicy obstaclePolicy = ObstaclePolicy.failFast();

        private Builder() {
        }

        /**
         * @param locale UI language, or {@code null} for the browser default
         * @return this builder
         */
        public Builder locale(Locale locale) {
            this.locale = locale;
            return this;
        }

        /**
         * @param acquireTimeout maximum wait for a free slot; zero = do not wait
         * @return this builder
         */
        public Builder acquireTimeout(Duration acquireTimeout) {
            this.acquireTimeout = Args.notNegative(acquireTimeout, "acquireTimeout");
            return this;
        }

        /**
         * @param defaultTimeout positive timeout of operations without an explicit timeout
         * @return this builder
         */
        public Builder defaultTimeout(Duration defaultTimeout) {
            this.defaultTimeout = Args.positive(defaultTimeout, "defaultTimeout");
            return this;
        }

        /**
         * @param urlPolicy navigation allowlist
         * @return this builder
         */
        public Builder urlPolicy(UrlPolicy urlPolicy) {
            this.urlPolicy = Args.notNull(urlPolicy, "urlPolicy");
            return this;
        }

        /**
         * @param profileMode ephemeral (default) or persistent, exclusively leased profile
         * @return this builder
         */
        public Builder profileMode(ProfileMode profileMode) {
            this.profileMode = Args.notNull(profileMode, "profileMode");
            return this;
        }

        /**
         * @param obstaclePolicy fail fast (default) or wait for a human in the visible window
         * @return this builder
         */
        public Builder obstaclePolicy(ObstaclePolicy obstaclePolicy) {
            this.obstaclePolicy = Args.notNull(obstaclePolicy, "obstaclePolicy");
            return this;
        }

        /**
         * @param capability capability the runtime must support
         * @return this builder
         */
        public Builder require(BrowserCapability capability) {
            requiredCapabilities.add(Args.notNull(capability, "capability"));
            return this;
        }

        /**
         * @param maxLifetime positive lifetime, or {@code null} for unlimited
         * @return this builder
         */
        public Builder maxLifetime(Duration maxLifetime) {
            this.maxLifetime = maxLifetime == null ? null : Args.positive(maxLifetime, "maxLifetime");
            return this;
        }

        /**
         * @param idleTimeout positive idle timeout, or {@code null} for unlimited
         * @return this builder
         */
        public Builder idleTimeout(Duration idleTimeout) {
            this.idleTimeout = idleTimeout == null ? null : Args.positive(idleTimeout, "idleTimeout");
            return this;
        }

        /**
         * @param maxNavigations positive number of navigations
         * @return this builder
         */
        public Builder maxNavigations(int maxNavigations) {
            this.maxNavigations = Args.positive(maxNavigations, "maxNavigations");
            return this;
        }

        /**
         * @return this builder without a navigation budget
         */
        public Builder unlimitedNavigations() {
            this.maxNavigations = null;
            return this;
        }

        /**
         * @return the options
         */
        public BrowserSessionOptions build() {
            return new BrowserSessionOptions(this);
        }
    }
}
