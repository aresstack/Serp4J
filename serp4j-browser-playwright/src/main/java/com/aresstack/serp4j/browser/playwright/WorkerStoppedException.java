package com.aresstack.serp4j.browser.playwright;

/** Internal signal: the worker's owner thread has stopped; translated by the session into a typed exception. */
final class WorkerStoppedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    WorkerStoppedException(String message) {
        super(message);
    }
}
