package com.aresstack.serp4j.browser;

/** A page script threw an error or returned a value that cannot be converted to a {@link BrowserValue}. */
public final class ScriptEvaluationException extends BrowserException {

    private static final long serialVersionUID = 1L;

    private final String scriptId;

    /**
     * @param scriptId id of the script
     * @param message  message without page content
     * @param cause    cause, may be {@code null}
     */
    public ScriptEvaluationException(String scriptId, String message, Throwable cause) {
        super(message, cause);
        this.scriptId = scriptId;
    }

    /**
     * @return id of the script
     */
    public String getScriptId() {
        return scriptId;
    }
}
