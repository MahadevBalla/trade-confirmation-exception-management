package com.tcem.exception.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Aggregate metrics summary DTO — returned by {@code GET /exceptions/metrics/summary} (FR11).
 * <p>
 * Surfaces the in-memory counters maintained by {@link com.tcem.exception.service.ExceptionMetricsService}
 * as a structured JSON payload for the React Operations dashboard widgets.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExceptionMetricsSummary {

    /** Total number of {@code exception.created} messages received by this service. */
    private long totalReceived;

    /** Count of exceptions classified with confidence ≥ 0.6 → {@code exception.classified}. */
    private long totalClassified;

    /** Count of exceptions routed to {@code manual.review.required}. */
    private long totalManualReview;

    /** Count of duplicate messages suppressed by idempotency check. */
    private long totalDuplicates;

    /** Count of messages routed to {@code deadletter.trade}. */
    private long totalDeadLetter;

    /**
     * Percentage of successfully classified exceptions out of all
     * processable messages (total − duplicates − deadLetters).
     * Rounded to 2 decimal places.
     */
    private double classificationRatePercent;

    /** Timestamp when this snapshot was generated. */
    private Instant timestamp;

    public ExceptionMetricsSummary() {
        this.timestamp = Instant.now();
    }

    public ExceptionMetricsSummary(long totalReceived, long totalClassified, long totalManualReview,
                                   long totalDuplicates, long totalDeadLetter,
                                   double classificationRatePercent, Instant timestamp) {
        this.totalReceived            = totalReceived;
        this.totalClassified          = totalClassified;
        this.totalManualReview        = totalManualReview;
        this.totalDuplicates          = totalDuplicates;
        this.totalDeadLetter          = totalDeadLetter;
        this.classificationRatePercent = classificationRatePercent;
        this.timestamp                = timestamp != null ? timestamp : Instant.now();
    }

    public long getTotalReceived() { return totalReceived; }
    public void setTotalReceived(long totalReceived) { this.totalReceived = totalReceived; }

    public long getTotalClassified() { return totalClassified; }
    public void setTotalClassified(long totalClassified) { this.totalClassified = totalClassified; }

    public long getTotalManualReview() { return totalManualReview; }
    public void setTotalManualReview(long totalManualReview) { this.totalManualReview = totalManualReview; }

    public long getTotalDuplicates() { return totalDuplicates; }
    public void setTotalDuplicates(long totalDuplicates) { this.totalDuplicates = totalDuplicates; }

    public long getTotalDeadLetter() { return totalDeadLetter; }
    public void setTotalDeadLetter(long totalDeadLetter) { this.totalDeadLetter = totalDeadLetter; }

    public double getClassificationRatePercent() { return classificationRatePercent; }
    public void setClassificationRatePercent(double classificationRatePercent) {
        this.classificationRatePercent = classificationRatePercent;
    }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}

