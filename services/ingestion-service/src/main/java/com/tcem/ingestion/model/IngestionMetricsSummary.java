package com.tcem.ingestion.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Metric summary of trade ingestion throughput and acceptance rates (FR11).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestionMetricsSummary {

    private long totalReceived;
    private long totalAccepted;
    private long totalSchemaInvalid;
    private long totalDuplicates;
    private long totalDeadLetter;
    private double acceptanceRatePercent;
    private Instant timestamp;

    public IngestionMetricsSummary() {
        this.timestamp = Instant.now();
    }

    public IngestionMetricsSummary(long totalReceived, long totalAccepted, long totalSchemaInvalid,
                                   long totalDuplicates, long totalDeadLetter, double acceptanceRatePercent,
                                   Instant timestamp) {
        this.totalReceived = totalReceived;
        this.totalAccepted = totalAccepted;
        this.totalSchemaInvalid = totalSchemaInvalid;
        this.totalDuplicates = totalDuplicates;
        this.totalDeadLetter = totalDeadLetter;
        this.acceptanceRatePercent = acceptanceRatePercent;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public long getTotalReceived() { return totalReceived; }
    public void setTotalReceived(long totalReceived) { this.totalReceived = totalReceived; }

    public long getTotalAccepted() { return totalAccepted; }
    public void setTotalAccepted(long totalAccepted) { this.totalAccepted = totalAccepted; }

    public long getTotalSchemaInvalid() { return totalSchemaInvalid; }
    public void setTotalSchemaInvalid(long totalSchemaInvalid) { this.totalSchemaInvalid = totalSchemaInvalid; }

    public long getTotalDuplicates() { return totalDuplicates; }
    public void setTotalDuplicates(long totalDuplicates) { this.totalDuplicates = totalDuplicates; }

    public long getTotalDeadLetter() { return totalDeadLetter; }
    public void setTotalDeadLetter(long totalDeadLetter) { this.totalDeadLetter = totalDeadLetter; }

    public double getAcceptanceRatePercent() { return acceptanceRatePercent; }
    public void setAcceptanceRatePercent(double acceptanceRatePercent) { this.acceptanceRatePercent = acceptanceRatePercent; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
