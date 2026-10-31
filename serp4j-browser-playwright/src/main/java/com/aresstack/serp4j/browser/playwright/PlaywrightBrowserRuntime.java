package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserCapability;
import com.aresstack.serp4j.browser.BrowserCapacityException;
import com.aresstack.serp4j.browser.BrowserReadiness;
import com.aresstack.serp4j.browser.BrowserRuntime;
import com.aresstack.serp4j.browser.BrowserRuntimeInfo;
import com.aresstack.serp4j.browser.BrowserSession;
import com.aresstack.serp4j.browser.BrowserSessionOptions;
import com.aresstack.serp4j.browser.BrowserUnavailableException;
import com.aresstack.serp4j.browser.IsolationLevel;
import com.aresstack.serp4j.browser.UnsupportedBrowserCapabilityException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link BrowserRuntime} over Playwright Java (ADR-011, WP3 §6). In-process by default; a remote Playwright server
 * is used with {@link RemotePlaywrightRuntime}.
 *
 * <pre>
 * BrowserRuntime runtime = PlaywrightBrowserRuntime.builder()
 *         .browserType(PlaywrightBrowserType.CHROMIUM)   // default
 *         .headless(true)                                 // default
 *         .maxConcurrentSessions(2)                       // default
 *         .build();
 * </pre>
 *
 * <p><strong>Lifecycle.</strong> Nothing starts before the first {@link #openSession(BrowserSessionOptions)} or
 * {@link #probe()}. Workers (owner thread + Playwright + browser) start lazily up to {@link Builder#workers(int)};
 * a crashed browser is replaced by a new worker on the next {@code openSession}. {@link #close()} closes every
 * session, then the browsers, then the drivers; a JVM shutdown hook does the same as a safety net. The runtime
 * never downloads browsers ({@code PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1}); install them explicitly or point
 * {@link Builder#executablePath(Path)} or {@link Builder#channel(String)} at an installed browser.</p>
 *
 * <p><strong>Parallelism.</strong> All sessions of one worker share its owner thread and are therefore serialized;
 * use more workers for real parallelism. {@link Builder#maxConcurrentSessions(int)} is a hard limit across all
 * workers.</p>
 *
 * <p><strong>Ownership (ADR-017).</strong> The runtime belongs to its creator; providers that receive it never close
 * it.</p>
 */
public final class PlaywrightBrowserRuntime implements BrowserRuntime {

    private final PlaywrightConfig config;
    private final Semaphore slots;
    private final List<Worker> workers = new ArrayList<Worker>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger workerNumbers = new AtomicInteger();
    private final Thread shutdownHook;
    private volatile String browserVersion;
    private volatile String driverId;

    private PlaywrightBrowserRuntime(PlaywrightConfig config) {
        this.config = config;
        this.slots = new Semaphore(config.maxConcurrentSessions, true);
        if (config.shutdownHook) {
            this.shutdownHook = new Thread(new Runnable() {
                @Override
                public void run() {
                    closeWorkers();
                }
            }, "serp4j-playwright-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } else {
            this.shutdownHook = null;
        }
    }

    /**
     * @return a builder for a local Chromium runtime
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public BrowserRuntimeInfo getInfo() {
        BrowserRuntimeInfo.Builder info = BrowserRuntimeInfo.builder(config.backendId(),
                        config.browserType.getEngineFamily())
                .browserVersion(browserVersion)
                .isolationLevel(IsolationLevel.CONTEXT)
                .maxConcurrentSessions(config.maxConcurrentSessions)
                .headless(config.headless)
                .capability(BrowserCapability.JAVASCRIPT)
                .capability(BrowserCapability.NETWORK_OBSERVATION)
                .capability(BrowserCapability.NETWORK_INTERCEPTION)
                .capability(BrowserCapability.LOCALE_EMULATION);
        if (!config.headless) {
            info.capability(BrowserCapability.HEADED_MODE);
        }
        if (config.maxConcurrentSessions > 1) {
            info.capability(BrowserCapability.PARALLEL_SESSIONS);
        }
        return info.build();
    }

    @Override
    public BrowserReadiness probe() {
        if (closed.get()) {
            return closedReadiness();
        }
        BrowserReadiness staticCheck = checkEnvironment();
        if (staticCheck != null) {
            return staticCheck;
        }
        try {
            Worker worker = acquireWorker();
            return BrowserReadiness.ready(config.backendId() + " " + worker.getBrowserVersion() + " via driver '"
                    + driverId + "'" + (config.isRemote() ? " at " + redact(config.wsEndpoint) : ""));
        } catch (BrowserUnavailableException e) {
            return e.getReadiness();
        }
    }

    /** Driver and executable checks without starting anything; {@code null} if fine. */
    private BrowserReadiness checkEnvironment() {
        DriverDetection.Result detection = DriverDetection.detect(config.classLoader);
        if (!detection.isOk()) {
            return detection.failure;
        }
        driverId = detection.module.getId();
        if (config.executablePath != null && !Files.isRegularFile(config.executablePath)) {
            return BrowserReadiness.of(BrowserReadiness.Status.BROWSER_NOT_INSTALLED,
                    "browser executable does not exist: " + config.executablePath);
        }
        return null;
    }

    @Override
    public BrowserSession openSession(BrowserSessionOptions options) {
        if (options == null) {
            throw new NullPointerException("options must not be null");
        }
        if (closed.get()) {
            throw new BrowserUnavailableException(closedReadiness(), null);
        }
        Set<BrowserCapability> missing = getInfo().missing(options.getRequiredCapabilities());
        if (!missing.isEmpty()) {
            throw new UnsupportedBrowserCapabilityException(missing, config.backendId() + " does not support "
                    + missing);
        }
        boolean acquired;
        try {
            acquired = slots.tryAcquire(options.getAcquireTimeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrowserCapacityException(config.maxConcurrentSessions, "interrupted while waiting for a "
                    + "browser session slot");
        }
        if (!acquired) {
            throw new BrowserCapacityException(config.maxConcurrentSessions, "all " + config.maxConcurrentSessions
                    + " browser session slots are in use (waited " + options.getAcquireTimeout() + ")");
        }
        try {
            for (int attempt = 0; ; attempt++) {
                Worker worker = acquireWorker();
                try {
                    return worker.openSession(options);
                } catch (WorkerStoppedException e) {
                    // the browser of this worker died; a fresh worker is started once
                    if (attempt > 0) {
                        throw new BrowserUnavailableException(BrowserReadiness.of(
                                BrowserReadiness.Status.BROWSER_START_FAILED, "browser disconnected repeatedly"), e);
                    }
                }
            }
        } catch (RuntimeException | Error e) {
            slots.release();
            throw e;
        }
    }

    /** Returns the least loaded live worker, starting a new one if allowed or needed. */
    private Worker acquireWorker() {
        synchronized (workers) {
            if (closed.get()) {
                throw new BrowserUnavailableException(closedReadiness(), null);
            }
            List<Worker> alive = new ArrayList<Worker>();
            for (Worker worker : workers) {
                if (worker.isAlive()) {
                    alive.add(worker);
                }
            }
            workers.retainAll(alive);
            Worker least = null;
            for (Worker worker : alive) {
                if (least == null || worker.getSessionCount() < least.getSessionCount()) {
                    least = worker;
                }
            }
            if (least != null && (least.getSessionCount() == 0 || alive.size() >= config.workers)) {
                return least;
            }
            BrowserReadiness problem = checkEnvironment();
            if (problem != null) {
                throw new BrowserUnavailableException(problem, null);
            }
            Worker started = Worker.start("serp4j-playwright-worker-" + workerNumbers.incrementAndGet(), config, this);
            browserVersion = started.getBrowserVersion();
            workers.add(started);
            return started;
        }
    }

    /** A session reached a terminal state: frees its slot. Called exactly once per session. */
    void release() {
        slots.release();
    }

    /** A worker stopped after its browser disconnected. */
    void workerStopped(Worker worker) {
        synchronized (workers) {
            workers.remove(worker);
        }
    }

    private static BrowserReadiness closedReadiness() {
        return BrowserReadiness.of(BrowserReadiness.Status.CLOSED, "Playwright runtime closed");
    }

    private static String redact(String wsEndpoint) {
        int query = wsEndpoint.indexOf('?');
        return query < 0 ? wsEndpoint : wsEndpoint.substring(0, query) + "?****";
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeWorkers();
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException e) {
                    // the JVM is shutting down; the hook runs anyway
                }
            }
        }
    }

    private void closeWorkers() {
        closed.set(true);
        List<Worker> toClose;
        synchronized (workers) {
            toClose = new ArrayList<Worker>(workers);
            workers.clear();
        }
        for (Worker worker : toClose) {
            worker.shutdown(config.closeTimeout.toMillis());
        }
    }

    @Override
    public String toString() {
        return "PlaywrightBrowserRuntime{" + config.backendId() + ", closed=" + closed.get() + '}';
    }

    /** Builder for {@link PlaywrightBrowserRuntime}. */
    public static final class Builder {
        private PlaywrightBrowserType browserType = PlaywrightBrowserType.CHROMIUM;
        private String channel;
        private Path executablePath;
        private boolean headless = true;
        private final List<String> launchArgs = new ArrayList<String>();
        private String wsEndpoint;
        private int workers = 1;
        private int maxConcurrentSessions = 2;
        private Duration startTimeout = Duration.ofSeconds(60);
        private Duration closeTimeout = Duration.ofSeconds(10);
        private final Map<String, String> driverEnvironment = new LinkedHashMap<String, String>();
        private boolean shutdownHook = true;
        private ClassLoader classLoader = PlaywrightBrowserRuntime.class.getClassLoader();

        Builder() {
        }

        /**
         * @param browserType engine, default {@link PlaywrightBrowserType#CHROMIUM}
         * @return this builder
         */
        public Builder browserType(PlaywrightBrowserType browserType) {
            if (browserType == null) {
                throw new NullPointerException("browserType must not be null");
            }
            this.browserType = browserType;
            return this;
        }

        /**
         * @param channel installed browser channel such as {@code chrome} or {@code msedge}; {@code null} for the
         *                Playwright browser build
         * @return this builder
         */
        public Builder channel(String channel) {
            this.channel = channel == null || channel.trim().isEmpty() ? null : channel.trim();
            return this;
        }

        /**
         * @param executablePath browser executable to launch; {@code null} for the default lookup
         * @return this builder
         */
        public Builder executablePath(Path executablePath) {
            this.executablePath = executablePath;
            return this;
        }

        /**
         * @param headless whether the browser runs without a window, default {@code true}; a visible browser
         *                 declares {@link BrowserCapability#HEADED_MODE}
         * @return this builder
         */
        public Builder headless(boolean headless) {
            this.headless = headless;
            return this;
        }

        /**
         * @param argument additional browser command line argument, for example {@code --no-sandbox} in containers
         * @return this builder
         */
        public Builder launchArgument(String argument) {
            if (argument == null || argument.trim().isEmpty()) {
                throw new IllegalArgumentException("argument must not be blank");
            }
            launchArgs.add(argument.trim());
            return this;
        }

        Builder wsEndpoint(String wsEndpoint) {
            if (wsEndpoint == null || wsEndpoint.trim().isEmpty()) {
                throw new IllegalArgumentException("wsEndpoint must not be blank");
            }
            this.wsEndpoint = wsEndpoint.trim();
            return this;
        }

        /**
         * @param workers positive number of owner threads, each with its own browser, default 1
         * @return this builder
         */
        public Builder workers(int workers) {
            if (workers <= 0) {
                throw new IllegalArgumentException("workers must be positive");
            }
            this.workers = workers;
            return this;
        }

        /**
         * @param maxConcurrentSessions positive hard session limit across all workers, default 2
         * @return this builder
         */
        public Builder maxConcurrentSessions(int maxConcurrentSessions) {
            if (maxConcurrentSessions <= 0) {
                throw new IllegalArgumentException("maxConcurrentSessions must be positive");
            }
            this.maxConcurrentSessions = maxConcurrentSessions;
            return this;
        }

        /**
         * @param startTimeout positive limit for starting driver and browser, default 60 s
         * @return this builder
         */
        public Builder startTimeout(Duration startTimeout) {
            this.startTimeout = positive(startTimeout, "startTimeout");
            return this;
        }

        /**
         * @param closeTimeout positive limit for closing one worker, default 10 s
         * @return this builder
         */
        public Builder closeTimeout(Duration closeTimeout) {
            this.closeTimeout = positive(closeTimeout, "closeTimeout");
            return this;
        }

        /**
         * @param name  environment variable passed to the driver process, for example
         *              {@code PLAYWRIGHT_BROWSERS_PATH}
         * @param value value
         * @return this builder
         */
        public Builder driverEnvironment(String name, String value) {
            if (name == null || value == null) {
                throw new NullPointerException("name and value must not be null");
            }
            if ("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD".equals(name)) {
                throw new IllegalArgumentException("Serp4J never downloads browsers; PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD "
                        + "is always 1");
            }
            driverEnvironment.put(name, value);
            return this;
        }

        /**
         * @param shutdownHook whether a JVM shutdown hook closes the runtime as a safety net, default {@code true}
         * @return this builder
         */
        public Builder shutdownHook(boolean shutdownHook) {
            this.shutdownHook = shutdownHook;
            return this;
        }

        /**
         * @param classLoader class loader for driver module discovery, default the loader of this class
         * @return this builder
         */
        public Builder classLoader(ClassLoader classLoader) {
            if (classLoader == null) {
                throw new NullPointerException("classLoader must not be null");
            }
            this.classLoader = classLoader;
            return this;
        }

        private static Duration positive(Duration value, String name) {
            if (value == null) {
                throw new NullPointerException(name + " must not be null");
            }
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }

        /**
         * @return the runtime; nothing is started yet
         */
        public PlaywrightBrowserRuntime build() {
            if (wsEndpoint != null && (executablePath != null || channel != null || !launchArgs.isEmpty())) {
                throw new IllegalStateException("executablePath, channel and launch arguments do not apply to a "
                        + "remote Playwright server");
            }
            return new PlaywrightBrowserRuntime(new PlaywrightConfig(browserType, channel, executablePath, headless,
                    new ArrayList<String>(launchArgs), wsEndpoint, workers, maxConcurrentSessions, startTimeout,
                    closeTimeout, new LinkedHashMap<String, String>(driverEnvironment), shutdownHook, classLoader));
        }
    }
}
