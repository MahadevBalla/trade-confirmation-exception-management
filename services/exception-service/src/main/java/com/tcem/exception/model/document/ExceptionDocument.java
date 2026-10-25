package com.tcem.exception.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;

import java.time.Instant;

/**
 * CouchDB document representing a classified exception in the 'exceptions' database (FR7-FR9).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExceptionDocument {

    @JsonProperty("_id")
    private String id;

    @JsonProperty("_rev")
    private String rev;

    private String docType = "EXCEPTION";

    // Identifiers
    private String exceptionId;
    private String tradeId;
    private int tradeVersion;
    private String sourceMessageId;
    private String correlationId;
    private String idempotencyKey;

    // Classification (FR8)
    private ExceptionCategory category;
    private ExceptionSubTheme subTheme;
    private double classificationConfidence = 1.0;
    private String reasonCode;
    private String affectedFields;

    // Recommendation (FR9)
    private String recommendedAction;
    private String ownerQueue;
    private boolean replayMayResolve;
    private String resolutionDifficulty;
    private String operationalImpact;

    // Lifecycle
    private String status = "OPEN";
    private Instant capturedAt;
    private Instant classifiedAt;
    private Instant slaDeadline;
    private Instant resolvedAt;
    private String linkedWorkItemId;

    public ExceptionDocument() {}

    public static Builder builder() {
        return new Builder();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRev() { return rev; }
    public void setRev(String rev) { this.rev = rev; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getExceptionId() { return exceptionId; }
    public void setExceptionId(String exceptionId) { this.exceptionId = exceptionId; }

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    public int getTradeVersion() { return tradeVersion; }
    public void setTradeVersion(int tradeVersion) { this.tradeVersion = tradeVersion; }

    public String getSourceMessageId() { return sourceMessageId; }
    public void setSourceMessageId(String sourceMessageId) { this.sourceMessageId = sourceMessageId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public ExceptionCategory getCategory() { return category; }
    public void setCategory(ExceptionCategory category) { this.category = category; }

    public ExceptionSubTheme getSubTheme() { return subTheme; }
    public void setSubTheme(ExceptionSubTheme subTheme) { this.subTheme = subTheme; }

    public double getClassificationConfidence() { return classificationConfidence; }
    public void setClassificationConfidence(double classificationConfidence) { this.classificationConfidence = classificationConfidence; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getAffectedFields() { return affectedFields; }
    public void setAffectedFields(String affectedFields) { this.affectedFields = affectedFields; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }

    public String getOwnerQueue() { return ownerQueue; }
    public void setOwnerQueue(String ownerQueue) { this.ownerQueue = ownerQueue; }

    public boolean isReplayMayResolve() { return replayMayResolve; }
    public void setReplayMayResolve(boolean replayMayResolve) { this.replayMayResolve = replayMayResolve; }

    public String getResolutionDifficulty() { return resolutionDifficulty; }
    public void setResolutionDifficulty(String resolutionDifficulty) { this.resolutionDifficulty = resolutionDifficulty; }

    public String getOperationalImpact() { return operationalImpact; }
    public void setOperationalImpact(String operationalImpact) { this.operationalImpact = operationalImpact; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }

    public Instant getClassifiedAt() { return classifiedAt; }
    public void setClassifiedAt(Instant classifiedAt) { this.classifiedAt = classifiedAt; }

    public Instant getSlaDeadline() { return slaDeadline; }
    public void setSlaDeadline(Instant slaDeadline) { this.slaDeadline = slaDeadline; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getLinkedWorkItemId() { return linkedWorkItemId; }
    public void setLinkedWorkItemId(String linkedWorkItemId) { this.linkedWorkItemId = linkedWorkItemId; }

    public static class Builder {
        private String id;
        private String rev;
        private String docType = "EXCEPTION";
        private String exceptionId;
        private String tradeId;
        private int tradeVersion;
        private String sourceMessageId;
        private String correlationId;
        private String idempotencyKey;
        private ExceptionCategory category;
        private ExceptionSubTheme subTheme;
        private double classificationConfidence = 1.0;
        private String reasonCode;
        private String affectedFields;
        private String recommendedAction;
        private String ownerQueue;
        private boolean replayMayResolve;
        private String resolutionDifficulty;
        private String operationalImpact;
        private String status = "OPEN";
        private Instant capturedAt;
        private Instant classifiedAt;
        private Instant slaDeadline;
        private Instant resolvedAt;
        private String linkedWorkItemId;

        public Builder id(String id) { this.id = id; return this; }
        public Builder rev(String rev) { this.rev = rev; return this; }
        public Builder docType(String docType) { this.docType = docType; return this; }
        public Builder exceptionId(String exceptionId) { this.exceptionId = exceptionId; return this; }
        public Builder tradeId(String tradeId) { this.tradeId = tradeId; return this; }
        public Builder tradeVersion(int tradeVersion) { this.tradeVersion = tradeVersion; return this; }
        public Builder sourceMessageId(String sourceMessageId) { this.sourceMessageId = sourceMessageId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder category(ExceptionCategory category) { this.category = category; return this; }
        public Builder subTheme(ExceptionSubTheme subTheme) { this.subTheme = subTheme; return this; }
        public Builder classificationConfidence(double classificationConfidence) { this.classificationConfidence = classificationConfidence; return this; }
        public Builder reasonCode(String reasonCode) { this.reasonCode = reasonCode; return this; }
        public Builder affectedFields(String affectedFields) { this.affectedFields = affectedFields; return this; }
        public Builder recommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; return this; }
        public Builder ownerQueue(String ownerQueue) { this.ownerQueue = ownerQueue; return this; }
        public Builder replayMayResolve(boolean replayMayResolve) { this.replayMayResolve = replayMayResolve; return this; }
        public Builder resolutionDifficulty(String resolutionDifficulty) { this.resolutionDifficulty = resolutionDifficulty; return this; }
        public Builder operationalImpact(String operationalImpact) { this.operationalImpact = operationalImpact; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder capturedAt(Instant capturedAt) { this.capturedAt = capturedAt; return this; }
        public Builder classifiedAt(Instant classifiedAt) { this.classifiedAt = classifiedAt; return this; }
        public Builder slaDeadline(Instant slaDeadline) { this.slaDeadline = slaDeadline; return this; }
        public Builder resolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; return this; }
        public Builder linkedWorkItemId(String linkedWorkItemId) { this.linkedWorkItemId = linkedWorkItemId; return this; }

        public ExceptionDocument build() {
            ExceptionDocument doc = new ExceptionDocument();
            doc.id = this.id;
            doc.rev = this.rev;
            doc.docType = this.docType;
            doc.exceptionId = this.exceptionId;
            doc.tradeId = this.tradeId;
            doc.tradeVersion = this.tradeVersion;
            doc.sourceMessageId = this.sourceMessageId;
            doc.correlationId = this.correlationId;
            doc.idempotencyKey = this.idempotencyKey;
            doc.category = this.category;
            doc.subTheme = this.subTheme;
            doc.classificationConfidence = this.classificationConfidence;
            doc.reasonCode = this.reasonCode;
            doc.affectedFields = this.affectedFields;
            doc.recommendedAction = this.recommendedAction;
            doc.ownerQueue = this.ownerQueue;
            doc.replayMayResolve = this.replayMayResolve;
            doc.resolutionDifficulty = this.resolutionDifficulty;
            doc.operationalImpact = this.operationalImpact;
            doc.status = this.status;
            doc.capturedAt = this.capturedAt;
            doc.classifiedAt = this.classifiedAt;
            doc.slaDeadline = this.slaDeadline;
            doc.resolvedAt = this.resolvedAt;
            doc.linkedWorkItemId = this.linkedWorkItemId;
            return doc;
        }
    }
}

