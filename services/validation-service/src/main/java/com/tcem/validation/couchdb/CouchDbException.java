package com.tcem.validation.couchdb;

/**
 * Runtime exception wrapping CouchDB HTTP and I/O failures.
 */
public class CouchDbException extends RuntimeException {

    public CouchDbException(String message) {
        super(message);
    }

    public CouchDbException(String message, Throwable cause) {
        super(message, cause);
    }
}

