package com.aresstack.serp4j.provider.argos;

/**
 * Failure of an {@link ArgosWebSearch} call that is not a plain transport error: protocol violation, HTTP error status
 * of the sidecar, or an error reported by the {@code web_search} tool itself.
 */
public final class ArgosWebSearchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** What went wrong; the provider maps it to a {@code SearchErrorCategory}. */
    public enum Kind {
        /** The sidecar answered with an HTTP error status. */
        HTTP_STATUS,
        /** The answer is not valid MCP / JSON-RPC. */
        PROTOCOL,
        /** The JSON-RPC call returned an error object. */
        RPC_ERROR,
        /** The tool ran but reported an error ({@code isError: true}), e.g. browser runtime unavailable. */
        TOOL_ERROR
    }

    private final Kind kind;
    private final int httpStatus;

    public ArgosWebSearchException(Kind kind, String message) {
        this(kind, message, -1, null);
    }

    public ArgosWebSearchException(Kind kind, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        if (kind == null) {
            throw new NullPointerException("kind must not be null");
        }
        this.kind = kind;
        this.httpStatus = httpStatus;
    }

    public Kind getKind() {
        return kind;
    }

    /** HTTP status of the sidecar answer, or {@code -1}. */
    public int getHttpStatus() {
        return httpStatus;
    }
}
