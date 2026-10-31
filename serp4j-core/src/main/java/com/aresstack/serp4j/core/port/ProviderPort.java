package com.aresstack.serp4j.core.port;

/**
 * Common base of all provider ports: identity, capabilities and lifecycle. Kept deliberately small so that
 * {@link SearchProvider} and {@link com.aresstack.serp4j.core.port.conversation.ConversationalSearchProvider} stay
 * independent (WP2 §4).
 *
 * <h2>Lifecycle and ownership (ADR-017, architecture.md §6.1)</h2>
 * <ol>
 * <li><strong>Whoever creates, closes.</strong> Injected infrastructure – an HTTP transport, a browser runtime, a
 * {@link com.aresstack.serp4j.core.port.credential.CredentialProvider}, a scheduler or executor – belongs to the
 * caller that passed it in. A provider never closes it.</li>
 * <li><strong>Providers close only what they created themselves:</strong> open sessions, browser sessions they
 * opened, running polling tasks (cancel the task, never shut the scheduler down) and running futures, which
 * complete with {@link com.aresstack.serp4j.core.error.SearchErrorCategory#CANCELLED}.</li>
 * <li><strong>Providers do not affect each other.</strong> Closing one provider leaves shared infrastructure, and
 * therefore every other provider on it, fully usable.</li>
 * <li><strong>Infrastructure a provider created itself</strong> (for example a transport it instantiated because
 * none was injected) belongs to the provider and is closed by it. Builders make this visible: passing an instance
 * means borrowed, passing none means own.</li>
 * <li>Services obtained from {@link com.aresstack.serp4j.core.port.factory.ProviderEnvironment#getService(Class)}
 * belong to whoever built the environment.</li>
 * </ol>
 */
public interface ProviderPort extends AutoCloseable {

    /** Immutable self-description; must not trigger I/O. */
    ProviderDescriptor getDescriptor();

    /**
     * Releases everything this provider owns (see the ownership rules above). Idempotent; a second call is a no-op.
     * Calls on a closed provider fail with {@link com.aresstack.serp4j.core.error.SearchErrorCategory#CANCELLED}
     * or {@link com.aresstack.serp4j.core.error.SearchErrorCategory#SESSION_CLOSED}.
     */
    @Override
    void close();
}
