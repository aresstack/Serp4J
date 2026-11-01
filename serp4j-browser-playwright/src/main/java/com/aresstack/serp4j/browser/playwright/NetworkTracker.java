package com.aresstack.serp4j.browser.playwright;

import com.aresstack.serp4j.browser.NetworkFilter;
import com.aresstack.serp4j.browser.ObservedExchange;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.Sizes;

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
 *
 * <p>The listeners are installed when the session's context is created, and the requests in flight are tracked for
 * the whole session, so that a capture started while a matching request is already running counts it as in flight.
 * A request whose response has a content type the capture does not want stops counting as soon as the response
 * headers arrive and never counts as activity.
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

        /** Whether the capture filters by content type, which is only known once the response headers are in. */
        boolean filtersContentType() {
            return !filter.getContentTypes().isEmpty();
        }
    }

    /** Marker for a request whose response headers have not arrived yet. */
    private static final String PENDING = new String("pending");

    private final List<Capture> captures = new ArrayList<Capture>();
    private final Deque<Request> finished = new ArrayDeque<Request>();
    /** Requests of the session in flight, with the content type of their response once known. */
    private final Map<Request, String> active = new IdentityHashMap<Request, String>();
    private boolean listening;

    /** Owner thread: installs the listeners; called right after the context is created, before any request. */
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
        context.onResponse(new Consumer<Response>() {
            @Override
            public void accept(Response response) {
                responded(response);
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
        active.put(request, PENDING);
        long now = System.nanoTime();
        for (Capture capture : captures) {
            if (capture.matchesRequest(request)) {
                capture.inflight.put(request, Boolean.TRUE);
                if (!capture.filtersContentType()) {
                    capture.lastActivity = now;
                }
            }
        }
    }

    /** Response headers are part of the event (no driver call): drop requests of an unwanted content type. */
    private void responded(Response response) {
        Request request = response.request();
        if (!active.containsKey(request)) {
            return;
        }
        String contentType = contentType(response);
        active.put(request, contentType == null ? "" : contentType);
        long now = System.nanoTime();
        for (Capture capture : captures) {
            if (!capture.inflight.containsKey(request) || !capture.filtersContentType()) {
                continue;
            }
            if (capture.filter.matchesContentType(contentType)) {
                capture.lastActivity = now;
            } else {
                capture.inflight.remove(request);
            }
        }
    }

    private static String contentType(Response response) {
        try {
            Map<String, String> headers = response.headers();
            return headers == null ? null : headers.get("content-type");
        } catch (PlaywrightException e) {
            return null;
        }
    }

    private void ended(Request request, boolean success) {
        active.remove(request);
        long now = System.nanoTime();
        boolean wanted = false;
        for (Capture capture : captures) {
            // every active request matching a capture is in its in-flight set; one that left it early had an
            // unwanted content type
            boolean counted = capture.inflight.remove(request) != null;
            if (counted || (!capture.filtersContentType() && capture.matchesRequest(request))) {
                capture.lastActivity = now;
                wanted = true;
            }
        }
        if (success && wanted) {
            finished.add(request);
        }
    }

    /** Owner thread: starts a capture that counts the matching requests already in flight. */
    Capture start(NetworkFilter filter) {
        drain();
        Capture capture = new Capture(filter);
        for (Map.Entry<Request, String> request : active.entrySet()) {
            if (!capture.matchesRequest(request.getKey())) {
                continue;
            }
            String contentType = request.getValue();
            if (contentType != PENDING && capture.filtersContentType()
                    && !filter.matchesContentType(contentType.isEmpty() ? null : contentType)) {
                continue;
            }
            capture.inflight.put(request.getKey(), Boolean.TRUE);
        }
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
            Long transferred = null;
            byte[] body = null;
            boolean bodyLoaded = false;
            for (Capture capture : targets) {
                if (!capture.filter.matchesContentType(contentType)) {
                    continue;
                }
                ObservedExchange.Builder builder = ObservedExchange.builder(request.method(), request.url(),
                        response.status(), Instant.now()).resourceType(request.resourceType()).responseHeaders(headers);
                if (capture.filter.isCaptureBody()) {
                    if (transferred == null) {
                        transferred = transferredBodySize(request, headers);
                    }
                    int max = capture.filter.getMaxBodyBytes();
                    if (tooLargeToLoad(transferred, max)) {
                        // driver and JVM would hold the whole body only to cut it; leave it out
                        builder.bodyOmitted();
                    } else {
                        if (!bodyLoaded) {
                            bodyLoaded = true;
                            try {
                                body = response.body();
                            } catch (PlaywrightException e) {
                                body = null;
                            }
                        }
                        builder.body(body, max);
                    }
                }
                capture.exchanges.add(builder.build());
            }
        }
    }

    /**
     * Size of the response body as transferred (after content encoding), from the request's size information or the
     * {@code content-length} header; {@code -1} when unknown. The decoded body is at least as large, so a transferred
     * size above the limit means the body exceeds it.
     */
    static long transferredBodySize(Request request, Map<String, String> headers) {
        try {
            Sizes sizes = request.sizes();
            if (sizes != null && sizes.responseBodySize > 0) {
                return sizes.responseBodySize;
            }
        } catch (PlaywrightException | UnsupportedOperationException e) {
            // fall back to the header
        }
        return declaredLength(headers.get("content-length"));
    }

    /** Bodies up to this size are loaded and cut to the limit, so that a capture still gets the body's beginning. */
    static final long LOAD_CEILING_BYTES = 4L * 1024 * 1024;

    /**
     * Whether a body is too large to load just to cut it: its transferred size exceeds both four times the limit and
     * {@link #LOAD_CEILING_BYTES}. Smaller bodies over the limit are loaded and truncated; unknown sizes are loaded.
     */
    static boolean tooLargeToLoad(long transferred, int maxBodyBytes) {
        return transferred > Math.max(4L * maxBodyBytes, LOAD_CEILING_BYTES);
    }

    static long declaredLength(String header) {
        if (header == null) {
            return -1L;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return -1L;
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
        active.clear();
    }

    boolean hasCaptures() {
        return !captures.isEmpty();
    }
}
