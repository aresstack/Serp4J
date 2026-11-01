package com.aresstack.serp4j.browser.testkit;

import com.aresstack.serp4j.browser.BrowserCapability;
import com.aresstack.serp4j.browser.BrowserCapacityException;
import com.aresstack.serp4j.browser.BrowserProfileInUseException;
import com.aresstack.serp4j.browser.BrowserReadiness;
import com.aresstack.serp4j.browser.BrowserRuntime;
import com.aresstack.serp4j.browser.BrowserRuntimeInfo;
import com.aresstack.serp4j.browser.BrowserSession;
import com.aresstack.serp4j.browser.BrowserSessionClosedException;
import com.aresstack.serp4j.browser.BrowserSessionLostException;
import com.aresstack.serp4j.browser.BrowserSessionOptions;
import com.aresstack.serp4j.browser.BrowserSessionState;
import com.aresstack.serp4j.browser.BrowserTimeoutException;
import com.aresstack.serp4j.browser.BrowserUnavailableException;
import com.aresstack.serp4j.browser.BrowserValue;
import com.aresstack.serp4j.browser.ElementSelector;
import com.aresstack.serp4j.browser.InteractionOptions;
import com.aresstack.serp4j.browser.NavigationRequest;
import com.aresstack.serp4j.browser.NavigationResult;
import com.aresstack.serp4j.browser.NetworkCapture;
import com.aresstack.serp4j.browser.NetworkFilter;
import com.aresstack.serp4j.browser.ObservedExchange;
import com.aresstack.serp4j.browser.ObstaclePolicy;
import com.aresstack.serp4j.browser.PageObstacle;
import com.aresstack.serp4j.browser.PageObstacleException;
import com.aresstack.serp4j.browser.ProfileMode;
import com.aresstack.serp4j.browser.PageScript;
import com.aresstack.serp4j.browser.PageSnapshot;
import com.aresstack.serp4j.browser.ScriptEvaluationException;
import com.aresstack.serp4j.browser.SnapshotOptions;
import com.aresstack.serp4j.browser.UnsupportedBrowserCapabilityException;
import com.aresstack.serp4j.browser.UrlPolicy;
import com.aresstack.serp4j.browser.UrlPolicyViolationException;
import com.aresstack.serp4j.browser.WaitCondition;
import com.aresstack.serp4j.browser.WaitOutcome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * TCK for {@link BrowserRuntime} implementations (WP3 §5.4, implementation-plan.md Paket F): navigation, waits,
 * scripts, interaction, snapshots, timeouts, idempotent close, isolation, capacity, budgets, URL policy after
 * redirects, closed and crashed browsers, serialized calls from many threads and, if declared, network observation
 * with event delivery in idle state.
 *
 * <p>Subclasses provide the site and a factory for runtimes:</p>
 *
 * <pre>
 * class MyRuntimeContractTest extends BrowserRuntimeContractTest {
 *     static TckHttpServer server;
 *     &#64;BeforeAll static void startServer() throws IOException { server = TckHttpServer.start(); }
 *     &#64;AfterAll static void stopServer() { server.close(); }
 *     &#64;Override protected TckSite site() { return server; }
 *     &#64;Override protected BrowserRuntime newRuntime() { return MyRuntime.builder().build(); }
 * }
 * </pre>
 *
 * <p>If {@link BrowserRuntime#probe()} is not ready, all tests are skipped with a readable reason, or fail when the
 * system property {@value #REQUIRED_PROPERTY} is {@code true} (CI job with a browser). They are never silently
 * green.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BrowserRuntimeContractTest {

    /** System property that turns a non-ready runtime from a skip into a failure. */
    public static final String REQUIRED_PROPERTY = "serp4j.browserTests.required";

    private static final Duration SHORT = Duration.ofMillis(400);
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private BrowserRuntime runtime;
    private final List<BrowserSession> opened = new ArrayList<BrowserSession>();
    private final List<BrowserRuntime> extraRuntimes = new ArrayList<BrowserRuntime>();

    /**
     * @return the site under test; must be running before the first test
     */
    protected abstract TckSite site();

    /**
     * @return a new, not yet started runtime; the TCK closes it
     */
    protected abstract BrowserRuntime newRuntime();

    /**
     * Records the processes the test JVM has started so far, for {@link #crashBrowser(BrowserRuntime, Object)} and
     * {@link #assertNoOrphanedProcesses(Object, Object)}. Default: {@code null} (no process checks).
     *
     * @return an implementation-specific snapshot, or {@code null}
     */
    protected Object processSnapshot() {
        return null;
    }

    /**
     * Crashes the browser of the runtime, for example by killing the browser processes started after
     * {@code before}. Return {@code false} if the implementation cannot simulate a crash; the test is then skipped.
     *
     * @param runtime runtime with at least one open session
     * @param before  {@link #processSnapshot()} taken before the runtime was created
     * @return whether a crash was caused
     * @throws Exception if the crash cannot be caused
     */
    protected boolean crashBrowser(BrowserRuntime runtime, Object before) throws Exception {
        return false;
    }

    /**
     * Asserts that a closed runtime left no browser or driver processes behind: every process in {@code running}
     * that was not in {@code before} must have ended. Default: no check.
     *
     * @param before  {@link #processSnapshot()} taken before the runtime was created
     * @param running {@link #processSnapshot()} taken while the runtime had an open session
     * @throws Exception if the check fails
     */
    protected void assertNoOrphanedProcesses(Object before, Object running) throws Exception {
    }

    /**
     * Makes the browser of the runtime hang so that closing it cannot finish normally, for example by stopping the
     * browser processes started after {@code before}. Return {@code false} if the implementation cannot simulate
     * that; the test is then skipped.
     *
     * @param runtime runtime with at least one open session
     * @param before  {@link #processSnapshot()} taken before the runtime was created
     * @return whether the browser now hangs
     * @throws Exception if the hang cannot be caused
     */
    protected boolean freezeBrowser(BrowserRuntime runtime, Object before) throws Exception {
        return false;
    }

    /**
     * @return upper bound for closing a runtime whose browser hangs: its close timeout plus the hard kill
     */
    protected Duration hungCloseBound() {
        return Duration.ofSeconds(60);
    }

    /**
     * @return default timeout of the sessions the TCK opens; generous because CI machines are slow
     */
    protected Duration defaultTimeout() {
        return Duration.ofSeconds(20);
    }

    @BeforeAll
    final void startRuntime() {
        runtime = newRuntime();
        BrowserReadiness readiness = runtime.probe();
        if (!readiness.isReady()) {
            runtime.close();
            runtime = null;
            String reason = "browser runtime not ready: " + readiness;
            if (Boolean.getBoolean(REQUIRED_PROPERTY)) {
                Assertions.fail(reason + " (" + REQUIRED_PROPERTY + "=true)");
            }
            Assumptions.abort(reason);
        }
    }

    @AfterEach
    final void closeSessions() {
        for (BrowserSession session : opened) {
            session.close();
        }
        opened.clear();
        for (BrowserRuntime extra : extraRuntimes) {
            extra.close();
        }
        extraRuntimes.clear();
    }

    @AfterAll
    final void closeRuntime() {
        if (runtime != null) {
            runtime.close();
        }
    }

    /**
     * @return the shared runtime of this test class
     */
    protected final BrowserRuntime runtime() {
        return runtime;
    }

    /**
     * @return default options of the TCK sessions
     */
    protected final BrowserSessionOptions.Builder options() {
        return BrowserSessionOptions.builder().defaultTimeout(defaultTimeout()).acquireTimeout(defaultTimeout());
    }

    /**
     * Opens a session on the shared runtime that is closed after the test.
     *
     * @param options options
     * @return the session
     */
    protected final BrowserSession open(BrowserSessionOptions options) {
        return track(runtime.openSession(options));
    }

    private BrowserSession open() {
        return open(options().build());
    }

    private BrowserSession track(BrowserSession session) {
        opened.add(session);
        return session;
    }

    private BrowserRuntime extraRuntime() {
        BrowserRuntime extra = newRuntime();
        extraRuntimes.add(extra);
        return extra;
    }

    private NavigationResult go(BrowserSession session, String path) {
        return session.navigate(NavigationRequest.to(site().url(path)));
    }

    private static String title(BrowserSession session) {
        return session.snapshot(SnapshotOptions.urlAndTitle()).getTitle();
    }

    private static <T extends Throwable> T expect(Class<T> type, Executable executable) {
        return Assertions.assertThrows(type, executable);
    }

    // ---------------------------------------------------------------------------------------------- runtime

    @Test
    void info_describes_the_runtime_consistently() {
        BrowserRuntimeInfo first = runtime.getInfo();
        BrowserRuntimeInfo second = runtime.getInfo();
        Assertions.assertEquals(first.getBackendId(), second.getBackendId());
        Assertions.assertTrue(first.getMaxConcurrentSessions() >= 1);
        Assertions.assertTrue(first.supports(BrowserCapability.JAVASCRIPT), "every TCK runtime evaluates scripts");
        Assertions.assertEquals(first.getMaxConcurrentSessions() > 1, first.supports(BrowserCapability.PARALLEL_SESSIONS),
                "PARALLEL_SESSIONS is declared exactly when more than one session is allowed");
    }

    @Test
    void probe_is_ready_and_repeatable() {
        Assertions.assertTrue(runtime.probe().isReady());
        Assertions.assertTrue(runtime.probe().isReady());
    }

    // ------------------------------------------------------------------------------------------- navigation

    @Test
    void navigate_loads_the_page_and_reports_url_status_and_title() {
        BrowserSession session = open();
        Assertions.assertEquals(BrowserSessionState.OPEN, session.getState());
        NavigationResult result = go(session, TckPages.HOME);
        Assertions.assertEquals(site().url(TckPages.HOME), result.getFinalUrl());
        Assertions.assertEquals(200, result.getHttpStatus().getAsInt());
        Assertions.assertEquals(PageObstacle.NONE, result.getObstacle());
        Assertions.assertFalse(result.isRedirected());
        Assertions.assertEquals(TckPages.HOME_TITLE, title(session));
        Assertions.assertEquals(BrowserSessionState.OPEN, session.getState());
    }

    @Test
    void http_error_status_is_a_result_not_an_exception() {
        BrowserSession session = open();
        NavigationResult result = go(session, TckPages.NOT_FOUND);
        Assertions.assertEquals(404, result.getHttpStatus().getAsInt());
        Assertions.assertEquals(PageObstacle.HTTP_ERROR, result.getObstacle());
        Assertions.assertEquals(TckPages.NOT_FOUND_TITLE, title(session));
    }

    @Test
    void redirects_are_followed_and_reported() {
        BrowserSession session = open();
        NavigationResult local = go(session, TckPages.REDIRECT_LOCAL);
        Assertions.assertEquals(site().url(TckPages.NEXT), local.getFinalUrl());
        Assertions.assertTrue(local.isRedirected());
        Assertions.assertEquals(PageObstacle.NONE, local.getObstacle());
        Assertions.assertEquals(200, local.getHttpStatus().getAsInt());

        NavigationResult foreign = go(session, TckPages.REDIRECT_FOREIGN);
        Assertions.assertEquals(site().foreignUrl(TckPages.NEXT), foreign.getFinalUrl());
        Assertions.assertEquals(PageObstacle.FOREIGN_HOST_REDIRECT, foreign.getObstacle());
    }

    @Test
    void navigation_timeout_is_typed_and_the_session_stays_usable() {
        final BrowserSession session = open();
        expect(BrowserTimeoutException.class, new Executable() {
            @Override
            public void execute() {
                session.navigate(NavigationRequest.to(site().url(TckPages.SLOW)).timeout(SHORT));
            }
        });
        Assertions.assertFalse(session.getState().isTerminal());
        go(session, TckPages.NEXT);
        Assertions.assertEquals(TckPages.NEXT_TITLE, title(session));
    }

    // ------------------------------------------------------------------------------------------------ waits

    @Test
    void await_reports_satisfied_and_timed_out_conditions() {
        BrowserSession session = open();
        go(session, TckPages.HOME);
        Duration timeout = Duration.ofSeconds(5);
        Assertions.assertEquals(WaitOutcome.SATISFIED,
                session.await(WaitCondition.present(ElementSelector.css("#marker")), timeout));
        Assertions.assertEquals(WaitOutcome.SATISFIED,
                session.await(WaitCondition.visible(ElementSelector.css("#items")), timeout));
        Assertions.assertEquals(WaitOutcome.SATISFIED,
                session.await(WaitCondition.absent(ElementSelector.css("#does-not-exist")), timeout));
        Assertions.assertEquals(WaitOutcome.SATISFIED,
                session.await(WaitCondition.countAtLeast(ElementSelector.css("#items li"), 3), timeout));
        Assertions.assertEquals(WaitOutcome.SATISFIED,
                session.await(WaitCondition.present(ElementSelector.role("link", "Next")), timeout));
        Assertions.assertEquals(WaitOutcome.SATISFIED,
                session.await(WaitCondition.urlMatches("localhost|tck\\.serp4j\\.test"), timeout));
        Assertions.assertEquals(WaitOutcome.SATISFIED, session.await(WaitCondition.allOf(
                WaitCondition.present(ElementSelector.css("#marker")),
                WaitCondition.domQuiet(ElementSelector.css("#items"), Duration.ofMillis(200))), timeout));

        Assertions.assertEquals(WaitOutcome.TIMED_OUT,
                session.await(WaitCondition.present(ElementSelector.css("#does-not-exist")), SHORT));
        Assertions.assertEquals(WaitOutcome.TIMED_OUT,
                session.await(WaitCondition.visible(ElementSelector.css("#hidden")), SHORT));
        Assertions.assertEquals(WaitOutcome.TIMED_OUT,
                session.await(WaitCondition.countAtLeast(ElementSelector.css("#items li"), 4), SHORT));
        Assertions.assertFalse(session.getState().isTerminal());
    }

    // ---------------------------------------------------------------------------------------------- scripts

    @Test
    void evaluate_returns_a_neutral_value_tree() {
        BrowserSession session = open();
        go(session, TckPages.HOME);
        BrowserValue value = session.evaluate(PageScript.of(TckPages.STRUCTURE_SCRIPT_ID, TckPages.STRUCTURE_SCRIPT));
        Assertions.assertEquals(BrowserValue.Type.MAP, value.getType());
        Assertions.assertEquals(TckPages.HOME_TITLE, value.getString("title").get());
        Assertions.assertEquals(3L, value.path("count").get().asLong());
        Assertions.assertEquals("beta", value.path("items", 1).get().asString());
        Assertions.assertTrue(value.path("flag").get().asBoolean());
        Assertions.assertTrue(value.path("nothing").get().isNull());

        Map<String, BrowserValue> argument = new LinkedHashMap<String, BrowserValue>();
        argument.put("a", BrowserValue.of(2));
        argument.put("b", BrowserValue.of(3));
        BrowserValue sum = session.evaluate(PageScript.of(TckPages.SUM_SCRIPT_ID, TckPages.SUM_SCRIPT)
                .withArgument(BrowserValue.map(argument)));
        Assertions.assertEquals(5L, sum.asLong());
    }

    @Test
    void failing_script_is_typed_and_the_session_stays_usable() {
        final BrowserSession session = open();
        go(session, TckPages.HOME);
        ScriptEvaluationException failure = expect(ScriptEvaluationException.class, new Executable() {
            @Override
            public void execute() {
                session.evaluate(PageScript.of(TckPages.THROWING_SCRIPT_ID, TckPages.THROWING_SCRIPT));
            }
        });
        Assertions.assertEquals(TckPages.THROWING_SCRIPT_ID, failure.getScriptId());
        Assertions.assertEquals(TckPages.HOME_TITLE, title(session));
    }

    @Test
    void locale_is_applied_when_declared() {
        Assumptions.assumeTrue(runtime.getInfo().supports(BrowserCapability.LOCALE_EMULATION),
                "runtime does not declare LOCALE_EMULATION");
        BrowserSession session = open(options().locale(Locale.GERMANY).build());
        go(session, TckPages.HOME);
        BrowserValue language = session.evaluate(PageScript.of(TckPages.LANGUAGE_SCRIPT_ID, TckPages.LANGUAGE_SCRIPT));
        Assertions.assertEquals("de-DE", language.asString());
    }

    // ---------------------------------------------------------------------------------------- interaction

    @Test
    void click_follows_a_link() {
        BrowserSession session = open();
        go(session, TckPages.HOME);
        session.click(ElementSelector.css("#next"), InteractionOptions.defaults());
        Assertions.assertEquals(WaitOutcome.SATISFIED,
                session.await(WaitCondition.urlMatches(TckPages.NEXT + "$"), Duration.ofSeconds(10)));
        Assertions.assertEquals(TckPages.NEXT_TITLE, title(session));
    }

    @Test
    void type_and_press_submit_a_form() {
        BrowserSession session = open();
        go(session, TckPages.HOME);
        session.type(ElementSelector.css("#q"), "serp4j", InteractionOptions.defaults());
        session.press(ElementSelector.css("#q"), "Enter", InteractionOptions.defaults());
        Assertions.assertEquals(WaitOutcome.SATISFIED,
                session.await(WaitCondition.present(ElementSelector.css("#result")), Duration.ofSeconds(10)));
        Assertions.assertEquals(TckPages.SEARCH_TITLE_PREFIX + "serp4j", title(session));
        Assertions.assertTrue(session.snapshot(SnapshotOptions.urlAndTitle()).getUrl().contains("q=serp4j"));
    }

    @Test
    void interaction_with_a_missing_element_times_out() {
        final BrowserSession session = open();
        go(session, TckPages.HOME);
        expect(BrowserTimeoutException.class, new Executable() {
            @Override
            public void execute() {
                session.click(ElementSelector.css("#does-not-exist"), InteractionOptions.defaults().timeout(SHORT));
            }
        });
        Assertions.assertFalse(session.getState().isTerminal());
    }

    // -------------------------------------------------------------------------------------------- snapshot

    @Test
    void snapshot_captures_url_title_html_and_text_within_limits() {
        BrowserSession session = open();
        go(session, TckPages.HOME);
        PageSnapshot plain = session.snapshot(SnapshotOptions.urlAndTitle());
        Assertions.assertEquals(site().url(TckPages.HOME), plain.getUrl());
        Assertions.assertFalse(plain.getHtml().isPresent());
        Assertions.assertFalse(plain.getText().isPresent());

        PageSnapshot full = session.snapshot(SnapshotOptions.urlAndTitle().withHtml().withText());
        Assertions.assertTrue(full.getHtml().get().contains("serp4j-tck"));
        Assertions.assertTrue(full.getText().get().contains("serp4j-tck"));
        Assertions.assertFalse(full.getText().get().contains("<h1"));

        PageSnapshot cut = session.snapshot(SnapshotOptions.urlAndTitle().withText().maxChars(5));
        Assertions.assertEquals(5, cut.getText().get().length());
        Assertions.assertTrue(cut.isTextTruncated());
    }

    // ---------------------------------------------------------------------------------------------- lifecycle

    @Test
    void session_close_is_idempotent_and_later_calls_fail_typed() {
        final BrowserSession session = open();
        go(session, TckPages.HOME);
        session.close();
        session.close();
        Assertions.assertEquals(BrowserSessionState.CLOSED, session.getState());
        BrowserSessionClosedException failure = expect(BrowserSessionClosedException.class, new Executable() {
            @Override
            public void execute() {
                go(session, TckPages.NEXT);
            }
        });
        Assertions.assertEquals(BrowserSessionState.CLOSED, failure.getState());
    }

    @Test
    void runtime_close_closes_sessions_rejects_new_ones_and_leaves_no_processes() throws Exception {
        Object before = processSnapshot();
        final BrowserRuntime own = extraRuntime();
        final BrowserSession session = own.openSession(options().build());
        go(session, TckPages.HOME);
        Object running = processSnapshot();
        own.close();
        own.close();
        Assertions.assertTrue(session.getState().isTerminal(), "session state after runtime close");
        Assertions.assertThrows(BrowserSessionClosedException.class, new Executable() {
            @Override
            public void execute() {
                go(session, TckPages.NEXT);
            }
        });
        BrowserUnavailableException failure = expect(BrowserUnavailableException.class, new Executable() {
            @Override
            public void execute() {
                own.openSession(options().build());
            }
        });
        Assertions.assertEquals(BrowserReadiness.Status.CLOSED, failure.getReadiness().getStatus());
        Assertions.assertEquals(BrowserReadiness.Status.CLOSED, own.probe().getStatus());
        assertNoOrphanedProcesses(before, running);
    }

    @Test
    void close_timeout_hard_kills_a_hanging_browser_and_leaves_no_processes() throws Exception {
        Object before = processSnapshot();
        final BrowserRuntime own = extraRuntime();
        BrowserSession session = own.openSession(options().build());
        go(session, TckPages.HOME);
        Object running = processSnapshot();
        Assumptions.assumeTrue(freezeBrowser(own, before), "implementation cannot simulate a hanging browser");
        long start = System.nanoTime();
        own.close();
        Duration took = Duration.ofNanos(System.nanoTime() - start);
        Assertions.assertTrue(took.compareTo(hungCloseBound()) <= 0, "close took " + took);
        Assertions.assertTrue(session.getState().isTerminal(), "session state " + session.getState());
        assertNoOrphanedProcesses(before, running);
    }

    @Test
    void crashed_browser_fails_sessions_as_lost_and_new_sessions_work() throws Exception {
        Object before = processSnapshot();
        final BrowserRuntime own = extraRuntime();
        final BrowserSession session = own.openSession(options().build());
        go(session, TckPages.HOME);
        Assumptions.assumeTrue(crashBrowser(own, before), "implementation cannot simulate a browser crash");
        expect(BrowserSessionLostException.class, new Executable() {
            @Override
            public void execute() {
                for (int i = 0; i < 50; i++) {
                    go(session, TckPages.NEXT);
                    sleep(100);
                }
            }
        });
        Assertions.assertEquals(BrowserSessionState.FAILED, session.getState());
        BrowserSession fresh = own.openSession(options().build());
        try {
            go(fresh, TckPages.NEXT);
            Assertions.assertEquals(TckPages.NEXT_TITLE, title(fresh));
        } finally {
            fresh.close();
        }
    }

    // ---------------------------------------------------------------------------------------------- isolation

    @Test
    void sessions_do_not_share_cookies() {
        BrowserSession first = open();
        BrowserSession second = open();
        go(first, TckPages.COOKIE_SET);
        go(first, TckPages.COOKIE_SHOW);
        go(second, TckPages.COOKIE_SHOW);
        Assertions.assertEquals(TckPages.COOKIE_TITLE_PREFIX + "tck=1", title(first));
        Assertions.assertEquals(TckPages.COOKIE_TITLE_PREFIX + "none", title(second));
    }

    // ---------------------------------------------------------------------------------------- profile mode

    @Test
    void ephemeral_profile_is_the_default() {
        Assertions.assertEquals(ProfileMode.ephemeral(), options().build().getProfileMode());
        BrowserSession first = open();
        go(first, TckPages.COOKIE_SET);
        first.close();
        BrowserSession second = open();
        go(second, TckPages.COOKIE_SHOW);
        Assertions.assertEquals(TckPages.COOKIE_TITLE_PREFIX + "none", title(second));
    }

    @Test
    void persistent_profile_keeps_cookies_and_is_leased_exclusively() {
        final String key = "tck-" + Long.toHexString(System.nanoTime());
        final BrowserSessionOptions persistent = options().profileMode(ProfileMode.persistentExclusive(key))
                .acquireTimeout(Duration.ZERO).build();
        if (!runtime.getInfo().supports(BrowserCapability.PERSISTENT_PROFILE)) {
            UnsupportedBrowserCapabilityException missing = expect(UnsupportedBrowserCapabilityException.class,
                    new Executable() {
                        @Override
                        public void execute() {
                            open(persistent);
                        }
                    });
            Assertions.assertTrue(missing.getMissing().contains(BrowserCapability.PERSISTENT_PROFILE));
            return;
        }
        BrowserSession first = open(persistent);
        go(first, TckPages.COOKIE_SET);
        BrowserProfileInUseException inUse = expect(BrowserProfileInUseException.class, new Executable() {
            @Override
            public void execute() {
                open(persistent);
            }
        });
        Assertions.assertEquals(key, inUse.getProfileKey());
        BrowserSession other = open(options().profileMode(ProfileMode.persistentExclusive(key + "-other")).build());
        go(other, TckPages.COOKIE_SHOW);
        Assertions.assertEquals(TckPages.COOKIE_TITLE_PREFIX + "none", title(other), "profiles are isolated");
        other.close();
        first.close();

        BrowserSession again = open(persistent);
        go(again, TckPages.COOKIE_SHOW);
        Assertions.assertEquals(TckPages.COOKIE_TITLE_PREFIX + "tck=1", title(again), "the lease was released and "
                + "the profile kept its cookie");
        BrowserSession ephemeral = open();
        go(ephemeral, TckPages.COOKIE_SHOW);
        Assertions.assertEquals(TckPages.COOKIE_TITLE_PREFIX + "none", title(ephemeral));
    }

    // -------------------------------------------------------------------------------------- obstacle policy

    @Test
    void fail_fast_reports_obstacles_typed_and_the_session_stays_usable() {
        final BrowserSession session = open();
        go(session, TckPages.HOME);
        Assertions.assertEquals(ObstaclePolicy.failFast(), options().build().getObstaclePolicy());
        PageObstacleException failure = expect(PageObstacleException.class, new Executable() {
            @Override
            public void execute() {
                session.resolveObstacle(PageObstacle.CONSENT, WaitCondition.present(ElementSelector.css("h1")));
            }
        });
        Assertions.assertEquals(PageObstacle.CONSENT, failure.getObstacle());
        Assertions.assertFalse(failure.isWaitedForHuman());
        go(session, TckPages.NEXT);
        Assertions.assertEquals(TckPages.NEXT_TITLE, title(session));
    }

    @Test
    void wait_for_human_is_only_allowed_in_headed_mode() {
        final BrowserSessionOptions waiting = options().obstaclePolicy(ObstaclePolicy.waitForHuman(SHORT)).build();
        Assertions.assertTrue(waiting.getRequiredCapabilities().contains(BrowserCapability.HEADED_MODE));
        if (!runtime.getInfo().supports(BrowserCapability.HEADED_MODE)) {
            UnsupportedBrowserCapabilityException missing = expect(UnsupportedBrowserCapabilityException.class,
                    new Executable() {
                        @Override
                        public void execute() {
                            open(waiting);
                        }
                    });
            Assertions.assertTrue(missing.getMissing().contains(BrowserCapability.HEADED_MODE));
            Assertions.assertTrue(runtime.getInfo().isHeadless());
            return;
        }
        final BrowserSession session = open(waiting);
        go(session, TckPages.HOME);
        session.resolveObstacle(PageObstacle.CHALLENGE, WaitCondition.urlMatches("/$"));
        PageObstacleException failure = expect(PageObstacleException.class, new Executable() {
            @Override
            public void execute() {
                session.resolveObstacle(PageObstacle.CHALLENGE,
                        WaitCondition.present(ElementSelector.css("#never-there")));
            }
        });
        Assertions.assertTrue(failure.isWaitedForHuman());
        Assertions.assertFalse(session.getState().isTerminal());
    }

    @Test
    void calls_from_many_threads_are_serialized() throws Exception {
        final BrowserSession session = open();
        go(session, TckPages.HOME);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Long>> results = new ArrayList<Future<Long>>();
            for (int i = 0; i < 12; i++) {
                results.add(pool.submit(new Callable<Long>() {
                    @Override
                    public Long call() {
                        return session.evaluate(PageScript.of(TckPages.STRUCTURE_SCRIPT_ID, TckPages.STRUCTURE_SCRIPT))
                                .path("count").get().asLong();
                    }
                }));
            }
            for (Future<Long> result : results) {
                Assertions.assertEquals(Long.valueOf(3L), result.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------------------------- capacity, budgets

    @Test
    void capacity_limit_is_hard_and_slots_are_released_on_close() {
        int limit = runtime.getInfo().getMaxConcurrentSessions();
        List<BrowserSession> all = new ArrayList<BrowserSession>();
        for (int i = 0; i < limit; i++) {
            all.add(open());
        }
        final BrowserSessionOptions quick = options().acquireTimeout(Duration.ofMillis(200)).build();
        BrowserCapacityException failure = expect(BrowserCapacityException.class, new Executable() {
            @Override
            public void execute() {
                track(runtime.openSession(quick));
            }
        });
        Assertions.assertEquals(limit, failure.getMaxConcurrentSessions());
        all.get(0).close();
        BrowserSession again = open(quick);
        Assertions.assertEquals(BrowserSessionState.OPEN, again.getState());
    }

    @Test
    void missing_required_capability_fails_fast() {
        Set<BrowserCapability> unsupported = EnumSet.allOf(BrowserCapability.class);
        unsupported.removeAll(runtime.getInfo().getCapabilities());
        Assumptions.assumeFalse(unsupported.isEmpty(), "runtime supports every capability");
        final BrowserCapability missing = unsupported.iterator().next();
        UnsupportedBrowserCapabilityException failure = expect(UnsupportedBrowserCapabilityException.class,
                new Executable() {
                    @Override
                    public void execute() {
                        track(runtime.openSession(options().require(missing).build()));
                    }
                });
        Assertions.assertTrue(failure.getMissing().contains(missing));
    }

    @Test
    void navigation_budget_expires_the_session() {
        final BrowserSession session = open(options().maxNavigations(2).build());
        go(session, TckPages.HOME);
        go(session, TckPages.NEXT);
        BrowserSessionClosedException failure = expect(BrowserSessionClosedException.class, new Executable() {
            @Override
            public void execute() {
                go(session, TckPages.HOME);
            }
        });
        Assertions.assertEquals(BrowserSessionState.EXPIRED, failure.getState());
        Assertions.assertEquals(BrowserSessionState.EXPIRED, session.getState());
    }

    @Test
    void idle_timeout_expires_the_session() {
        final BrowserSession session = open(options().idleTimeout(Duration.ofMillis(300)).build());
        go(session, TckPages.HOME);
        sleep(800);
        BrowserSessionClosedException failure = expect(BrowserSessionClosedException.class, new Executable() {
            @Override
            public void execute() {
                go(session, TckPages.NEXT);
            }
        });
        Assertions.assertEquals(BrowserSessionState.EXPIRED, failure.getState());
    }

    // --------------------------------------------------------------------------------------------- url policy

    @Test
    void url_policy_is_checked_before_navigation_and_after_redirects() {
        final BrowserSession session = open(options().urlPolicy(UrlPolicy.allowHosts(site().primaryHostPattern())).build());
        go(session, TckPages.HOME);

        expect(UrlPolicyViolationException.class, new Executable() {
            @Override
            public void execute() {
                session.navigate(NavigationRequest.to(site().foreignUrl(TckPages.NEXT)));
            }
        });
        UrlPolicyViolationException redirect = expect(UrlPolicyViolationException.class, new Executable() {
            @Override
            public void execute() {
                go(session, TckPages.REDIRECT_FOREIGN);
            }
        });
        Assertions.assertTrue(redirect.getUrl().startsWith(site().foreignUrl("")), redirect.getUrl());
        Assertions.assertEquals("about:blank", session.snapshot(SnapshotOptions.urlAndTitle()).getUrl());
        Assertions.assertFalse(session.getState().isTerminal());

        go(session, TckPages.NEXT);
        Assertions.assertEquals(TckPages.NEXT_TITLE, title(session));
    }

    @Test
    void non_web_schemes_are_never_navigated() {
        final BrowserSession session = open();
        expect(UrlPolicyViolationException.class, new Executable() {
            @Override
            public void execute() {
                session.navigate(NavigationRequest.to("file:///etc/passwd"));
            }
        });
        expect(UrlPolicyViolationException.class, new Executable() {
            @Override
            public void execute() {
                session.navigate(NavigationRequest.to("data:text/html,<h1>x</h1>"));
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ network

    @Test
    void network_observation_delivers_events_in_idle_state_without_secret_headers() {
        final BrowserSession session = open();
        if (!runtime.getInfo().supports(BrowserCapability.NETWORK_OBSERVATION)) {
            expect(UnsupportedBrowserCapabilityException.class, new Executable() {
                @Override
                public void execute() {
                    session.network();
                }
            });
            return;
        }
        NetworkCapture capture = session.network().start(NetworkFilter.builder()
                .urlMatching("/api/late\\.json").captureBody(true).maxBodyBytes(64).build());
        try {
            go(session, TckPages.LATE);
            // No command runs on the session while the page fetches: the event must still be delivered.
            sleep(TckPages.LATE_DELAY.toMillis() + 1500);
            List<ObservedExchange> exchanges = capture.getExchanges();
            Assertions.assertEquals(1, exchanges.size(), "exchanges recorded while idle: " + exchanges);
            ObservedExchange exchange = exchanges.get(0);
            Assertions.assertEquals("GET", exchange.getMethod());
            Assertions.assertEquals(200, exchange.getStatus());
            Assertions.assertTrue(exchange.getUrl().endsWith(TckPages.LATE_API), exchange.getUrl());
            Assertions.assertTrue(exchange.getContentType().get().startsWith("application/json"));
            Assertions.assertFalse(exchange.getResponseHeaders().containsKey("set-cookie"), "Set-Cookie is never exposed");
            Assertions.assertTrue(exchange.isBodyTruncated());
            Assertions.assertEquals(64, exchange.getBody().get().length);
            Assertions.assertTrue(exchange.getBodyAsString(UTF8).get().startsWith("{\"late\":true"));

            Optional<ObservedExchange> first = capture.awaitFirst(NetworkFilter.any(), Duration.ofSeconds(1));
            Assertions.assertTrue(first.isPresent());
            Assertions.assertEquals(WaitOutcome.SATISFIED,
                    capture.awaitQuiet(Duration.ofMillis(200), Duration.ofSeconds(5)));
        } finally {
            capture.close();
        }
        Assertions.assertTrue(capture.getExchanges().isEmpty(), "a closed capture discards its exchanges");
    }

    @Test
    void network_capture_does_not_see_exchanges_before_its_start_and_await_first_times_out() {
        BrowserSession session = open();
        Assumptions.assumeTrue(runtime.getInfo().supports(BrowserCapability.NETWORK_OBSERVATION),
                "runtime does not declare NETWORK_OBSERVATION");
        go(session, TckPages.HOME);
        NetworkCapture capture = session.network().start(NetworkFilter.builder().urlMatching("/api/late\\.json").build());
        try {
            Assertions.assertFalse(capture.awaitFirst(NetworkFilter.any(), SHORT).isPresent());
            Assertions.assertTrue(capture.getExchanges().isEmpty());
        } finally {
            capture.close();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
