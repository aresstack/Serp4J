package com.aresstack.serp4j.browser;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** A required or used {@link BrowserCapability} is not supported by the runtime. */
@SuppressWarnings("serial")
public final class UnsupportedBrowserCapabilityException extends BrowserException {

    private static final long serialVersionUID = 1L;

    private final Set<BrowserCapability> missing;

    /**
     * @param missing unsupported capabilities, not empty
     * @param message message
     */
    public UnsupportedBrowserCapabilityException(Set<BrowserCapability> missing, String message) {
        super(message);
        if (Args.notNull(missing, "missing").isEmpty()) {
            throw new IllegalArgumentException("missing must not be empty");
        }
        this.missing = Collections.unmodifiableSet(EnumSet.copyOf(missing));
    }

    /**
     * @return unsupported capabilities
     */
    public Set<BrowserCapability> getMissing() {
        return missing;
    }
}
