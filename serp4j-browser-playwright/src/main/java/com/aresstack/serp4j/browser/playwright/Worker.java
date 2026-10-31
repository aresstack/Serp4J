package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserReadiness;
import com.aresstack.serp4j.browser.BrowserSessionOptions;
import com.aresstack.serp4j.browser.BrowserUnavailableException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One owner thread with its own {@code Playwright} instance and one browser (WP3 §6.1). Every Playwright object of
 * the worker is created and used on the owner thread only; other threads enqueue commands and block on the result.
 * While no command is queued the owner thread pumps Playwright events in short slices.
 */
final class Worker {

    /** A unit of work executed on the owner thread. */
    interface Task<T> {
        T run();
    }

    static final long PUMP_MILLIS = 20L;
    private static final long REAP_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final Logger LOG = Logger.getLogger(Worker.class.getName());

    private final String name;
    private final PlaywrightConfig config;
    private final PlaywrightBrowserRuntime runtime;
    private final BlockingQueue<Command<?>> queue = new LinkedBlockingQueue<Command<?>>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger sessionCount = new AtomicInteger();
    private final List<PlaywrightSession> sessions = new ArrayList<PlaywrightSession>();
    private final List<Runnable> deferred = new ArrayList<Runnable>();
    private volatile Thread owner;
    private volatile boolean disconnected;
    private volatile String browserVersion;
    private Playwright playwright;
    private Browser browser;
    private long nextReap;

    private Worker(String name, PlaywrightConfig config, PlaywrightBrowserRuntime runtime) {
        this.name = name;
        this.config = config;
        this.runtime = runtime;
    }

    /**
     * Starts the owner thread, creates Playwright and the browser on it and returns once the browser is ready.
     *
     * @throws BrowserUnavailableException if the driver or the browser cannot be started
     */
    static Worker start(String name, PlaywrightConfig config, PlaywrightBrowserRuntime runtime) {
        final Worker worker = new Worker(name, config, runtime);
        final CompletableFuture<Void> started = new CompletableFuture<Void>();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    worker.init();
                } catch (Throwable e) {
                    worker.running.set(false);
                    worker.closePlaywrightQuietly();
                    started.completeExceptionally(e);
                    return;
                }
                started.complete(null);
                worker.loop();
            }
        }, name);
        thread.setDaemon(true);
        worker.owner = thread;
        thread.start();
        try {
            started.get(config.startTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return worker;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrowserUnavailableException(BrowserReadiness.of(BrowserReadiness.Status.BROWSER_START_FAILED,
                    "interrupted while starting the browser"), e);
        } catch (TimeoutException e) {
            worker.running.set(false);
            thread.interrupt();
            throw new BrowserUnavailableException(BrowserReadiness.of(BrowserReadiness.Status.BROWSER_START_FAILED,
                    "browser did not start within " + config.startTimeout), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BrowserUnavailableException) {
                throw (BrowserUnavailableException) cause;
            }
            throw new BrowserUnavailableException(Errors.startFailure(cause), cause);
        }
    }

    private void init() {
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        env.putAll(config.driverEnvironment);
        playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));
        BrowserType type;
        switch (config.browserType) {
            case FIREFOX:
                type = playwright.firefox();
                break;
            case WEBKIT:
                type = playwright.webkit();
                break;
            default:
                type = playwright.chromium();
                break;
        }
        if (config.isRemote()) {
            browser = type.connect(config.wsEndpoint,
                    new BrowserType.ConnectOptions().setTimeout(config.startTimeout.toMillis()));
        } else {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(config.headless)
                    .setTimeout(config.startTimeout.toMillis());
            if (!config.launchArgs.isEmpty()) {
                options.setArgs(new ArrayList<String>(config.launchArgs));
            }
            if (config.channel != null) {
                options.setChannel(config.channel);
            }
            if (config.executablePath != null) {
                options.setExecutablePath(config.executablePath);
            }
            browser = type.launch(options);
        }
        browser.onDisconnected(new Consumer<Browser>() {
            @Override
            public void accept(Browser disconnectedBrowser) {
                disconnected = true;
            }
        });
        browserVersion = browser.version();
    }

    String getName() {
        return name;
    }

    String getBrowserVersion() {
        return browserVersion;
    }

    boolean isAlive() {
        return running.get() && !disconnected;
    }

    boolean isDisconnected() {
        return disconnected;
    }

    int getSessionCount() {
        return sessionCount.get();
    }

    boolean isOwnerThread() {
        return Thread.currentThread() == owner;
    }

    void checkOwner() {
        if (!isOwnerThread()) {
            throw new IllegalStateException("Playwright touched from thread '" + Thread.currentThread().getName()
                    + "'; all Playwright access must run on the owner thread '" + name + "'");
        }
    }

    Browser browser() {
        checkOwner();
        return browser;
    }

    /** Runs a task on the owner thread and blocks until it is done. */
    <T> T call(Task<T> task) {
        if (isOwnerThread()) {
            return task.run();
        }
        if (!running.get()) {
            throw new WorkerStoppedException("browser worker " + name + " has stopped");
        }
        Command<T> command = new Command<T>(task);
        queue.add(command);
        if (!running.get() && queue.remove(command)) {
            throw new WorkerStoppedException("browser worker " + name + " has stopped");
        }
        return command.await();
    }

    /** Defers work to the owner loop, outside of event callbacks. Owner thread only. */
    void defer(Runnable runnable) {
        deferred.add(runnable);
    }

    PlaywrightSession openSession(final BrowserSessionOptions options) {
        return call(new Task<PlaywrightSession>() {
            @Override
            public PlaywrightSession run() {
                if (disconnected || !browser.isConnected()) {
                    disconnected = true;
                    handleDisconnect();
                    throw new WorkerStoppedException("browser of worker " + name + " is disconnected");
                }
                PlaywrightSession session = new PlaywrightSession(Worker.this, runtime, options, config.headless);
                try {
                    session.init();
                } catch (PlaywrightException e) {
                    if (!browser.isConnected() || Errors.isGone(e)) {
                        disconnected = true;
                        handleDisconnect();
                        throw new WorkerStoppedException("browser of worker " + name + " is disconnected");
                    }
                    throw Errors.map(e, "opening a browser session");
                }
                sessions.add(session);
                sessionCount.incrementAndGet();
                return session;
            }
        });
    }

    /** Owner thread: a session reached a terminal state. */
    void sessionEnded(PlaywrightSession session) {
        if (sessions.remove(session)) {
            sessionCount.decrementAndGet();
        }
    }

    private void loop() {
        nextReap = System.nanoTime() + REAP_INTERVAL_NANOS;
        while (running.get()) {
            Command<?> command = queue.poll();
            if (command != null) {
                command.execute();
                afterWork();
                continue;
            }
            if (pump()) {
                afterWork();
            } else {
                try {
                    command = queue.poll(PUMP_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    running.set(false);
                    break;
                }
                if (command != null) {
                    command.execute();
                    afterWork();
                }
            }
            long now = System.nanoTime();
            if (now >= nextReap) {
                nextReap = now + REAP_INTERVAL_NANOS;
                for (PlaywrightSession session : new ArrayList<PlaywrightSession>(sessions)) {
                    session.reap(now);
                }
            }
        }
        Command<?> leftover;
        while ((leftover = queue.poll()) != null) {
            leftover.fail(new WorkerStoppedException("browser worker " + name + " has stopped"));
        }
    }

    /** Pumps Playwright events for one slice; returns false if there is no page to pump on. */
    private boolean pump() {
        if (disconnected) {
            return false;
        }
        Page page = null;
        for (PlaywrightSession session : sessions) {
            page = session.pageForPump();
            if (page != null) {
                break;
            }
        }
        if (page == null) {
            return false;
        }
        try {
            page.waitForTimeout(PUMP_MILLIS);
        } catch (PlaywrightException e) {
            if (!browser.isConnected()) {
                disconnected = true;
            }
        }
        return true;
    }

    private void afterWork() {
        if (!deferred.isEmpty()) {
            List<Runnable> work = new ArrayList<Runnable>(deferred);
            deferred.clear();
            for (Runnable runnable : work) {
                try {
                    runnable.run();
                } catch (RuntimeException e) {
                    LOG.log(Level.FINE, "deferred browser task failed", e);
                }
            }
        }
        if (!disconnected) {
            for (PlaywrightSession session : new ArrayList<PlaywrightSession>(sessions)) {
                session.afterEvents();
            }
        }
        if (disconnected && running.get()) {
            handleDisconnect();
        }
    }

    /** Owner thread: the browser is gone; every session is lost and the worker stops. */
    private void handleDisconnect() {
        for (PlaywrightSession session : new ArrayList<PlaywrightSession>(sessions)) {
            session.markLost();
        }
        sessions.clear();
        sessionCount.set(0);
        running.set(false);
        closePlaywrightQuietly();
        runtime.workerStopped(this);
    }

    private void closeAll() {
        for (PlaywrightSession session : new ArrayList<PlaywrightSession>(sessions)) {
            session.closeOnOwner(com.aresstack.serp4j.browser.BrowserSessionState.CLOSED);
        }
        sessions.clear();
        sessionCount.set(0);
        running.set(false);
        if (browser != null) {
            try {
                browser.close();
            } catch (PlaywrightException e) {
                LOG.log(Level.FINE, "closing the browser failed", e);
            }
        }
        closePlaywrightQuietly();
    }

    private void closePlaywrightQuietly() {
        if (playwright != null) {
            try {
                playwright.close();
            } catch (RuntimeException e) {
                LOG.log(Level.FINE, "closing Playwright failed", e);
            }
            playwright = null;
        }
    }

    /** Closes all sessions, the browser and the driver; waits at most {@code timeoutMillis}. Idempotent. */
    void shutdown(long timeoutMillis) {
        Thread thread = owner;
        if (running.get()) {
            if (isOwnerThread()) {
                closeAll();
                return;
            }
            Command<Void> close = new Command<Void>(new Task<Void>() {
                @Override
                public Void run() {
                    closeAll();
                    return null;
                }
            });
            queue.add(close);
            try {
                close.result.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException e) {
                LOG.log(Level.WARNING, "browser worker " + name + " did not close within " + timeoutMillis
                        + " ms; interrupting its owner thread", e);
            }
            running.set(false);
        }
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(timeoutMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (thread.isAlive()) {
                thread.interrupt();
            }
        }
    }

    @Override
    public String toString() {
        return "Worker{" + name + ", alive=" + isAlive() + ", sessions=" + sessionCount.get() + '}';
    }

    /** A queued command with its result. */
    private static final class Command<T> {
        final Task<T> task;
        final CompletableFuture<T> result = new CompletableFuture<T>();

        Command(Task<T> task) {
            this.task = task;
        }

        void execute() {
            try {
                result.complete(task.run());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        }

        void fail(RuntimeException reason) {
            result.completeExceptionally(reason);
        }

        T await() {
            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        return result.get();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new IllegalStateException(cause);
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
