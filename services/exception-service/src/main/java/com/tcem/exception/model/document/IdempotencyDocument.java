package com.tcem.exception.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * CouchDB document tracking processed idempotency keys in the 'exceptions' database.
 * Stored alongside exception documents to enable duplicate suppression in the consumer pipeline.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdempotencyDocument {

    @JsonProperty("_id")
    private String id;

    @JsonProperty("_rev")
    private String rev;

    private String docType = "IDEMPOTENCY";
    private String tradeId;
    private String messageId;
    private String schemaVersion;
    private String eventType;
    private String processingResult;
    private Instant processedAt;

    public IdempotencyDocument() {}

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

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getProcessingResult() { return processingResult; }
    public void setProcessingResult(String processingResult) { this.processingResult = processingResult; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public static class Builder {
        private String id;
        private String rev;
        private String docType = "IDEMPOTENCY";
        private String tradeId;
        private String messageId;
        private String schemaVersion;
        private String eventType;
        private String processingResult;
        private Instant processedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder rev(String rev) { this.rev = rev; return this; }
        public Builder docType(String docType) { this.docType = docType; return this; }
        public Builder tradeId(String tradeId) { this.tradeId = tradeId; return this; }
        public Builder messageId(String messageId) { this.messageId = messageId; return this; }
        public Builder schemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder processingResult(String processingResult) { this.processingResult = processingResult; return this; }
        public Builder processedAt(Instant processedAt) { this.processedAt = processedAt; return this; }

        public IdempotencyDocument build() {
            IdempotencyDocument doc = new IdempotencyDocument();
            doc.id = this.id;
            doc.rev = this.rev;
            doc.docType = this.docType;
            doc.tradeId = this.tradeId;
            doc.messageId = this.messageId;
            doc.schemaVersion = this.schemaVersion;
            doc.eventType = this.eventType;
            doc.processingResult = this.processingResult;
            doc.processedAt = this.processedAt;
            return doc;
        }
    }
}

