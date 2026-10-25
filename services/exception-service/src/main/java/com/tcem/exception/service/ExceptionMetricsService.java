package com.tcem.exception.service;

import com.tcem.exception.model.ExceptionMetricsSummary;
import com.tcem.exception.model.ExceptionProcessingResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory metrics tracker for the exception-service pipeline (FR11).
 * <p>
 * Maintains atomic counters for each pipeline outcome so the
 * {@link com.tcem.exception.controller.ExceptionQueryController} can serve
 * live dashboard widgets without hitting CouchDB on every request.
 * </p>
 *
 * <h3>Usage</h3>
 * Call {@link #record(ExceptionProcessingResult)} from {@code DefaultExceptionProcessingService}
 * after each message is fully processed. For dead-letter events that occur before
 * a result is available, call {@link #recordReceived()} followed by {@link #recordDeadLetter()}.
 */
@Service
public class ExceptionMetricsService {

    private final AtomicLong receivedCount     = new AtomicLong(0);
    private final AtomicLong classifiedCount   = new AtomicLong(0);
    private final AtomicLong manualReviewCount = new AtomicLong(0);
    private final AtomicLong duplicateCount    = new AtomicLong(0);
    private final AtomicLong deadLetterCount   = new AtomicLong(0);

    /** Increments the received counter. Call at the start of each pipeline invocation. */
    public void recordReceived() {
        receivedCount.incrementAndGet();
    }

    /** Records the terminal outcome of the pipeline. */
    public void record(ExceptionProcessingResult result) {
        if (result == null) return;
        switch (result) {
            case CLASSIFIED    -> classifiedCount.incrementAndGet();
            case MANUAL_REVIEW -> manualReviewCount.incrementAndGet();
            case DUPLICATE     -> duplicateCount.incrementAndGet();
            case DEAD_LETTER   -> deadLetterCount.incrementAndGet();
        }
    }

    /** Convenience method — increments the dead-letter counter directly. */
    public void recordDeadLetter() {
        deadLetterCount.incrementAndGet();
    }

    /**
     * Builds an {@link ExceptionMetricsSummary} snapshot for the dashboard endpoint.
     */
    public ExceptionMetricsSummary getSummary() {
        long received    = receivedCount.get();
        long classified  = classifiedCount.get();
        long manual      = manualReviewCount.get();
        long duplicates  = duplicateCount.get();
        long deadLetters = deadLetterCount.get();

        // Classification rate = (classified + manual) / (received - duplicates)
        // Excludes duplicates as they are legitimate suppression, not failures.
        long processable = received - duplicates;
        double classificationRate = processable > 0
                ? Math.round((double) (classified + manual) / processable * 10000.0) / 100.0
                : 100.0;

        return new ExceptionMetricsSummary(
                received, classified, manual, duplicates, deadLetters,
                classificationRate, Instant.now());
    }

    /**
     * Returns the exception classification rate metrics for the React dashboard
     * ({@code GET /metrics/exception-rate}).
     */
    public Map<String, Object> getExceptionRate() {
        long received    = receivedCount.get();
        long classified  = classifiedCount.get();
        long manual      = manualReviewCount.get();
        long duplicates  = duplicateCount.get();
        long deadLetters = deadLetterCount.get();
        long processable = received - duplicates;

        double classificationRate = processable > 0
                ? Math.round((double) (classified + manual) / processable * 10000.0) / 100.0
                : 100.0;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("service", "exception-service");
        metrics.put("classificationRatePercent", classificationRate);
        metrics.put("totalReceived", received);
        metrics.put("totalClassified", classified);
        metrics.put("totalManualReview", manual);
        metrics.put("totalDuplicates", duplicates);
        metrics.put("totalDeadLetter", deadLetters);
        metrics.put("timestamp", Instant.now().toString());
        return metrics;
    }

    // -------------------------------------------------------------------------
    // Package-visible accessors for testing
    // -------------------------------------------------------------------------

    long getReceivedCount()     { return receivedCount.get(); }
    long getClassifiedCount()   { return classifiedCount.get(); }
    long getManualReviewCount() { return manualReviewCount.get(); }
    long getDuplicateCount()    { return duplicateCount.get(); }
    long getDeadLetterCount()   { return deadLetterCount.get(); }
}

