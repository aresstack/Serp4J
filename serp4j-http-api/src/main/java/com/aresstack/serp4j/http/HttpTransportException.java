package com.aresstack.serp4j.http;

/**
 * Failure of an {@link HttpTransport} to obtain a response: connection, TLS, timeout, protocol or I/O problems, or
 * cancellation. A server response with an error status is <em>not</em> an exception – it is delivered as
 * {@link HttpResponse}. Messages must not contain request bodies, response bodies or credentials. Providers map
 * this exception onto their own error category; it never leaves an adapter (architecture.md §5.4).
 */
public final class HttpTransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Technical kind of the failure. */
    public enum Kind {
        /** Host not resolvable or connection refused. */
        CONNECT,
        /** TLS handshake or certificate failure. */
        TLS,
        /** Connect, read or total timeout. */
        TIMEOUT,
        /** Malformed response or protocol violation. */
        PROTOCOL,
        /** The request was cancelled or the transport closed. */
        CANCELLED,
        /** Other I/O failure. */
        IO,
        UNKNOWN
    }

    private final Kind kind;

    public HttpTransportException(Kind kind, String message) {
        this(kind, message, null);
    }

    public HttpTransportException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        if (kind == null) {
            throw new NullPointerException("kind must not be null");
        }
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    /** Whether retrying the same request may succeed. */
    public boolean isRetryable() {
        return kind == Kind.CONNECT || kind == Kind.TIMEOUT || kind == Kind.IO;
    }

    @Override
    public String toString() {
        return "HttpTransportException{kind=" + kind + ", message=" + getMessage() + '}';
    }
}
