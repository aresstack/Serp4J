package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.BrowserException;
import com.aresstack.serp4j.browser.BrowserReadiness;
import com.aresstack.serp4j.browser.BrowserSessionLostException;
import com.aresstack.serp4j.browser.BrowserTimeoutException;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

import java.util.Locale;

/** Maps Playwright failures to the typed exceptions of the browser port. */
final class Errors {

    private static final int MAX_MESSAGE = 300;

    private Errors() {
    }

    static boolean isTimeout(Throwable e) {
        return e instanceof TimeoutError;
    }

    /** Whether the failure means that page, context, browser or driver connection are gone. */
    static boolean isGone(Throwable e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("target page, context or browser has been closed")
                || message.contains("target closed") || message.contains("browser has been closed")
                || message.contains("browser has disconnected") || message.contains("connection closed")
                || message.contains("playwright connection closed") || message.contains("page crashed")
                || message.contains("context closed") || message.contains("page closed")
                // the GraalJS driver (playwright4j) reports a dead browser pipe as an I/O error
                || message.contains("output closed") || message.contains("pipe closed")
                || message.contains("broken pipe") || message.contains("stream closed");
    }

    static BrowserException map(PlaywrightException e, String action) {
        if (isTimeout(e)) {
            return new BrowserTimeoutException(action + " timed out: " + firstLine(e), e);
        }
        if (isGone(e)) {
            return new BrowserSessionLostException(action + " failed, browser connection lost: " + firstLine(e), e);
        }
        return new BrowserException(action + " failed: " + firstLine(e), e);
    }

    static BrowserReadiness startFailure(Throwable e) {
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("executable doesn't exist") || lower.contains("executable does not exist")
                || lower.contains("is not found at") || lower.contains("looks like playwright was just installed")
                || lower.contains("please run the following command to download new browsers")) {
            return BrowserReadiness.of(BrowserReadiness.Status.BROWSER_NOT_INSTALLED,
                    "browser executable not found; Serp4J never downloads browsers, install it or set "
                            + "executablePath/channel: " + firstLine(e));
        }
        if (lower.contains("driver") && !lower.contains("launch")) {
            return BrowserReadiness.of(BrowserReadiness.Status.INCOMPATIBLE_DRIVER, "driver failed: " + firstLine(e));
        }
        return BrowserReadiness.of(BrowserReadiness.Status.BROWSER_START_FAILED, firstLine(e));
    }

    static String firstLine(Throwable e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        int newline = message.indexOf('\n');
        String line = (newline < 0 ? message : message.substring(0, newline)).trim();
        return line.length() > MAX_MESSAGE ? line.substring(0, MAX_MESSAGE) + "…" : line;
    }
}
