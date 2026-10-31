package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.NetworkFilter;
import com.aresstack.serp4j.browser.ObservedExchange;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Network observation of one session. Owner-thread only: the Playwright listeners run on the owner thread during
 * a Playwright call or the idle pump and only record the request; bodies are fetched later by {@link #drain()},
 * outside of event callbacks, so that a callback never issues a Playwright call itself.
 */
final class NetworkTracker {

    /** One capture of the session. */
    static final class Capture {
        final NetworkFilter filter;
        final List<ObservedExchange> exchanges = new ArrayList<ObservedExchange>();
        final Map<Request, Boolean> inflight = new IdentityHashMap<Request, Boolean>();
        long lastActivity = System.nanoTime();
        boolean closed;

        Capture(NetworkFilter filter) {
            this.filter = filter;
        }

        boolean matchesRequest(Request request) {
            return filter.matchesRequest(request.method(), request.url(), request.resourceType());
        }
    }

    private final List<Capture> captures = new ArrayList<Capture>();
    private final Deque<Request> finished = new ArrayDeque<Request>();
    private boolean listening;

    void ensureListening(BrowserContext context) {
        if (listening) {
            return;
        }
        listening = true;
        context.onRequest(new Consumer<Request>() {
            @Override
            public void accept(Request request) {
                started(request);
            }
        });
        context.onRequestFinished(new Consumer<Request>() {
            @Override
            public void accept(Request request) {
                ended(request, true);
            }
        });
        context.onRequestFailed(new Consumer<Request>() {
            @Override
            public void accept(Request request) {
                ended(request, false);
            }
        });
    }

    private void started(Request request) {
        if (captures.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        for (Capture capture : captures) {
            if (capture.matchesRequest(request)) {
                capture.inflight.put(request, Boolean.TRUE);
                capture.lastActivity = now;
            }
        }
    }

    private void ended(Request request, boolean success) {
        if (captures.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        boolean wanted = false;
        for (Capture capture : captures) {
            if (capture.inflight.remove(request) != null || capture.matchesRequest(request)) {
                capture.lastActivity = now;
                wanted = true;
            }
        }
        if (success && wanted) {
            finished.add(request);
        }
    }

    Capture start(NetworkFilter filter) {
        drain();
        Capture capture = new Capture(filter);
        captures.add(capture);
        return capture;
    }

    void stop(Capture capture) {
        capture.closed = true;
        capture.exchanges.clear();
        capture.inflight.clear();
        captures.remove(capture);
    }

    /** Fetches status, headers and bodies of finished requests. Must not run inside an event callback. */
    void drain() {
        Request request;
        while ((request = finished.poll()) != null) {
            List<Capture> targets = new ArrayList<Capture>();
            for (Capture capture : captures) {
                if (capture.matchesRequest(request)) {
                    targets.add(capture);
                }
            }
            if (targets.isEmpty()) {
                continue;
            }
            Response response;
            Map<String, String> headers;
            try {
                response = request.response();
                if (response == null) {
                    continue;
                }
                headers = response.allHeaders();
            } catch (PlaywrightException e) {
                continue;
            }
            String contentType = headers.get("content-type");
            byte[] body = null;
            boolean bodyLoaded = false;
            for (Capture capture : targets) {
                if (!capture.filter.matchesContentType(contentType)) {
                    continue;
                }
                ObservedExchange.Builder builder = ObservedExchange.builder(request.method(), request.url(),
                        response.status(), Instant.now()).resourceType(request.resourceType()).responseHeaders(headers);
                if (capture.filter.isCaptureBody()) {
                    if (!bodyLoaded) {
                        bodyLoaded = true;
                        try {
                            body = response.body();
                        } catch (PlaywrightException e) {
                            body = null;
                        }
                    }
                    builder.body(body, capture.filter.getMaxBodyBytes());
                }
                capture.exchanges.add(builder.build());
            }
        }
    }

    List<ObservedExchange> exchanges(Capture capture) {
        drain();
        return capture.closed ? Collections.<ObservedExchange>emptyList()
                : Collections.unmodifiableList(new ArrayList<ObservedExchange>(capture.exchanges));
    }

    void clear() {
        for (Capture capture : new ArrayList<Capture>(captures)) {
            stop(capture);
        }
        finished.clear();
    }

    boolean hasCaptures() {
        return !captures.isEmpty();
    }
}
