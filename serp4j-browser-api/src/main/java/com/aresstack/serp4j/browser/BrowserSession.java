package com.aresstack.serp4j.browser;

import java.time.Duration;

/**
 * An isolated browser session (cookies, storage, history) with exactly one active page (WP3 §5.1).
 *
 * <p><strong>Threading.</strong> Thread-safe in the sense of serialized: calls from any thread are executed one
 * after another on the thread that owns the session. All methods block until they have a result or their timeout
 * expires. Without an explicit timeout, {@link BrowserSessionOptions#getDefaultTimeout()} applies.</p>
 *
 * <p><strong>States.</strong> See {@link BrowserSessionState}. Once the state is terminal, every operation except
 * {@link #getId()}, {@link #getState()} and {@link #close()} fails: with {@link BrowserSessionLostException} if the
 * browser crashed or disconnected ({@link BrowserSessionState#FAILED}), otherwise with
 * {@link BrowserSessionClosedException}.</p>
 *
 * <p><strong>URL policy.</strong> Every navigation is checked against {@link BrowserSessionOptions#getUrlPolicy()}
 * before it starts and again on the final URL after redirects; a violation leaves the page on
 * {@code about:blank} and fails with {@link UrlPolicyViolationException}. Page-initiated navigations (click,
 * key press) are checked the same way.</p>
 */
public interface BrowserSession extends AutoCloseable {

    /**
     * @return the session id, unique within the JVM
     */
    BrowserSessionId getId();

    /**
     * @return the current state; never blocks
     */
    BrowserSessionState getState();

    /**
     * Navigates the page. HTTP error statuses are results, not exceptions.
     *
     * @param request target and wait strategy
     * @return final URL, status and generic obstacle signal
     * @throws BrowserTimeoutException if the navigation did not reach its wait state in time; the session stays
     *                                 usable
     * @throws UrlPolicyViolationException if the target or the final URL is not allowed
     */
    NavigationResult navigate(NavigationRequest request);

    /**
     * Waits until a condition holds on the current page.
     *
     * @param condition condition to wait for
     * @param timeout   maximum wait
     * @return {@link WaitOutcome#SATISFIED} or {@link WaitOutcome#TIMED_OUT}; a timeout is not an exception here
     */
    WaitOutcome await(WaitCondition condition, Duration timeout);

    /**
     * Runs a page script and returns its result as a neutral value tree.
     *
     * @param script the script, a JavaScript function expression
     * @return the result; {@code undefined} and {@code null} become {@link BrowserValue#nullValue()}
     * @throws ScriptEvaluationException if the script throws or its result cannot be converted
     */
    BrowserValue evaluate(PageScript script);

    /**
     * Clicks the first element matching the selector.
     *
     * @param selector element
     * @param options  timeout for finding the element
     * @throws BrowserTimeoutException if no matching, actionable element appeared in time
     */
    void click(ElementSelector selector, InteractionOptions options);

    /**
     * Enters text into the first element matching the selector. Replaces the current value unless
     * {@link InteractionOptions#getTypingDelay()} is set, in which case the text is typed key by key.
     *
     * @param selector input element
     * @param text     text to enter
     * @param options  timeout and typing delay
     * @throws BrowserTimeoutException if no matching, editable element appeared in time
     */
    void type(ElementSelector selector, String text, InteractionOptions options);

    /**
     * Presses a key on the first element matching the selector.
     *
     * @param selector element that receives the key
     * @param key      key name such as {@code Enter}, {@code Escape}, {@code Tab}, {@code ArrowDown} or a single
     *                 character
     * @param options  timeout for finding the element
     * @throws BrowserTimeoutException if no matching element appeared in time
     */
    void press(ElementSelector selector, String key, InteractionOptions options);

    /**
     * Captures the rendered state of the page.
     *
     * @param options what to capture and how much
     * @return URL, title and optionally HTML and visible text
     */
    PageSnapshot snapshot(SnapshotOptions options);

    /**
     * Optional capability: network observation of this session.
     *
     * @return the observer of this session
     * @throws UnsupportedBrowserCapabilityException if the runtime does not declare
     *                                               {@link BrowserCapability#NETWORK_OBSERVATION}
     */
    NetworkObserver network();

    /**
     * Closes page and context (with {@link IsolationLevel#PROCESS} also the browser process) and discards captured
     * network data. Idempotent; never throws.
     */
    @Override
    void close();
}
