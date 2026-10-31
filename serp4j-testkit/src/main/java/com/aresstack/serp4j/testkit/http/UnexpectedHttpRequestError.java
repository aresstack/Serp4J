package com.aresstack.serp4j.testkit.http;

import com.aresstack.serp4j.http.HttpRequest;

/**
 * A {@link FakeHttpTransport} received a request no stub matches. Deliberately an {@link AssertionError}: it is a
 * defect of the test or the adapter, not a transport failure an adapter should map. Also recorded, so that
 * {@link FakeHttpTransport#assertNoUnmatchedRequests()} reports it even if the adapter swallowed the failure.
 */
@SuppressWarnings("serial")
public final class UnexpectedHttpRequestError extends AssertionError {

    private final transient HttpRequest request;

    UnexpectedHttpRequestError(HttpRequest request, String stubs) {
        super("no stub matches " + request.getMethod() + " " + request.getRedactedUrl() + "; stubs: " + stubs);
        this.request = request;
    }

    /** The unmatched request. */
    public HttpRequest getRequest() {
        return request;
    }
}
