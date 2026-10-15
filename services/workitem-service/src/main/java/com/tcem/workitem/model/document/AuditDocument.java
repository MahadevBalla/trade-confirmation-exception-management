package com.tcem.workitem.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tcem.common.constants.ProcessingStatus;

import java.time.Instant;

/**
 * Audit entry appended to 'audit-events'. Same shape as ingestion-service's audit documents
 * ({@code docType=AUDIT_EVENT}) so both services' entries show in one trade audit trail.
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
    private String correlationId;
    private String eventType;
    private String stage;
    private ProcessingStatus status;
    private Instant timestamp;
    private String details;
    private String sourceService = "workitem-service";

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRev() { return rev; }
    public void setRev(String rev) { this.rev = rev; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

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
}
