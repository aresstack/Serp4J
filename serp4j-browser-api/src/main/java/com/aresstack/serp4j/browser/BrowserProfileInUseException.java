package com.aresstack.serp4j.browser;

/**
 * The persistent profile of {@link ProfileMode#persistentExclusive(String)} is leased by another session (in this
 * or another process). No session was opened.
 */
public final class BrowserProfileInUseException extends BrowserException {

    private static final long serialVersionUID = 1L;

    private final String profileKey;

    /**
     * @param profileKey the leased profile
     * @param message    message
     */
    public BrowserProfileInUseException(String profileKey, String message) {
        super(message);
        this.profileKey = Args.notBlank(profileKey, "profileKey");
    }

    /**
     * @return the leased profile
     */
    public String getProfileKey() {
        return profileKey;
    }
}
