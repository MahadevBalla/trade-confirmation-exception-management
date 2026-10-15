package com.tcem.workitem.service;

/**
 * Thrown when a work-item request cannot be honoured. {@link Reason} lets the REST layer map to HTTP codes.
 */
public class WorkItemException extends RuntimeException {

    public enum Reason { NOT_FOUND, INVALID_REQUEST, CONFLICT }

    private final Reason reason;

    public WorkItemException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
