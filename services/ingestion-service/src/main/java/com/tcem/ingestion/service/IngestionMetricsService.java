package com.tcem.ingestion.service;

import com.tcem.ingestion.model.IngestionMetricsSummary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service tracking trade ingestion volume, validation rates, and STP metrics (FR11).
 */
@Service
public class IngestionMetricsService {

    private final AtomicLong receivedCount = new AtomicLong(0);
    private final AtomicLong acceptedCount = new AtomicLong(0);
    private final AtomicLong schemaInvalidCount = new AtomicLong(0);
    private final AtomicLong duplicateCount = new AtomicLong(0);
    private final AtomicLong deadLetterCount = new AtomicLong(0);

    public void recordReceived() {
        receivedCount.incrementAndGet();
    }

    public void recordAccepted() {
        acceptedCount.incrementAndGet();
    }

    public void recordSchemaInvalid() {
        schemaInvalidCount.incrementAndGet();
    }

    public void recordDuplicate() {
        duplicateCount.incrementAndGet();
    }

    public void recordDeadLetter() {
        deadLetterCount.incrementAndGet();
    }

    /**
     * Returns an aggregated metrics summary for dashboard widgets.
     */
    public IngestionMetricsSummary getSummary() {
        long received = receivedCount.get();
        long accepted = acceptedCount.get();
        long invalid = schemaInvalidCount.get();
        long duplicates = duplicateCount.get();
        long deadLetters = deadLetterCount.get();

        long evaluated = accepted + invalid + deadLetters;
        double acceptanceRate = evaluated > 0
                ? Math.round((double) accepted / evaluated * 10000.0) / 100.0
                : 100.0;

        return new IngestionMetricsSummary(
                received,
                accepted,
                invalid,
                duplicates,
                deadLetters,
                acceptanceRate,
                Instant.now()
        );
    }

    /**
     * Returns STP rate response specifically formatted for the React Operations UI (/metrics/stp-rate).
     */
    public Map<String, Object> getStpRate() {
        long accepted = acceptedCount.get();
        long invalid = schemaInvalidCount.get();
        long evaluated = accepted + invalid;
        double stpRate = evaluated > 0
                ? Math.round((double) accepted / evaluated * 10000.0) / 100.0
                : 100.0;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("service", "ingestion-service");
        metrics.put("stpRatePercent", stpRate);
        metrics.put("totalTradesEvaluated", evaluated);
        metrics.put("acceptedTrades", accepted);
        metrics.put("schemaInvalidTrades", invalid);
        metrics.put("timestamp", Instant.now().toString());
        return metrics;
    }
}
