package com.aresstack.serp4j.core.port.conversation;

import com.aresstack.serp4j.core.annotation.Experimental;
import com.aresstack.serp4j.core.internal.Preconditions;
import com.aresstack.serp4j.core.model.id.ProviderId;
import com.aresstack.serp4j.core.model.id.SearchSessionId;

import java.util.Objects;

/**
 * Opaque token to resume a session later (WP2 §11.3). Its content is the adapter's business (for example a URL
 * plus a cookie reference) and must not contain plaintext secrets. The three parts are plain strings so a host can
 * persist them; the class itself is not {@code Serializable} (ADR-004).
 */
@Experimental("conversational search stabilises with the first Google AI Mode adapter (Paket G)")
public final class SessionHandle {

    private final ProviderId providerId;
    private final SearchSessionId sessionId;
    private final String opaqueToken;

    private SessionHandle(ProviderId providerId, SearchSessionId sessionId, String opaqueToken) {
        this.providerId = providerId;
        this.sessionId = sessionId;
        this.opaqueToken = opaqueToken;
    }

    public static SessionHandle of(ProviderId providerId, SearchSessionId sessionId, String opaqueToken) {
        return new SessionHandle(Preconditions.requireNonNull(providerId, "providerId"),
                Preconditions.requireNonNull(sessionId, "sessionId"),
                Preconditions.requireText(opaqueToken, "opaqueToken"));
    }

    public ProviderId getProviderId() {
        return providerId;
    }

    public SearchSessionId getSessionId() {
        return sessionId;
    }

    public String getOpaqueToken() {
        return opaqueToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SessionHandle)) {
            return false;
        }
        SessionHandle that = (SessionHandle) o;
        return providerId.equals(that.providerId)
                && sessionId.equals(that.sessionId)
                && opaqueToken.equals(that.opaqueToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, sessionId, opaqueToken);
    }

    /** Does not reveal the token. */
    @Override
    public String toString() {
        return "SessionHandle{provider=" + providerId + ", session=" + sessionId + ", token=****}";
    }
}
