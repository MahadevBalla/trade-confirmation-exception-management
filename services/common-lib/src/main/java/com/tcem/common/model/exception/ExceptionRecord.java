package com.tcem.common.model.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Structured exception record (FR7–FR9).
 * <p>
 * Created by the exception-service when STP fails or an enrichment step cannot
 * complete. Published to {@code exception.created} and subsequently to
 * {@code exception.classified} once classification and recommendation are attached.
 * Stored in CouchDB ({@code exceptions} database).
 * </p>
 *
 * <h3>SLA and prioritisation</h3>
 * The recommendation engine (FR9) scores each exception using four inputs:
 * <ol>
 *   <li>{@code operationalImpact} – financial/legal risk</li>
 *   <li>Age / {@code slaDeadline}</li>
 *   <li>Exception volume for the same {@code category}</li>
 *   <li>{@code resolutionDifficulty}</li>
 * </ol>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExceptionRecord {

    /** Platform-generated unique ID. Example: {@code EXC-30001} */
    private String exceptionId;

    /** Trade that triggered this exception. */
    private String tradeId;

    /** Trade version at the time the exception was raised. */
    private int tradeVersion;

    /** Kafka message that triggered the exception. */
    private String sourceMessageId;

    /** Correlation ID for distributed-trace linking. */
    private String correlationId;

    // -----------------------------------------------------------------------
    // Classification (FR8)
    // -----------------------------------------------------------------------

    /** High-level category from the spec §12 taxonomy. */
    private ExceptionCategory category;

    /** Granular sub-theme within the category. */
    private ExceptionSubTheme subTheme;

    /**
     * Confidence level of the classification (0.0–1.0).
     * MVP: always {@code 1.0} (deterministic rules). Stretch: ML-derived.
     */
    @Builder.Default
    private double classificationConfidence = 1.0;

    /** Human-readable explanation of why this exception was raised. */
    private String reasonCode;

    /** Specific field name(s) that triggered the exception. Comma-separated. */
    private String affectedFields;

    // -----------------------------------------------------------------------
    // Recommendation (FR9)
    // -----------------------------------------------------------------------

    /**
     * Suggested next action.
     * Example: {@code "Find source field; replay after update"}
     */
    private String recommendedAction;

    /**
     * Suggested owner queue.
     * Example: {@code DATA_REMEDIATION}, {@code OPERATIONS_REVIEW}
     */
    private String ownerQueue;

    /**
     * Whether replaying the message after a data fix is expected to resolve this.
     * Drives the replay UI button (FR12).
     */
    @Builder.Default
    private boolean replayMayResolve = false;

    /**
     * Estimated resolution difficulty.
     * Values: {@code LOW}, {@code MEDIUM}, {@code HIGH}
     */
    private String resolutionDifficulty;

    /**
     * Operational impact level.
     * Values: {@code LOW}, {@code MEDIUM}, {@code HIGH}, {@code CRITICAL}
     */
    private String operationalImpact;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /** UTC timestamp when the exception was first captured. */
    private Instant capturedAt;

    /**
     * SLA deadline by which this exception must be resolved.
     * Derived from {@code operationalImpact} and product configuration.
     */
    private Instant slaDeadline;

    /**
     * Current status of the exception.
     * Values: {@code OPEN}, {@code IN_PROGRESS}, {@code RESOLVED}, {@code CLOSED}, {@code REPLAYED}
     */
    @Builder.Default
    private String status = "OPEN";

    /** UTC timestamp of resolution. Null while still open. */
    private Instant resolvedAt;

    /** Work-item ID linked to this exception (FR10). Null until linked. */
    private String linkedWorkItemId;

    public String getExceptionId() {
        return exceptionId;
    }

    public void setExceptionId(String exceptionId) {
        this.exceptionId = exceptionId;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public int getTradeVersion() {
        return tradeVersion;
    }

    public void setTradeVersion(int tradeVersion) {
        this.tradeVersion = tradeVersion;
    }

    public String getSourceMessageId() {
        return sourceMessageId;
    }

    public void setSourceMessageId(String sourceMessageId) {
        this.sourceMessageId = sourceMessageId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public ExceptionCategory getCategory() {
        return category;
    }

    public void setCategory(ExceptionCategory category) {
        this.category = category;
    }

    public ExceptionSubTheme getSubTheme() {
        return subTheme;
    }

    public void setSubTheme(ExceptionSubTheme subTheme) {
        this.subTheme = subTheme;
    }

    public double getClassificationConfidence() {
        return classificationConfidence;
    }

    public void setClassificationConfidence(double classificationConfidence) {
        this.classificationConfidence = classificationConfidence;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getAffectedFields() {
        return affectedFields;
    }

    public void setAffectedFields(String affectedFields) {
        this.affectedFields = affectedFields;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getOwnerQueue() {
        return ownerQueue;
    }

    public void setOwnerQueue(String ownerQueue) {
        this.ownerQueue = ownerQueue;
    }

    public boolean isReplayMayResolve() {
        return replayMayResolve;
    }

    public void setReplayMayResolve(boolean replayMayResolve) {
        this.replayMayResolve = replayMayResolve;
    }

    public String getResolutionDifficulty() {
        return resolutionDifficulty;
    }

    public void setResolutionDifficulty(String resolutionDifficulty) {
        this.resolutionDifficulty = resolutionDifficulty;
    }

    public String getOperationalImpact() {
        return operationalImpact;
    }

    public void setOperationalImpact(String operationalImpact) {
        this.operationalImpact = operationalImpact;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }

    public Instant getSlaDeadline() {
        return slaDeadline;
    }

    public void setSlaDeadline(Instant slaDeadline) {
        this.slaDeadline = slaDeadline;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getLinkedWorkItemId() {
        return linkedWorkItemId;
    }

    public void setLinkedWorkItemId(String linkedWorkItemId) {
        this.linkedWorkItemId = linkedWorkItemId;
    }
}
