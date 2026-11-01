package com.aresstack.serp4j.browser.testkit;

import com.aresstack.serp4j.browser.BrowserCapability;
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
import com.aresstack.serp4j.browser.ObstaclePolicy;
import com.aresstack.serp4j.browser.PageObstacle;
import com.aresstack.serp4j.browser.PageObstacleException;
import com.aresstack.serp4j.browser.PageScript;
import com.aresstack.serp4j.browser.PageSnapshot;
import com.aresstack.serp4j.browser.ScriptEvaluationException;
import com.aresstack.serp4j.browser.SnapshotOptions;
import com.aresstack.serp4j.browser.UnsupportedBrowserCapabilityException;
import com.aresstack.serp4j.browser.UrlPolicyViolationException;
import com.aresstack.serp4j.browser.WaitCondition;
import com.aresstack.serp4j.browser.WaitOutcome;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A session of {@link FakeBrowserRuntime}. Calls are serialized with the session monitor. Records navigations,
 * interactions and evaluated scripts for assertions in adapter tests.
 */
public final class FakeBrowserSession implements BrowserSession {

    private static final String ABOUT_BLANK = "about:blank";
    private static final int MAX_REDIRECTS = 10;

    private final FakeBrowserRuntime runtime;
    private final FakeWeb web;
    private final BrowserSessionOptions options;
    private final BrowserSessionId id = BrowserSessionId.random();
    private final long createdAt = System.nanoTime();

    private volatile BrowserSessionState state = BrowserSessionState.OPEN;
    private long lastActivity = System.nanoTime();
    private int navigationCount;
    private String url = ABOUT_BLANK;
    private FakePage page = FakePage.builder().build();
    private final Map<ElementSelector, String> inputValues = new LinkedHashMap<ElementSelector, String>();
    /** Cookie jar; for a persistent profile the profile's jar, used exclusively while the session holds the lease. */
    private final Map<String, Map<String, String>> cookiesByHost;
    private final List<Scheduled> timeline = new CopyOnWriteArrayList<Scheduled>();
    private final List<String> navigations = new CopyOnWriteArrayList<String>();
    private final List<String> interactions = new CopyOnWriteArrayList<String>();
    private final List<String> evaluatedScripts = new CopyOnWriteArrayList<String>();

    FakeBrowserSession(FakeBrowserRuntime runtime, FakeWeb web, BrowserSessionOptions options,
                       Map<String, Map<String, String>> cookieJar) {
        this.runtime = runtime;
        this.web = web;
        this.options = options;
        this.cookiesByHost = cookieJar;
    }

    /** An exchange that becomes visible at a point in time. */
    private static final class Scheduled {
        final long visibleAt;
        final FakeExchange exchange;
        final String url;

        Scheduled(long visibleAt, FakeExchange exchange, String url) {
            this.visibleAt = visibleAt;
            this.exchange = exchange;
            this.url = url;
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

    /**
     * @return the options the session was opened with
     */
    public BrowserSessionOptions getOptions() {
        return options;
    }

    /**
     * @return final URLs of all navigations, including those caused by clicks and key presses
     */
    public List<String> getNavigations() {
        return Collections.unmodifiableList(new ArrayList<String>(navigations));
    }

    /**
     * @return interactions in order, for example {@code click css(#next)}, {@code type css(#q) serp4j},
     * {@code press css(#q) Enter}
     */
    public List<String> getInteractions() {
        return Collections.unmodifiableList(new ArrayList<String>(interactions));
    }

    /**
     * @return ids of evaluated scripts in order
     */
    public List<String> getEvaluatedScripts() {
        return Collections.unmodifiableList(new ArrayList<String>(evaluatedScripts));
    }

    /**
     * @return the current page URL
     */
    public synchronized String getCurrentUrl() {
        return url;
    }

    @Override
    public synchronized NavigationResult navigate(NavigationRequest request) {
        if (request == null) {
            throw new NullPointerException("request must not be null");
        }
        begin();
        try {
            if (options.getMaxNavigations().isPresent() && navigationCount >= options.getMaxNavigations().getAsInt()) {
                expire("navigation budget of " + options.getMaxNavigations().getAsInt() + " exhausted");
            }
            navigationCount++;
            Duration timeout = request.getTimeout().orElse(options.getDefaultTimeout());
            long started = System.nanoTime();
            Loaded loaded = load(request.getUrl(), timeout);
            return NavigationResult.of(request.getUrl(), loaded.url, loaded.status,
                    Duration.ofNanos(System.nanoTime() - started));
        } finally {
            end();
        }
    }

    /** Result of loading a URL including redirects. */
    private static final class Loaded {
        final String url;
        final int status;

        Loaded(String url, int status) {
            this.url = url;
            this.status = status;
        }
    }

    private Loaded load(String target, Duration timeout) {
        if (!options.getUrlPolicy().isAllowed(target)) {
            blank();
            throw new UrlPolicyViolationException(target, "navigation target not allowed by " + options.getUrlPolicy());
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        String current = target;
        for (int hop = 0; ; hop++) {
            FakePage next = ABOUT_BLANK.equals(current) ? FakePage.builder().build()
                    : web.respond(new FakeRequest(current, cookiesFor(current)));
            long remaining = deadline - System.nanoTime();
            if (next.getDelay().toNanos() > remaining) {
                sleepNanos(Math.max(0, remaining));
                throw new BrowserTimeoutException("navigation to " + target + " timed out after " + timeout, null);
            }
            sleepNanos(next.getDelay().toNanos());
            storeCookies(current, next.getSetCookies());
            if (next.getRedirectTo() != null && hop < MAX_REDIRECTS) {
                current = resolve(current, next.getRedirectTo());
                if (!options.getUrlPolicy().isAllowed(current)) {
                    blank();
                    throw new UrlPolicyViolationException(current,
                            "redirect target not allowed by " + options.getUrlPolicy());
                }
                continue;
            }
            show(current, next);
            return new Loaded(current, next.getStatus());
        }
    }

    private void show(String newUrl, FakePage newPage) {
        this.url = newUrl;
        this.page = newPage;
        inputValues.clear();
        long now = System.nanoTime();
        for (FakeExchange exchange : newPage.getExchanges()) {
            timeline.add(new Scheduled(now + exchange.getDelay().toNanos(), exchange, resolve(newUrl, exchange.getUrl())));
        }
        navigations.add(newUrl);
    }

    private void blank() {
        this.url = ABOUT_BLANK;
        this.page = FakePage.builder().build();
        inputValues.clear();
    }

    @Override
    public synchronized WaitOutcome await(WaitCondition condition, Duration timeout) {
        if (condition == null || timeout == null) {
            throw new NullPointerException("condition and timeout must not be null");
        }
        begin();
        try {
            long quiet = quietNanos(condition);
            if (holds(condition) && quiet <= timeout.toNanos()) {
                sleepNanos(quiet);
                return WaitOutcome.SATISFIED;
            }
            sleepNanos(timeout.toNanos());
            return WaitOutcome.TIMED_OUT;
        } finally {
            end();
        }
    }

    @Override
    public void resolveObstacle(PageObstacle obstacle, WaitCondition resolved) {
        if (obstacle == null || resolved == null) {
            throw new NullPointerException("obstacle and resolved must not be null");
        }
        if (obstacle == PageObstacle.NONE) {
            throw new IllegalArgumentException("obstacle must not be NONE");
        }
        ObstaclePolicy policy = options.getObstaclePolicy();
        if (!policy.isWaitForHuman()) {
            checkUsable();
            throw new PageObstacleException(obstacle, false, obstacle + " on " + url + " (policy FAIL_FAST)");
        }
        if (await(resolved, policy.getHumanTimeout().get()) != WaitOutcome.SATISFIED) {
            throw new PageObstacleException(obstacle, true, obstacle + " on " + url + " was not resolved within "
                    + policy.getHumanTimeout().get());
        }
    }

    private boolean holds(WaitCondition condition) {
        switch (condition.getKind()) {
            case SELECTOR_PRESENT:
                return count(condition.getSelector().get(), false) > 0;
            case SELECTOR_VISIBLE:
                return count(condition.getSelector().get(), true) > 0;
            case SELECTOR_ABSENT:
                return count(condition.getSelector().get(), false) == 0;
            case SELECTOR_COUNT_AT_LEAST:
                return count(condition.getSelector().get(), false) >= condition.getCount();
            case URL_MATCHES:
                return condition.getUrlPattern().get().matcher(url).find();
            case DOM_QUIET:
                return !condition.getSelector().isPresent() || count(condition.getSelector().get(), false) > 0;
            case ALL_OF:
                for (WaitCondition child : condition.getChildren()) {
                    if (!holds(child)) {
                        return false;
                    }
                }
                return true;
            default:
                return false;
        }
    }

    private static long quietNanos(WaitCondition condition) {
        if (condition.getKind() == WaitCondition.Kind.DOM_QUIET) {
            return condition.getQuietPeriod().get().toNanos();
        }
        long max = 0;
        for (WaitCondition child : condition.getChildren()) {
            max = Math.max(max, quietNanos(child));
        }
        return max;
    }

    private int count(ElementSelector selector, boolean visibleOnly) {
        int total = 0;
        for (Map.Entry<ElementSelector, FakePage.Element> entry : page.getElements().entrySet()) {
            if (matches(selector, entry.getKey()) && (!visibleOnly || entry.getValue().visible)) {
                total += entry.getValue().count;
            }
        }
        return total;
    }

    private static boolean matches(ElementSelector wanted, ElementSelector declared) {
        if (wanted.equals(declared)) {
            return true;
        }
        return wanted.getKind() == ElementSelector.Kind.ROLE && declared.getKind() == ElementSelector.Kind.ROLE
                && wanted.getValue().equals(declared.getValue()) && !wanted.getAccessibleName().isPresent();
    }

    private ElementSelector find(ElementSelector selector, InteractionOptions interaction, String action) {
        for (ElementSelector declared : page.getElements().keySet()) {
            if (matches(selector, declared) && page.getElements().get(declared).count > 0) {
                return declared;
            }
        }
        Duration timeout = interaction.getTimeout().orElse(options.getDefaultTimeout());
        sleepNanos(timeout.toNanos());
        throw new BrowserTimeoutException(action + " " + selector + ": no matching element within " + timeout, null);
    }

    @Override
    public synchronized BrowserValue evaluate(PageScript script) {
        if (script == null) {
            throw new NullPointerException("script must not be null");
        }
        begin();
        try {
            evaluatedScripts.add(script.getId());
            FakeScript fake = page.getScripts().get(script.getId());
            if (fake == null) {
                fake = web.globalScript(script.getId());
            }
            if (fake == null) {
                throw new ScriptEvaluationException(script.getId(),
                        "no fake result registered for script " + script.getId() + " on " + url, null);
            }
            try {
                BrowserValue value = fake.evaluate(new FakeScriptContext(url, page.getTitle(), script.getArgument(),
                        options.getLocale().orElse(null), inputValues));
                return value == null ? BrowserValue.nullValue() : value;
            } catch (ScriptEvaluationException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ScriptEvaluationException(script.getId(), "script " + script.getId() + " failed: " + e, e);
            }
        } finally {
            end();
        }
    }

    @Override
    public synchronized void click(ElementSelector selector, InteractionOptions interaction) {
        checkArgs(selector, interaction);
        begin();
        try {
            ElementSelector declared = find(selector, interaction, "click");
            interactions.add("click " + selector);
            String target = page.getClickTargets().get(declared);
            if (target != null) {
                load(resolve(url, target), options.getDefaultTimeout());
            }
        } finally {
            end();
        }
    }

    @Override
    public synchronized void type(ElementSelector selector, String text, InteractionOptions interaction) {
        checkArgs(selector, interaction);
        if (text == null) {
            throw new NullPointerException("text must not be null");
        }
        begin();
        try {
            ElementSelector declared = find(selector, interaction, "type into");
            interactions.add("type " + selector + " " + text);
            inputValues.put(declared, text);
        } finally {
            end();
        }
    }

    @Override
    public synchronized void press(ElementSelector selector, String key, InteractionOptions interaction) {
        checkArgs(selector, interaction);
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        begin();
        try {
            ElementSelector declared = find(selector, interaction, "press on");
            interactions.add("press " + selector + " " + key);
            String template = page.getEnterTargets().get(declared);
            if ("Enter".equals(key) && template != null) {
                String value = inputValues.containsKey(declared) ? inputValues.get(declared) : "";
                load(resolve(url, template.replace("{value}", encode(value))), options.getDefaultTimeout());
            }
        } finally {
            end();
        }
    }

    private static void checkArgs(ElementSelector selector, InteractionOptions interaction) {
        if (selector == null || interaction == null) {
            throw new NullPointerException("selector and options must not be null");
        }
    }

    @Override
    public synchronized PageSnapshot snapshot(SnapshotOptions snapshotOptions) {
        if (snapshotOptions == null) {
            throw new NullPointerException("options must not be null");
        }
        begin();
        try {
            PageSnapshot.Builder builder = PageSnapshot.builder(url, Instant.now()).title(page.getTitle());
            if (snapshotOptions.isIncludeHtml()) {
                builder.html(page.getHtml(), snapshotOptions.getMaxChars());
            }
            if (snapshotOptions.isIncludeText()) {
                builder.text(page.getText(), snapshotOptions.getMaxChars());
            }
            return builder.build();
        } finally {
            end();
        }
    }

    @Override
    public NetworkObserver network() {
        checkUsable();
        if (!runtime.supports(BrowserCapability.NETWORK_OBSERVATION)) {
            throw new UnsupportedBrowserCapabilityException(EnumSet.of(BrowserCapability.NETWORK_OBSERVATION),
                    "the fake runtime was built without NETWORK_OBSERVATION");
        }
        return new NetworkObserver() {
            @Override
            public NetworkCapture start(NetworkFilter filter) {
                if (filter == null) {
                    throw new NullPointerException("filter must not be null");
                }
                checkUsable();
                return new Capture(filter, System.nanoTime());
            }
        };
    }

    /** A capture over the timeline of the session. */
    private final class Capture implements NetworkCapture {
        private final NetworkFilter filter;
        private final long startedAt;
        private volatile boolean closed;

        Capture(NetworkFilter filter, long startedAt) {
            this.filter = filter;
            this.startedAt = startedAt;
        }

        @Override
        public List<ObservedExchange> getExchanges() {
            if (closed) {
                return Collections.emptyList();
            }
            long now = System.nanoTime();
            List<Scheduled> completed = new ArrayList<Scheduled>();
            for (Scheduled scheduled : timeline) {
                if (scheduled.visibleAt >= startedAt && scheduled.visibleAt <= now) {
                    completed.add(scheduled);
                }
            }
            // completion order, like the real runtime, not registration order
            Collections.sort(completed, new java.util.Comparator<Scheduled>() {
                @Override
                public int compare(Scheduled a, Scheduled b) {
                    return Long.compare(a.visibleAt, b.visibleAt);
                }
            });
            List<ObservedExchange> result = new ArrayList<ObservedExchange>();
            for (Scheduled scheduled : completed) {
                ObservedExchange exchange = toObserved(scheduled);
                if (filter.matches(exchange)) {
                    result.add(exchange);
                }
            }
            return Collections.unmodifiableList(result);
        }

        private ObservedExchange toObserved(Scheduled scheduled) {
            FakeExchange exchange = scheduled.exchange;
            ObservedExchange.Builder builder = ObservedExchange.builder(exchange.getMethod(), scheduled.url,
                    exchange.getStatus(), Instant.now()).resourceType(exchange.getResourceType())
                    .responseHeaders(exchange.getHeaders());
            if (filter.isCaptureBody()) {
                builder.body(exchange.getBody(), filter.getMaxBodyBytes());
            }
            return builder.build();
        }

        @Override
        public Optional<ObservedExchange> awaitFirst(NetworkFilter additional, Duration timeout) {
            if (additional == null || timeout == null) {
                throw new NullPointerException("filter and timeout must not be null");
            }
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                checkUsable();
                for (ObservedExchange exchange : getExchanges()) {
                    if (additional.matches(exchange)) {
                        return Optional.of(exchange);
                    }
                }
                if (System.nanoTime() >= deadline || closed) {
                    return Optional.empty();
                }
                sleepNanos(Math.min(10_000_000L, Math.max(0, deadline - System.nanoTime())));
            }
        }

        @Override
        public WaitOutcome awaitQuiet(Duration quietPeriod, Duration timeout) {
            if (quietPeriod == null || timeout == null) {
                throw new NullPointerException("quietPeriod and timeout must not be null");
            }
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                checkUsable();
                long last = startedAt;
                for (Scheduled scheduled : timeline) {
                    // only exchanges this capture matches count, finished or still in flight
                    if (scheduled.visibleAt >= startedAt && filter.matches(toObserved(scheduled))) {
                        last = Math.max(last, scheduled.visibleAt);
                    }
                }
                long now = System.nanoTime();
                if (now - last >= quietPeriod.toNanos()) {
                    return WaitOutcome.SATISFIED;
                }
                if (now >= deadline) {
                    return WaitOutcome.TIMED_OUT;
                }
                sleepNanos(Math.min(10_000_000L, deadline - now));
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Override
    public void close() {
        terminate(BrowserSessionState.CLOSED);
    }

    void crash() {
        terminate(BrowserSessionState.FAILED);
    }

    private void terminate(BrowserSessionState terminal) {
        synchronized (this) {
            if (state.isTerminal()) {
                return;
            }
            state = terminal;
            timeline.clear();
        }
        runtime.released(this);
    }

    private void expire(String reason) {
        terminate(BrowserSessionState.EXPIRED);
        throw new BrowserSessionClosedException(BrowserSessionState.EXPIRED, "session " + id + " expired: " + reason);
    }

    private void checkUsable() {
        BrowserSessionState current = state;
        if (current == BrowserSessionState.FAILED) {
            throw new BrowserSessionLostException("session " + id + " lost: the fake browser crashed", null);
        }
        if (current.isTerminal()) {
            throw new BrowserSessionClosedException(current, "session " + id + " is " + current);
        }
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
    }

    private void end() {
        lastActivity = System.nanoTime();
        if (state == BrowserSessionState.BUSY) {
            state = BrowserSessionState.OPEN;
        }
    }

    private Map<String, String> cookiesFor(String target) {
        Map<String, String> cookies = cookiesByHost.get(host(target));
        return cookies == null ? Collections.<String, String>emptyMap() : cookies;
    }

    private void storeCookies(String target, Map<String, String> cookies) {
        if (cookies.isEmpty()) {
            return;
        }
        String host = host(target);
        Map<String, String> jar = cookiesByHost.get(host);
        if (jar == null) {
            jar = new LinkedHashMap<String, String>();
            cookiesByHost.put(host, jar);
        }
        jar.putAll(cookies);
    }

    private static String host(String target) {
        try {
            String host = new URI(target).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    private static String resolve(String base, String target) {
        try {
            return new URI(base).resolve(target).toString();
        } catch (Exception e) {
            return target;
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleepNanos(long nanos) {
        if (nanos <= 0) {
            return;
        }
        try {
            Thread.sleep(nanos / 1_000_000L, (int) (nanos % 1_000_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrowserTimeoutException("interrupted", e);
        }
    }

    @Override
    public String toString() {
        return "FakeBrowserSession{" + id + ", state=" + state + ", url=" + url + '}';
    }
}
