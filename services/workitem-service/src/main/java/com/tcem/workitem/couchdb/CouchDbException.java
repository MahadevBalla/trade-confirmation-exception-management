package com.tcem.workitem.couchdb;

/**
 * Exception thrown when a CouchDB HTTP operation encounters an error.
 */
public class CouchDbException extends RuntimeException {

    private final int statusCode;

    public CouchDbException(String message) {
        super(message);
        this.statusCode = 500;
    }

    public CouchDbException(String message, int statusCode) {
        super(message + " (HTTP " + statusCode + ")");
        this.statusCode = statusCode;
    }

    public CouchDbException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
