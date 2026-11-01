package com.aresstack.serp4j.browser;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Browser profile of a session (WP3 §5.2, §6.3 rule 6). {@link #ephemeral()} is the default: a fresh, isolated
 * profile that is discarded on close. {@link #persistentExclusive(String)} keeps cookies and storage (for example
 * consent cookies) under a profile key across sessions; the runtime leases the profile exclusively, so it is never
 * used by two sessions at the same time, and requires {@link BrowserCapability#PERSISTENT_PROFILE}. Immutable.
 */
public final class ProfileMode {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final ProfileMode EPHEMERAL = new ProfileMode(null);

    private final String profileKey;

    private ProfileMode(String profileKey) {
        this.profileKey = profileKey;
    }

    /**
     * @return a fresh profile per session (default)
     */
    public static ProfileMode ephemeral() {
        return EPHEMERAL;
    }

    /**
     * @param profileKey 1–64 characters of {@code A-Z a-z 0-9 . _ -}, starting with a letter or digit; used as
     *                   directory name, so it never contains path separators
     * @return a persistent profile that is leased exclusively for the lifetime of a session
     */
    public static ProfileMode persistentExclusive(String profileKey) {
        Args.notNull(profileKey, "profileKey");
        if (!KEY.matcher(profileKey).matches()) {
            throw new IllegalArgumentException("profileKey must match " + KEY.pattern() + ": " + profileKey);
        }
        return new ProfileMode(profileKey);
    }

    /**
     * @return whether the profile outlives the session
     */
    public boolean isPersistent() {
        return profileKey != null;
    }

    /**
     * @return the key of a persistent profile; empty for {@link #ephemeral()}
     */
    public Optional<String> getProfileKey() {
        return Optional.ofNullable(profileKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProfileMode)) {
            return false;
        }
        ProfileMode that = (ProfileMode) o;
        return profileKey == null ? that.profileKey == null : profileKey.equals(that.profileKey);
    }

    @Override
    public int hashCode() {
        return profileKey == null ? 0 : profileKey.hashCode();
    }

    @Override
    public String toString() {
        return profileKey == null ? "EPHEMERAL" : "PERSISTENT_EXCLUSIVE(" + profileKey + ")";
    }
}
