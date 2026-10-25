package com.tcem.exception.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExceptionIdGenerator.
 */
class ExceptionIdGeneratorTest {

    @Test
    void nextId_startsAtEXC30001() {
        ExceptionIdGenerator generator = new ExceptionIdGenerator();
        assertThat(generator.nextId()).isEqualTo("EXC-30001");
    }

    @Test
    void nextId_incrementsSequentially() {
        ExceptionIdGenerator generator = new ExceptionIdGenerator();
        assertThat(generator.nextId()).isEqualTo("EXC-30001");
        assertThat(generator.nextId()).isEqualTo("EXC-30002");
        assertThat(generator.nextId()).isEqualTo("EXC-30003");
    }

    @Test
    void nextId_formatsWithFiveDigits() {
        ExceptionIdGenerator generator = new ExceptionIdGenerator();
        String id = generator.nextId();
        assertThat(id).matches("EXC-\\d{5}");
    }

    @Test
    void nextId_isThreadSafe() throws InterruptedException {
        ExceptionIdGenerator generator = new ExceptionIdGenerator();
        java.util.Set<String> generatedIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        int threadCount = 20;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> generatedIds.add(generator.nextId()));
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        // All IDs must be unique
        assertThat(generatedIds).hasSize(threadCount);
    }
}

