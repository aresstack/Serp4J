package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserCapability;
import com.aresstack.serp4j.browser.BrowserException;
import com.aresstack.serp4j.browser.BrowserSession;
import com.aresstack.serp4j.browser.BrowserSessionClosedException;
import com.aresstack.serp4j.browser.BrowserSessionId;
import com.aresstack.serp4j.browser.BrowserSessionLostException;
import com.aresstack.serp4j.browser.BrowserSessionOptions;
import com.aresstack.serp4j.browser.BrowserSessionState;
import com.aresstack.serp4j.browser.BrowserTimeoutException;
import com.aresstack.serp4j.browser.BrowserValue;
import com.aresstack.serp4j.browser.ElementSelector;
import com.aresstack.serp4j.browser.InteractionOptions;
import com.aresstack.serp4j.browser.NavigationRequest;
import com.aresstack.serp4j.browser.NavigationResult;
import com.aresstack.serp4j.browser.NetworkCapture;
import com.aresstack.serp4j.browser.NetworkFilter;
import com.aresstack.serp4j.browser.NetworkObserver;
import com.aresstack.serp4j.browser.ObservedExchange;
import com.aresstack.serp4j.browser.PageScript;
import com.aresstack.serp4j.browser.PageSnapshot;
import com.aresstack.serp4j.browser.ScriptEvaluationException;
import com.aresstack.serp4j.browser.SnapshotOptions;
import com.aresstack.serp4j.browser.UnsupportedBrowserCapabilityException;
import com.aresstack.serp4j.browser.UrlPolicy;
import com.aresstack.serp4j.browser.UrlPolicyViolationException;
import com.aresstack.serp4j.browser.WaitCondition;
import com.aresstack.serp4j.browser.WaitOutcome;
import com.aresstack.serp4j.browser.WaitUntil;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A {@link BrowserSession} backed by one {@code BrowserContext} with one {@code Page} (isolation
 * {@link com.aresstack.serp4j.browser.IsolationLevel#CONTEXT}). Public methods may be called from any thread; they
 * run on the owner thread of the {@link Worker}. Fields without {@code volatile} are owner-thread only.
 */
final class PlaywrightSession implements BrowserSession {

    private static final String ABOUT_BLANK = "about:blank";
    private static final long URL_POLL_MILLIS = 50L;
    private static final String TEXT_SCRIPT = "() => document.body ? document.body.innerText : ''";

    private final Worker worker;
    private final PlaywrightBrowserRuntime runtime;
    private final BrowserSessionOptions options;
    private final boolean headless;
    private final BrowserSessionId id = BrowserSessionId.random();
    private final AtomicBoolean released = new AtomicBoolean();
    private volatile BrowserSessionState state = BrowserSessionState.OPEN;

    private BrowserContext context;
    private Page page;
    private final long createdAt = System.nanoTime();
    private long lastActivity = System.nanoTime();
    private int navigations;
    private String blockedNavigation;
    private boolean closing;
    private boolean closedByUser;
    private boolean crashed;
    private final List<Page> popups = new ArrayList<Page>();
    private final NetworkTracker network = new NetworkTracker();

    PlaywrightSession(Worker worker, PlaywrightBrowserRuntime runtime, BrowserSessionOptions options, boolean headless) {
        this.worker = worker;
        this.runtime = runtime;
        this.options = options;
        this.headless = headless;
    }

    /** Owner thread: creates context and page. */
    void init() {
        worker.checkOwner();
        Browser browser = worker.browser();
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setAcceptDownloads(false)
                // Service workers would bypass request routing and therefore the URL policy.
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK);
        if (options.getLocale().isPresent()) {
            contextOptions.setLocale(options.getLocale().get().toLanguageTag());
        }
        context = browser.newContext(contextOptions);
        try {
            context.setDefaultTimeout(options.getDefaultTimeout().toMillis());
            final UrlPolicy policy = options.getUrlPolicy();
            if (!policy.isAllowAllHosts()) {
                context.route(new Predicate<String>() {
                    @Override
                    public boolean test(String url) {
                        return true;
                    }
                }, new Consumer<Route>() {
                    @Override
                    public void accept(Route route) {
                        Request request = route.request();
                        if (request.isNavigationRequest() && page != null && page.mainFrame().equals(request.frame())
                                && !policy.isAllowed(request.url())) {
                            blockedNavigation = request.url();
                            route.abort("blockedbyclient");
                        } else {
                            route.resume();
                        }
                    }
                });
            }
            context.onPage(new Consumer<Page>() {
                @Override
                public void accept(Page opened) {
                    popups.add(opened);
                }
            });
            page = context.newPage();
            page.onClose(new Consumer<Page>() {
                @Override
                public void accept(Page closedPage) {
                    if (!closing) {
                        closedByUser = true;
                    }
                }
            });
            page.onCrash(new Consumer<Page>() {
                @Override
                public void accept(Page crashedPage) {
                    crashed = true;
                }
            });
        } catch (PlaywrightException e) {
            try {
                context.close();
            } catch (PlaywrightException ignored) {
                // the context is unusable anyway
            }
            throw e;
        }
    }

    @Override
    public BrowserSessionId getId() {
        return id;
    }

    @Override
    public BrowserSessionState getState() {
        return state;
    }

    // ----------------------------------------------------------------------------------- command plumbing

    private <T> T run(final String action, final Worker.Task<T> body) {
        checkUsable();
        try {
            return worker.call(new Worker.Task<T>() {
                @Override
                public T run() {
                    begin();
                    try {
                        enforcePolicyOnCurrentPage();
                        return body.run();
                    } catch (PlaywrightException e) {
                        throw translate(e, action);
                    } finally {
                        end();
                    }
                }
            });
        } catch (WorkerStoppedException e) {
            throw terminal();
        }
    }

    private void checkUsable() {
        BrowserSessionState current = state;
        if (current.isTerminal()) {
            throw terminal();
        }
    }

    private BrowserException terminal() {
        BrowserSessionState current = state;
        if (current == BrowserSessionState.FAILED || !current.isTerminal()) {
            return new BrowserSessionLostException("browser session " + id + " is lost: the browser crashed or "
                    + "disconnected", null);
        }
        return new BrowserSessionClosedException(current, "browser session " + id + " is " + current);
    }

    private void begin() {
        checkUsable();
        long now = System.nanoTime();
        if (options.getMaxLifetime().isPresent() && now - createdAt >= options.getMaxLifetime().get().toNanos()) {
            expire("max lifetime of " + options.getMaxLifetime().get() + " reached");
        }
        if (options.getIdleTimeout().isPresent() && now - lastActivity >= options.getIdleTimeout().get().toNanos()) {
            expire("idle for more than " + options.getIdleTimeout().get());
        }
        state = BrowserSessionState.BUSY;
        blockedNavigation = null;
    }

    private void end() {
        lastActivity = System.nanoTime();
        if (state == BrowserSessionState.BUSY) {
            state = BrowserSessionState.OPEN;
        }
    }

    private void expire(String reason) {
        closeOnOwner(BrowserSessionState.EXPIRED);
        throw new BrowserSessionClosedException(BrowserSessionState.EXPIRED, "browser session " + id + " expired: "
                + reason);
    }

    private BrowserException translate(PlaywrightException e, String action) {
        if (crashed || worker.checkBrowserProcessGone() || !worker.browser().isConnected()) {
            markLost();
            return new BrowserSessionLostException(action + " failed: the browser crashed or disconnected", e);
        }
        if (closedByUser) {
            closeOnOwner(BrowserSessionState.CLOSED_BY_USER);
            return new BrowserSessionClosedException(BrowserSessionState.CLOSED_BY_USER,
                    action + " failed: the page was closed by the user");
        }
        BrowserException mapped = Errors.map(e, action);
        if (mapped instanceof BrowserSessionLostException) {
            markLost();
        }
        return mapped;
    }

    /** Owner thread: the page must never stay on a URL the policy forbids (late redirects, page navigations). */
    private void enforcePolicyOnCurrentPage() {
        String current = page.url();
        if (!options.getUrlPolicy().isAllowed(current)) {
            blank();
            throw new UrlPolicyViolationException(current, "the page navigated to a URL not allowed by "
                    + options.getUrlPolicy());
        }
    }

    private void checkInteractionPolicy(String action) {
        if (blockedNavigation != null) {
            String blocked = blockedNavigation;
            blockedNavigation = null;
            blank();
            throw new UrlPolicyViolationException(blocked, action + " started a navigation that is not allowed by "
                    + options.getUrlPolicy());
        }
        String current = page.url();
        if (!options.getUrlPolicy().isAllowed(current)) {
            blank();
            throw new UrlPolicyViolationException(current, action + " ended on a URL not allowed by "
                    + options.getUrlPolicy());
        }
    }

    private void blank() {
        try {
            page.navigate(ABOUT_BLANK);
        } catch (PlaywrightException e) {
            throw translate(e, "leaving a forbidden page");
        }
    }

    private double millis(Optional<Duration> timeout) {
        return Math.max(1L, timeout.orElse(options.getDefaultTimeout()).toMillis());
    }

    // ---------------------------------------------------------------------------------------- operations

    @Override
    public NavigationResult navigate(final NavigationRequest request) {
        if (request == null) {
            throw new NullPointerException("request must not be null");
        }
        return run("navigation to " + request.getUrl(), new Worker.Task<NavigationResult>() {
            @Override
            public NavigationResult run() {
                if (options.getMaxNavigations().isPresent()
                        && navigations >= options.getMaxNavigations().getAsInt()) {
                    expire("navigation budget of " + options.getMaxNavigations().getAsInt() + " exhausted");
                }
                navigations++;
                String target = request.getUrl();
                if (!options.getUrlPolicy().isAllowed(target)) {
                    blank();
                    throw new UrlPolicyViolationException(target, "navigation target not allowed by "
                            + options.getUrlPolicy());
                }
                long started = System.nanoTime();
                Response response;
                try {
                    response = page.navigate(target, new Page.NavigateOptions()
                            .setTimeout(millis(request.getTimeout()))
                            .setWaitUntil(waitUntil(request.getWaitUntil())));
                } catch (PlaywrightException e) {
                    if (blockedNavigation != null) {
                        String blocked = blockedNavigation;
                        blockedNavigation = null;
                        blank();
                        throw new UrlPolicyViolationException(blocked, "redirect target not allowed by "
                                + options.getUrlPolicy());
                    }
                    throw e;
                }
                String finalUrl = page.url();
                if (!options.getUrlPolicy().isAllowed(finalUrl)) {
                    blank();
                    throw new UrlPolicyViolationException(finalUrl, "redirect target not allowed by "
                            + options.getUrlPolicy());
                }
                Integer status = response == null ? null : response.status();
                return NavigationResult.of(target, finalUrl, status, Duration.ofNanos(System.nanoTime() - started));
            }
        });
    }

    private static WaitUntilState waitUntil(WaitUntil waitUntil) {
        switch (waitUntil) {
            case COMMIT:
                return WaitUntilState.COMMIT;
            case DOM_CONTENT_LOADED:
                return WaitUntilState.DOMCONTENTLOADED;
            case NETWORK_IDLE:
                return WaitUntilState.NETWORKIDLE;
            default:
                return WaitUntilState.LOAD;
        }
    }

    @Override
    public WaitOutcome await(final WaitCondition condition, final Duration timeout) {
        if (condition == null || timeout == null) {
            throw new NullPointerException("condition and timeout must not be null");
        }
        return run("waiting for " + condition, new Worker.Task<WaitOutcome>() {
            @Override
            public WaitOutcome run() {
                long deadline = System.nanoTime() + timeout.toNanos();
                if (!condition.isCssOnly()) {
                    return awaitLocator(condition, timeout);
                }
                if (WaitScript.hasUrlCondition(condition)) {
                    return pollWithUrl(condition, deadline);
                }
                Object spec = WaitScript.spec(condition, page.url());
                while (true) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return WaitOutcome.TIMED_OUT;
                    }
                    try {
                        page.waitForFunction(WaitScript.PREDICATE, spec, new Page.WaitForFunctionOptions()
                                .setTimeout(Math.max(1L, remaining / 1_000_000L)).setPollingInterval(50));
                        return WaitOutcome.SATISFIED;
                    } catch (TimeoutError e) {
                        return WaitOutcome.TIMED_OUT;
                    } catch (PlaywrightException e) {
                        if (!isContextDestroyed(e)) {
                            throw e;
                        }
                        // the page navigated while waiting; evaluate again in the new document
                    }
                }
            }
        });
    }

    /** Polls conditions containing URL parts: the URL is matched in Java (Java regex), the rest in the page. */
    private WaitOutcome pollWithUrl(WaitCondition condition, long deadline) {
        while (true) {
            try {
                Object result = page.evaluate(WaitScript.PREDICATE, WaitScript.spec(condition, page.url()));
                if (Boolean.TRUE.equals(result)) {
                    return WaitOutcome.SATISFIED;
                }
            } catch (PlaywrightException e) {
                if (!isContextDestroyed(e)) {
                    throw e;
                }
                // the page navigated while evaluating; evaluate again in the new document
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return WaitOutcome.TIMED_OUT;
            }
            page.waitForTimeout(Math.min(URL_POLL_MILLIS, Math.max(1L, remaining / 1_000_000L)));
        }
    }

    private WaitOutcome awaitLocator(WaitCondition condition, Duration timeout) {
        WaitForSelectorState waitState;
        switch (condition.getKind()) {
            case SELECTOR_PRESENT:
                waitState = WaitForSelectorState.ATTACHED;
                break;
            case SELECTOR_VISIBLE:
                waitState = WaitForSelectorState.VISIBLE;
                break;
            case SELECTOR_ABSENT:
                waitState = WaitForSelectorState.DETACHED;
                break;
            default:
                throw new IllegalArgumentException("role selectors are supported for present, visible and absent only: "
                        + condition);
        }
        try {
            locate(condition.getSelector().get()).waitFor(new Locator.WaitForOptions().setState(waitState)
                    .setTimeout(Math.max(1L, timeout.toMillis())));
            return WaitOutcome.SATISFIED;
        } catch (TimeoutError e) {
            return WaitOutcome.TIMED_OUT;
        }
    }

    private static boolean isContextDestroyed(PlaywrightException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("execution context was destroyed") || message.contains("most likely because of a navigation")
                || message.contains("cannot find context with specified id");
    }

    private Locator locate(ElementSelector selector) {
        if (selector.isCss()) {
            return page.locator(selector.getValue()).first();
        }
        AriaRole role;
        try {
            role = AriaRole.valueOf(selector.getValue().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown ARIA role: " + selector.getValue(), e);
        }
        Page.GetByRoleOptions roleOptions = new Page.GetByRoleOptions();
        if (selector.getAccessibleName().isPresent()) {
            roleOptions.setName(selector.getAccessibleName().get()).setExact(true);
        }
        return page.getByRole(role, roleOptions).first();
    }

    @Override
    public BrowserValue evaluate(final PageScript script) {
        if (script == null) {
            throw new NullPointerException("script must not be null");
        }
        return run("script " + script.getId(), new Worker.Task<BrowserValue>() {
            @Override
            public BrowserValue run() {
                Object result;
                try {
                    result = page.evaluate(script.getSource(), script.getArgument().toJava());
                } catch (PlaywrightException e) {
                    if (crashed || worker.checkBrowserProcessGone() || !worker.browser().isConnected() || Errors.isGone(e)) {
                        throw e;
                    }
                    throw new ScriptEvaluationException(script.getId(), "script " + script.getId() + " failed: "
                            + Errors.firstLine(e), e);
                }
                try {
                    return BrowserValue.fromJava(result);
                } catch (IllegalArgumentException e) {
                    throw new ScriptEvaluationException(script.getId(), "result of script " + script.getId()
                            + " cannot be converted: " + e.getMessage(), e);
                }
            }
        });
    }

    @Override
    public void click(final ElementSelector selector, final InteractionOptions interaction) {
        checkArgs(selector, interaction);
        run("click on " + selector, new Worker.Task<Void>() {
            @Override
            public Void run() {
                try {
                    locate(selector).click(new Locator.ClickOptions().setTimeout(millis(interaction.getTimeout())));
                } catch (TimeoutError e) {
                    throw new BrowserTimeoutException("no clickable element " + selector + " within "
                            + Duration.ofMillis((long) millis(interaction.getTimeout())), e);
                }
                checkInteractionPolicy("click on " + selector);
                return null;
            }
        });
    }

    @Override
    public void type(final ElementSelector selector, final String text, final InteractionOptions interaction) {
        checkArgs(selector, interaction);
        if (text == null) {
            throw new NullPointerException("text must not be null");
        }
        run("typing into " + selector, new Worker.Task<Void>() {
            @Override
            public Void run() {
                double timeout = millis(interaction.getTimeout());
                Locator locator = locate(selector);
                try {
                    if (interaction.getTypingDelay().isPresent()) {
                        locator.fill("", new Locator.FillOptions().setTimeout(timeout));
                        locator.pressSequentially(text, new Locator.PressSequentiallyOptions()
                                .setDelay(interaction.getTypingDelay().get().toMillis()).setTimeout(timeout));
                    } else {
                        locator.fill(text, new Locator.FillOptions().setTimeout(timeout));
                    }
                } catch (TimeoutError e) {
                    throw new BrowserTimeoutException("no editable element " + selector + " within "
                            + Duration.ofMillis((long) timeout), e);
                }
                return null;
            }
        });
    }

    @Override
    public void press(final ElementSelector selector, final String key, final InteractionOptions interaction) {
        checkArgs(selector, interaction);
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        run("pressing " + key + " on " + selector, new Worker.Task<Void>() {
            @Override
            public Void run() {
                double timeout = millis(interaction.getTimeout());
                try {
                    locate(selector).press(key, new Locator.PressOptions().setTimeout(timeout));
                } catch (TimeoutError e) {
                    throw new BrowserTimeoutException("no element " + selector + " within "
                            + Duration.ofMillis((long) timeout), e);
                }
                checkInteractionPolicy("pressing " + key + " on " + selector);
                return null;
            }
        });
    }

    private static void checkArgs(ElementSelector selector, InteractionOptions interaction) {
        if (selector == null || interaction == null) {
            throw new NullPointerException("selector and options must not be null");
        }
    }

    @Override
    public PageSnapshot snapshot(final SnapshotOptions snapshotOptions) {
        if (snapshotOptions == null) {
            throw new NullPointerException("options must not be null");
        }
        return run("snapshot", new Worker.Task<PageSnapshot>() {
            @Override
            public PageSnapshot run() {
                PageSnapshot.Builder builder = PageSnapshot.builder(page.url(), Instant.now()).title(page.title());
                if (snapshotOptions.isIncludeHtml()) {
                    builder.html(page.content(), snapshotOptions.getMaxChars());
                }
                if (snapshotOptions.isIncludeText()) {
                    Object text = page.evaluate(TEXT_SCRIPT);
                    builder.text(text == null ? "" : text.toString(), snapshotOptions.getMaxChars());
                }
                return builder.build();
            }
        });
    }

    @Override
    public NetworkObserver network() {
        checkUsable();
        if (!runtime.getInfo().supports(BrowserCapability.NETWORK_OBSERVATION)) {
            throw new UnsupportedBrowserCapabilityException(EnumSet.of(BrowserCapability.NETWORK_OBSERVATION),
                    "this runtime does not observe the network");
        }
        return new NetworkObserver() {
            @Override
            public NetworkCapture start(final NetworkFilter filter) {
                if (filter == null) {
                    throw new NullPointerException("filter must not be null");
                }
                NetworkTracker.Capture capture = run("starting a network capture", new Worker.Task<NetworkTracker.Capture>() {
                    @Override
                    public NetworkTracker.Capture run() {
                        network.ensureListening(context);
                        return network.start(filter);
                    }
                });
                return new CaptureHandle(capture);
            }
        };
    }

    /** Public face of a capture; every access runs on the owner thread. */
    private final class CaptureHandle implements NetworkCapture {
        private final NetworkTracker.Capture capture;

        CaptureHandle(NetworkTracker.Capture capture) {
            this.capture = capture;
        }

        @Override
        public List<ObservedExchange> getExchanges() {
            if (capture.closed || state.isTerminal()) {
                return java.util.Collections.emptyList();
            }
            return networkCall(new Worker.Task<List<ObservedExchange>>() {
                @Override
                public List<ObservedExchange> run() {
                    return network.exchanges(capture);
                }
            });
        }

        @Override
        public Optional<ObservedExchange> awaitFirst(final NetworkFilter additional, final Duration timeout) {
            if (additional == null || timeout == null) {
                throw new NullPointerException("filter and timeout must not be null");
            }
            return networkCall(new Worker.Task<Optional<ObservedExchange>>() {
                @Override
                public Optional<ObservedExchange> run() {
                    long deadline = System.nanoTime() + timeout.toNanos();
                    while (true) {
                        for (ObservedExchange exchange : network.exchanges(capture)) {
                            if (additional.matches(exchange)) {
                                return Optional.of(exchange);
                            }
                        }
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0 || capture.closed) {
                            return Optional.empty();
                        }
                        page.waitForTimeout(Math.min(Worker.PUMP_MILLIS, Math.max(1L, remaining / 1_000_000L)));
                    }
                }
            });
        }

        @Override
        public WaitOutcome awaitQuiet(final Duration quietPeriod, final Duration timeout) {
            if (quietPeriod == null || timeout == null) {
                throw new NullPointerException("quietPeriod and timeout must not be null");
            }
            return networkCall(new Worker.Task<WaitOutcome>() {
                @Override
                public WaitOutcome run() {
                    long deadline = System.nanoTime() + timeout.toNanos();
                    while (true) {
                        network.drain();
                        long now = System.nanoTime();
                        if (capture.inflight.isEmpty() && now - capture.lastActivity >= quietPeriod.toNanos()) {
                            return WaitOutcome.SATISFIED;
                        }
                        if (now >= deadline) {
                            return WaitOutcome.TIMED_OUT;
                        }
                        page.waitForTimeout(Math.min(Worker.PUMP_MILLIS, Math.max(1L, (deadline - now) / 1_000_000L)));
                    }
                }
            });
        }

        private <T> T networkCall(Worker.Task<T> task) {
            return run("network capture", task);
        }

        @Override
        public void close() {
            if (capture.closed) {
                return;
            }
            if (state.isTerminal()) {
                capture.closed = true;
                return;
            }
            try {
                worker.call(new Worker.Task<Void>() {
                    @Override
                    public Void run() {
                        network.stop(capture);
                        return null;
                    }
                });
            } catch (RuntimeException e) {
                capture.closed = true;
            }
        }
    }

    // ------------------------------------------------------------------------------------------ lifecycle

    @Override
    public void close() {
        if (state.isTerminal()) {
            return;
        }
        try {
            worker.call(new Worker.Task<Void>() {
                @Override
                public Void run() {
                    closeOnOwner(BrowserSessionState.CLOSED);
                    return null;
                }
            });
        } catch (RuntimeException e) {
            // worker gone: nothing left to close in the browser
            if (!state.isTerminal()) {
                state = BrowserSessionState.CLOSED;
            }
            release();
        }
    }

    /** Owner thread: ends the session with a terminal state. Idempotent. */
    void closeOnOwner(BrowserSessionState terminal) {
        if (state.isTerminal()) {
            return;
        }
        closing = true;
        state = terminal;
        network.clear();
        try {
            context.close();
        } catch (PlaywrightException e) {
            // already gone
        }
        worker.sessionEnded(this);
        release();
    }

    /** Owner thread: the browser is gone. */
    void markLost() {
        if (state.isTerminal()) {
            return;
        }
        state = BrowserSessionState.FAILED;
        network.clear();
        worker.sessionEnded(this);
        release();
    }

    private void release() {
        if (released.compareAndSet(false, true)) {
            runtime.release();
        }
    }

    /** Owner thread, after commands and pump slices: handles events recorded by callbacks. */
    void afterEvents() {
        if (state.isTerminal()) {
            return;
        }
        if (crashed) {
            markLost();
            try {
                context.close();
            } catch (PlaywrightException e) {
                // already gone
            }
            return;
        }
        if (closedByUser) {
            closeOnOwner(BrowserSessionState.CLOSED_BY_USER);
            return;
        }
        if (!popups.isEmpty()) {
            List<Page> opened = new ArrayList<Page>(popups);
            popups.clear();
            for (Page popup : opened) {
                if (popup != page) {
                    try {
                        popup.close();
                    } catch (PlaywrightException e) {
                        // already closed
                    }
                }
            }
        }
        if (network.hasCaptures()) {
            try {
                network.drain();
            } catch (PlaywrightException e) {
                // reported by the next command
            }
        }
    }

    /** Owner thread: closes the session if a time budget is exhausted while it is idle. */
    void reap(long now) {
        if (state != BrowserSessionState.OPEN) {
            return;
        }
        boolean lifetime = options.getMaxLifetime().isPresent()
                && now - createdAt >= options.getMaxLifetime().get().toNanos();
        boolean idle = options.getIdleTimeout().isPresent()
                && now - lastActivity >= options.getIdleTimeout().get().toNanos();
        if (lifetime || idle) {
            closeOnOwner(BrowserSessionState.EXPIRED);
        }
    }

    /** Owner thread: a page to pump events on, or {@code null}. */
    Page pageForPump() {
        return state.isTerminal() || page == null || page.isClosed() ? null : page;
    }

    @Override
    public String toString() {
        return "PlaywrightSession{" + id + ", state=" + state + ", worker=" + worker.getName()
                + (headless ? "" : ", headed") + '}';
    }
}
