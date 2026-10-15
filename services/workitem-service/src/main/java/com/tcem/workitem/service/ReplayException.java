package com.tcem.workitem.service;

/**
 * Thrown when a replay request cannot be honoured. {@link Reason} lets the REST layer map to HTTP codes.
 */
public class ReplayException extends RuntimeException {

    public enum Reason { NOT_FOUND, NOT_REPLAYABLE, MAX_ATTEMPTS_REACHED, ALREADY_REPLAYING, PUBLISH_FAILED }

    private final Reason reason;

    public ReplayException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ReplayException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
