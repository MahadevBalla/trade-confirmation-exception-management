package com.tcem.ingestion.service;

/**
 * Thrown when the seed data is loaded a second time. Every message would count as a duplicate and raise a new
 * exception and work item, without adding a single trade.
 */
public class SeedAlreadyLoadedException extends RuntimeException {

    public SeedAlreadyLoadedException() {
        super("The seed data is already loaded. Loading it again adds no trades and raises a duplicate exception for "
                + "every message. Reset the stack to start again, or pass force=true if you really want this.");
    }
}
