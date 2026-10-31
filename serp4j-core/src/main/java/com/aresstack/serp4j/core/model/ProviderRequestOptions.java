package com.aresstack.serp4j.core.model;

import com.aresstack.serp4j.core.model.id.ProviderId;

/**
 * Marker for provider-specific, per-request options (ADR-006, WP2 §5.2). Implementations live in the provider
 * modules (for example {@code BraveRequestOptions}) and are immutable. A {@link SearchRequest} holds at most one
 * options object per {@link ProviderId}.
 *
 * <p>Rule: a provider silently ignores options addressed to other providers; options addressed to it but of an
 * unexpected type fail with {@code SearchErrorCategory.INVALID_REQUEST}.</p>
 */
public interface ProviderRequestOptions {

    /** The provider instance the options are meant for. */
    ProviderId getProviderId();
}
