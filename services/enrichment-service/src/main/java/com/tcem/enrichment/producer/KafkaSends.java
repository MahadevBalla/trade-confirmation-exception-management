package com.tcem.enrichment.producer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Waits for a Kafka send for a bounded time. The consumer records its idempotency key only after a publish
 * succeeded, so sends cannot be fire-and-forget; the bound stops a slow broker from holding a consumer thread
 * past {@code max.poll.interval.ms}. A send that times out may still be delivered later, which downstream
 * idempotency checks absorb.
 */
final class KafkaSends {

    private KafkaSends() {}

    static void await(Future<?> send, long timeoutMs, String description) {
        try {
            send.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + description, ex);
        } catch (TimeoutException ex) {
            throw new IllegalStateException("Timed out after " + timeoutMs + " ms publishing " + description, ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Failed to publish " + description, ex.getCause());
        }
    }
}
