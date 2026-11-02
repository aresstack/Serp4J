/**
 * Google engine parts shared by the HTTP and the browser access path (ADR-018): {@link
 * com.aresstack.serp4j.engine.google.GoogleUrlBuilder}, {@link com.aresstack.serp4j.engine.google.GoogleObstacleDetector},
 * {@link com.aresstack.serp4j.engine.google.GoogleSerpHtmlParser} and {@link
 * com.aresstack.serp4j.engine.google.GoogleSerpMapper}.
 *
 * <p>Experimental and not published in 0.x (ADR-016, E-3/E-4). Never sets {@code udm=50} (AI Mode), never solves
 * challenges or accepts consent dialogs.</p>
 */
package com.aresstack.serp4j.engine.google;
