package com.aresstack.serp4j.core.port.conversation;

import com.aresstack.serp4j.core.annotation.Experimental;
import com.aresstack.serp4j.core.port.ProviderPort;
import com.aresstack.serp4j.core.port.SearchContext;

import java.util.concurrent.CompletableFuture;

/**
 * Provider of conversational searches (ADR-005, ADR-009, WP2 §11): opens {@link SearchSession}s against an AI
 * search surface such as Google AI Mode. Independent of {@link com.aresstack.serp4j.core.port.SearchProvider}: a
 * provider may implement one or both. {@code getDescriptor().getCapabilities().getConversational()} must be present.
 *
 * <p>Ownership (ADR-017, see {@link ProviderPort}): sessions opened here belong to the caller, who closes them;
 * closing the provider closes all of its still-open sessions but never the injected browser runtime or transport.</p>
 */
@Experimental("conversational search stabilises with the first Google AI Mode adapter (Paket G)")
public interface ConversationalSearchProvider extends ProviderPort {

    /**
     * Opens a new, empty session. May reserve resources (for example a browser context) but sends no question yet.
     * Fails with {@code RATE_LIMITED}, {@code UNAVAILABLE} or {@code BLOCKED} when the concurrent-session limit is
     * reached or the resource is not available.
     */
    CompletableFuture<SearchSession> openSession(SearchSessionOptions options, SearchContext context);

    /**
     * Resumes a session opened earlier. Supported only when the conversational capabilities are
     * {@link com.aresstack.serp4j.core.port.capability.ConversationalCapabilities#isResumable() resumable};
     * otherwise the future fails with {@code UNSUPPORTED}.
     */
    CompletableFuture<SearchSession> resumeSession(SessionHandle handle, SearchContext context);
}
