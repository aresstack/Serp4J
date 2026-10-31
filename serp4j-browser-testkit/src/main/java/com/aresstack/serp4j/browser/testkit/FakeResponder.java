package com.aresstack.serp4j.browser.testkit;

/** Produces a {@link FakePage} for a request, for pages that depend on query or cookies. */
public interface FakeResponder {

    /**
     * @param request the request
     * @return the page to serve
     */
    FakePage respond(FakeRequest request);
}
