/**
 * Google web search over a direct, cookie-free {@code GET https://www.google.com/search?q=…} (ADR-018, Serp4J #6).
 * Experimental and not published in 0.x (ADR-016). Spike S0 (docs/spikes/s0-google-http.md) showed that Google
 * currently answers such requests with a consent redirect or a JavaScript-only page; the provider reports this as
 * {@code BLOCKED} so that callers can fall back to {@code google-browser} or a SERP API via a {@code SearchPlan}.
 */
package com.aresstack.serp4j.engine.google.http;
