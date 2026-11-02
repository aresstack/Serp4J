/**
 * Google web search through the optional browser port {@code serp4j-browser-api} (ADR-018): the classic result page
 * ({@code /search?q=…}, never {@code udm=50}) is loaded in a browser session and parsed with the same parser as the
 * HTTP path. Experimental and not published in 0.x (ADR-016).
 */
package com.aresstack.serp4j.engine.google.browser;
