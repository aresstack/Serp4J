package com.aresstack.serp4j.http.ahc;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

/**
 * Settings of an {@link AhcHttpTransport}. Immutable; created with {@link #builder()}. The defaults match the
 * transport settings AskAI uses today ({@code HttpTransportConfiguration}), except for the user agent, which is the
 * neutral {@code Serp4J/<version>} (architecture.md §9.1).
 *
 * <p>Connection-level settings ({@linkplain Builder#connectTimeout connect} and {@linkplain Builder#readTimeout read}
 * timeout, keep-alive, compression) configure the client that {@link AhcHttpTransport#create} builds and are
 * ignored by {@link AhcHttpTransport#wrap}, where the host configured its own client. Request-level settings
 * ({@linkplain Builder#requestTimeout request timeout}, {@linkplain Builder#userAgent user agent},
 * {@linkplain Builder#followRedirects redirects}, {@linkplain Builder#maxConnections connection limits}) apply in
 * both cases.</p>
 */
public final class AhcHttpTransportOptions {

    /** {@code Serp4J/<version>}, the user agent sent when a request names none. */
    public static final String DEFAULT_USER_AGENT = "Serp4J/" + moduleVersion();

    private static final AhcHttpTransportOptions DEFAULTS = builder().build();

    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration requestTimeout;
    private final int maxConnections;
    private final int maxConnectionsPerHost;
    private final boolean followRedirects;
    private final int maxRedirects;
    private final boolean keepAlive;
    private final boolean compressionEnabled;
    private final String userAgent;

    private AhcHttpTransportOptions(Builder builder) {
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.requestTimeout = builder.requestTimeout;
        this.maxConnections = builder.maxConnections;
        this.maxConnectionsPerHost = builder.maxConnectionsPerHost;
        this.followRedirects = builder.followRedirects;
        this.maxRedirects = builder.maxRedirects;
        this.keepAlive = builder.keepAlive;
        this.compressionEnabled = builder.compressionEnabled;
        this.userAgent = builder.userAgent;
    }

    /** The default settings. */
    public static AhcHttpTransportOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Time to establish a TCP/TLS connection; default 15 s. */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /** Maximum idle time between two reads of a response; default 60 s. */
    public Duration getReadTimeout() {
        return readTimeout;
    }

    /**
     * Total time for a request including all redirects, used when the request itself sets no
     * {@linkplain com.aresstack.serp4j.http.HttpRequest#getTimeout() timeout}; default 60 s.
     */
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * Maximum number of requests in flight, and therefore of connections in use, over all hosts; default 100.
     * Further requests wait in order instead of failing.
     */
    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Maximum number of requests in flight, and therefore of connections in use, per origin (scheme, host, port);
     * default 20. Further requests to that origin wait in order instead of failing.
     */
    public int getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    /**
     * Whether {@code 301}, {@code 302}, {@code 303}, {@code 307} and {@code 308} responses with a {@code Location}
     * header are followed; default {@code true}. Credential headers are dropped when a redirect leaves the origin;
     * see {@link AhcHttpTransport}.
     */
    public boolean isFollowRedirects() {
        return followRedirects;
    }

    /** Maximum number of redirects per request; default 5. */
    public int getMaxRedirects() {
        return maxRedirects;
    }

    /** Whether connections are kept alive and pooled; default {@code true}. */
    public boolean isKeepAlive() {
        return keepAlive;
    }

    /** Whether gzip/deflate responses are requested and decompressed transparently; default {@code true}. */
    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    /** User agent sent when a request has no {@code User-Agent} header; default {@link #DEFAULT_USER_AGENT}. */
    public String getUserAgent() {
        return userAgent;
    }

    public Builder toBuilder() {
        return new Builder()
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .requestTimeout(requestTimeout)
                .maxConnections(maxConnections)
                .maxConnectionsPerHost(maxConnectionsPerHost)
                .followRedirects(followRedirects)
                .maxRedirects(maxRedirects)
                .keepAlive(keepAlive)
                .compressionEnabled(compressionEnabled)
                .userAgent(userAgent);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AhcHttpTransportOptions)) {
            return false;
        }
        AhcHttpTransportOptions that = (AhcHttpTransportOptions) o;
        return maxConnections == that.maxConnections
                && maxConnectionsPerHost == that.maxConnectionsPerHost
                && followRedirects == that.followRedirects
                && maxRedirects == that.maxRedirects
                && keepAlive == that.keepAlive
                && compressionEnabled == that.compressionEnabled
                && connectTimeout.equals(that.connectTimeout)
                && readTimeout.equals(that.readTimeout)
                && requestTimeout.equals(that.requestTimeout)
                && userAgent.equals(that.userAgent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectTimeout, readTimeout, requestTimeout, maxConnections, maxConnectionsPerHost,
                followRedirects, maxRedirects, keepAlive, compressionEnabled, userAgent);
    }

    @Override
    public String toString() {
        return "AhcHttpTransportOptions{connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout
                + ", requestTimeout=" + requestTimeout + ", maxConnections=" + maxConnections
                + ", maxConnectionsPerHost=" + maxConnectionsPerHost + ", followRedirects=" + followRedirects
                + ", maxRedirects=" + maxRedirects + ", keepAlive=" + keepAlive
                + ", compressionEnabled=" + compressionEnabled + ", userAgent=" + userAgent + '}';
    }

    private static String moduleVersion() {
        Properties properties = new Properties();
        try (InputStream in = AhcHttpTransportOptions.class.getResourceAsStream("serp4j-http-ahc.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            return "unknown";
        }
        String version = properties.getProperty("version", "").trim();
        return version.isEmpty() || version.startsWith("$") ? "unknown" : version;
    }

    /** Builder for {@link AhcHttpTransportOptions}; {@link #build()} validates. */
    public static final class Builder {
        private Duration connectTimeout = Duration.ofSeconds(15);
        private Duration readTimeout = Duration.ofSeconds(60);
        private Duration requestTimeout = Duration.ofSeconds(60);
        private int maxConnections = 100;
        private int maxConnectionsPerHost = 20;
        private boolean followRedirects = true;
        private int maxRedirects = 5;
        private boolean keepAlive = true;
        private boolean compressionEnabled = true;
        private String userAgent = DEFAULT_USER_AGENT;

        private Builder() {
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public Builder maxConnectionsPerHost(int maxConnectionsPerHost) {
            this.maxConnectionsPerHost = maxConnectionsPerHost;
            return this;
        }

        public Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        public Builder maxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
            return this;
        }

        public Builder keepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        public Builder compressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * @throws IllegalArgumentException if a timeout is not positive or exceeds {@link Integer#MAX_VALUE}
         *                                  milliseconds, a connection limit is not positive, the per-host limit
         *                                  exceeds the total limit, {@code maxRedirects} is negative or the user
         *                                  agent is blank
         */
        public AhcHttpTransportOptions build() {
            requirePositive(connectTimeout, "connectTimeout");
            requirePositive(readTimeout, "readTimeout");
            requirePositive(requestTimeout, "requestTimeout");
            if (maxConnections <= 0) {
                throw new IllegalArgumentException("maxConnections must be positive but was " + maxConnections);
            }
            if (maxConnectionsPerHost <= 0 || maxConnectionsPerHost > maxConnections) {
                throw new IllegalArgumentException("maxConnectionsPerHost must be in 1.." + maxConnections
                        + " but was " + maxConnectionsPerHost);
            }
            if (maxRedirects < 0) {
                throw new IllegalArgumentException("maxRedirects must not be negative but was " + maxRedirects);
            }
            if (userAgent == null || userAgent.trim().isEmpty()) {
                throw new IllegalArgumentException("userAgent must not be blank");
            }
            userAgent = userAgent.trim();
            return new AhcHttpTransportOptions(this);
        }

        private static void requirePositive(Duration value, String name) {
            if (value == null) {
                throw new NullPointerException(name + " must not be null");
            }
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException(name + " must be positive but was " + value);
            }
            if (value.toMillis() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(name + " must not exceed " + Integer.MAX_VALUE + " ms");
            }
        }
    }
}
