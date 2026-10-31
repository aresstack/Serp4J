package com.aresstack.serp4j.testkit.http;

import com.aresstack.serp4j.http.HttpResponse;
import com.aresstack.serp4j.http.HttpTransportException;
import com.aresstack.serp4j.testkit.fixture.Fixture;

import java.time.Duration;

/**
 * One answer of a {@link FakeHttpTransport} stub: a response (any status, also {@code 4xx}/{@code 5xx} – they are
 * responses, not transport failures), a transport failure, or no answer at all. Optionally after a delay. Immutable.
 */
public final class FakeHttpStep {

    private final HttpResponse response;
    private final HttpTransportException.Kind failure;
    private final Duration delay;

    private FakeHttpStep(HttpResponse response, HttpTransportException.Kind failure, Duration delay) {
        this.response = response;
        this.failure = failure;
        this.delay = delay;
    }

    /** Answers with exactly {@code response}. */
    public static FakeHttpStep respond(HttpResponse response) {
        if (response == null) {
            throw new NullPointerException("response must not be null");
        }
        return new FakeHttpStep(response, null, Duration.ZERO);
    }

    /** Answers with {@code status} and an empty body. */
    public static FakeHttpStep status(int status) {
        return respond(HttpResponse.builder(status).build());
    }

    /** Answers with {@code status} and a JSON body. */
    public static FakeHttpStep json(int status, String json) {
        return text(status, json, "application/json; charset=utf-8");
    }

    /** Answers with {@code status} and a text body of {@code contentType}. */
    public static FakeHttpStep text(int status, String text, String contentType) {
        return respond(HttpResponse.builder(status).textBody(text, contentType).build());
    }

    /** Answers with {@code status} and the fixture's bytes and content type. */
    public static FakeHttpStep fixture(int status, Fixture fixture) {
        if (fixture == null) {
            throw new NullPointerException("fixture must not be null");
        }
        return respond(fixture.toHttpResponse(status));
    }

    /** {@code 200} with the fixture. */
    public static FakeHttpStep fixture(Fixture fixture) {
        return fixture(200, fixture);
    }

    /** Fails the request with an {@link HttpTransportException} of {@code kind} – no response was obtained. */
    public static FakeHttpStep fail(HttpTransportException.Kind kind) {
        if (kind == null) {
            throw new NullPointerException("kind must not be null");
        }
        return new FakeHttpStep(null, kind, Duration.ZERO);
    }

    /**
     * Never answers: the request completes only through its own timeout ({@code TIMEOUT}) or when the transport is
     * closed ({@code CANCELLED}).
     */
    public static FakeHttpStep hang() {
        return new FakeHttpStep(null, null, null);
    }

    /**
     * The same answer after {@code delay}. If the request's own timeout is shorter, the request fails with
     * {@code TIMEOUT} instead.
     */
    public FakeHttpStep after(Duration delay) {
        if (delay == null) {
            throw new NullPointerException("delay must not be null");
        }
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative: " + delay);
        }
        if (isHang()) {
            throw new IllegalStateException("hang() has no delay");
        }
        return new FakeHttpStep(response, failure, delay);
    }

    boolean isHang() {
        return response == null && failure == null;
    }

    HttpResponse getResponse() {
        return response;
    }

    HttpTransportException.Kind getFailure() {
        return failure;
    }

    Duration getDelay() {
        return delay;
    }

    @Override
    public String toString() {
        String what = isHang() ? "hang" : failure != null ? "fail " + failure : "status " + response.getStatus();
        return "FakeHttpStep{" + what + (delay != null && !delay.isZero() ? ", after " + delay : "") + '}';
    }
}
