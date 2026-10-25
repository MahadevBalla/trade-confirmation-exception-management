package com.tcem.exception.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates unique exception IDs in the format {@code EXC-XXXXX}.
 * <p>
 * Sequence starts at 30001 to match the platform convention (trade IDs start at 10001,
 * confirmation IDs at 20001). Thread-safe via {@link AtomicLong}.
 * </p>
 * <p>
 * If the inbound {@code ExceptionRecord.exceptionId} is already non-null and non-blank
 * (set by an upstream producer), that value is preserved and this generator is not called.
 * </p>
 */
@Component
public class ExceptionIdGenerator {

    private final AtomicLong sequence = new AtomicLong(30001L);

    /**
     * Returns the next exception ID, e.g. {@code EXC-30001}.
     */
    public String nextId() {
        return String.format("EXC-%05d", sequence.getAndIncrement());
    }
}

