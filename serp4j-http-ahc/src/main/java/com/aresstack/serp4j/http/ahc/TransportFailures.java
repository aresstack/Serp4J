package com.aresstack.serp4j.http.ahc;

import com.aresstack.serp4j.http.HttpRequest;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.http.HttpTransportException.Kind;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.CodecException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Translates failures of async-http-client and Netty into {@link HttpTransportException}s whose messages and
 * causes cannot leak request or response content.
 */
final class TransportFailures {

    private static final int MAX_CAUSE_DEPTH = 16;

    private TransportFailures() {
    }

    static HttpTransportException translate(Throwable failure, HttpRequest request) {
        HttpTransportException own = find(failure, HttpTransportException.class);
        if (own != null) {
            return own;
        }
        Kind kind = classify(failure);
        return new HttpTransportException(kind, describe(kind) + " (" + request.getMethod() + " "
                + request.getRedactedUrl() + ")", new SanitizedCause(failure));
    }

    static HttpTransportException timeout(HttpRequest request, long timeoutNanos) {
        return new HttpTransportException(Kind.TIMEOUT, "request timed out after "
                + TimeUnit.NANOSECONDS.toMillis(timeoutNanos) + " ms (" + request.getMethod() + " "
                + request.getRedactedUrl() + ")");
    }

    /**
     * Classifies by the whole cause chain; the more specific kind wins (e.g. a TLS failure wrapped in a Netty
     * decoder exception is {@link Kind#TLS}, a connect timeout is {@link Kind#TIMEOUT}, not {@link Kind#CONNECT}).
     */
    static Kind classify(Throwable failure) {
        if (has(failure, CancellationException.class)) {
            return Kind.CANCELLED;
        }
        if (has(failure, SSLException.class)) {
            return Kind.TLS;
        }
        if (has(failure, TimeoutException.class) || has(failure, SocketTimeoutException.class)
                || has(failure, ConnectTimeoutException.class)) {
            return Kind.TIMEOUT;
        }
        if (has(failure, ConnectException.class) || has(failure, UnknownHostException.class)
                || has(failure, NoRouteToHostException.class)) {
            return Kind.CONNECT;
        }
        if (has(failure, CodecException.class)) {
            return Kind.PROTOCOL;
        }
        if (has(failure, ClosedChannelException.class) || has(failure, IOException.class)) {
            return Kind.IO;
        }
        return Kind.UNKNOWN;
    }

    private static String describe(Kind kind) {
        switch (kind) {
            case CONNECT:
                return "connection failed";
            case TLS:
                return "TLS handshake or certificate failure";
            case TIMEOUT:
                return "request timed out";
            case PROTOCOL:
                return "malformed response";
            case CANCELLED:
                return "request cancelled";
            case IO:
                return "I/O failure";
            default:
                return "transport failure";
        }
    }

    private static boolean has(Throwable failure, Class<? extends Throwable> type) {
        return find(failure, type) != null;
    }

    private static <T extends Throwable> T find(Throwable failure, Class<T> type) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return null;
    }

    /**
     * Stand-in for the original failure: keeps the class names of the cause chain and the stack trace for
     * debugging, but no message, because messages of the underlying libraries may contain URLs with credentials.
     */
    static final class SanitizedCause extends Exception {

        private static final long serialVersionUID = 1L;

        SanitizedCause(Throwable original) {
            super(chain(original), null, false, true);
            setStackTrace(original.getStackTrace());
        }

        private static String chain(Throwable original) {
            StringBuilder names = new StringBuilder();
            Throwable current = original;
            for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
                if (names.length() > 0) {
                    names.append(" <- ");
                }
                names.append(current.getClass().getName());
                current = current.getCause() == current ? null : current.getCause();
            }
            return names.toString();
        }
    }
}
