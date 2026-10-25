package com.tcem.exception.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tcem.common.constants.ProcessingStatus;

import java.time.Instant;

/**
 * CouchDB document representing an immutable audit log entry in the 'audit-events' database.
 * Uses the same AUDIT_EVENT shape as ingestion-service so GET /trades/{id}/audit on
 * ingestion-service shows entries from both services.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditDocument {

    @JsonProperty("_id")
    private String id;

    @JsonProperty("_rev")
    private String rev;

    private String docType = "AUDIT_EVENT";
    private String tradeId;
    private String exceptionId;
    private String correlationId;
    private String eventType;
    private String stage;
    private ProcessingStatus status;
    private Instant timestamp;
    private String details;
    private String sourceService = "exception-service";

    public AuditDocument() {}

    public static Builder builder() {
        return new Builder();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRev() { return rev; }
    public void setRev(String rev) { this.rev = rev; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    public String getExceptionId() { return exceptionId; }
    public void setExceptionId(String exceptionId) { this.exceptionId = exceptionId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public ProcessingStatus getStatus() { return status; }
    public void setStatus(ProcessingStatus status) { this.status = status; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getSourceService() { return sourceService; }
    public void setSourceService(String sourceService) { this.sourceService = sourceService; }

    public static class Builder {
        private String id;
        private String rev;
        private String docType = "AUDIT_EVENT";
        private String tradeId;
        private String exceptionId;
        private String correlationId;
        private String eventType;
        private String stage;
        private ProcessingStatus status;
        private Instant timestamp;
        private String details;
        private String sourceService = "exception-service";

        public Builder id(String id) { this.id = id; return this; }
        public Builder rev(String rev) { this.rev = rev; return this; }
        public Builder docType(String docType) { this.docType = docType; return this; }
        public Builder tradeId(String tradeId) { this.tradeId = tradeId; return this; }
        public Builder exceptionId(String exceptionId) { this.exceptionId = exceptionId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder stage(String stage) { this.stage = stage; return this; }
        public Builder status(ProcessingStatus status) { this.status = status; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder details(String details) { this.details = details; return this; }
        public Builder sourceService(String sourceService) { this.sourceService = sourceService; return this; }

        public AuditDocument build() {
            AuditDocument doc = new AuditDocument();
            doc.id = this.id;
            doc.rev = this.rev;
            doc.docType = this.docType;
            doc.tradeId = this.tradeId;
            doc.exceptionId = this.exceptionId;
            doc.correlationId = this.correlationId;
            doc.eventType = this.eventType;
            doc.stage = this.stage;
            doc.status = this.status;
            doc.timestamp = this.timestamp;
            doc.details = this.details;
            doc.sourceService = this.sourceService;
            return doc;
        }
    }
}

