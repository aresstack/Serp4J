package com.aresstack.serp4j.core.model;

/**
 * Whether a request aspect must be honoured or may be ignored (WP2 §6.3, ADR-007).
 *
 * <p>A provider that cannot honour a {@link #REQUIRED} aspect fails with {@code SearchErrorCategory.UNSUPPORTED};
 * an {@link #OPTIONAL} aspect it cannot honour is ignored and reported as a {@code SearchWarning}.</p>
 */
public enum RequirementLevel {
    REQUIRED, OPTIONAL
}
