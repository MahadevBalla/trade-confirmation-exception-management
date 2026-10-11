package com.tcem.ingestion.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.constants.ProductType;
import com.tcem.common.model.trade.Trade;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * CouchDB document representing a trade within the 'trades' database.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeDocument {

    @JsonProperty("_id")
    private String id;

    @JsonProperty("_rev")
    private String rev;

    private String docType = "TRADE";
    private String tradeId;
    private String sourceSystemTradeId;
    private int tradeVersion = 1;
    private ProductType productType;
    private String messageId;
    private String correlationId;
    private String schemaVersion;
    private ProcessingStatus processingStatus;
    private Instant receivedTimestamp;
    private Instant updatedTimestamp;
    private Trade canonicalTrade;
    private String rawPayload;
    private List<String> validationErrors = new ArrayList<>();
    private String idempotencyKey;

    public TradeDocument() {}

    public TradeDocument(String id, String rev, String docType, String tradeId, String sourceSystemTradeId,
                         int tradeVersion, ProductType productType, String messageId, String correlationId,
                         String schemaVersion, ProcessingStatus processingStatus, Instant receivedTimestamp,
                         Instant updatedTimestamp, Trade canonicalTrade, String rawPayload,
                         List<String> validationErrors, String idempotencyKey) {
        this.id = id;
        this.rev = rev;
        this.docType = docType != null ? docType : "TRADE";
        this.tradeId = tradeId;
        this.sourceSystemTradeId = sourceSystemTradeId;
        this.tradeVersion = tradeVersion;
        this.productType = productType;
        this.messageId = messageId;
        this.correlationId = correlationId;
        this.schemaVersion = schemaVersion;
        this.processingStatus = processingStatus;
        this.receivedTimestamp = receivedTimestamp;
        this.updatedTimestamp = updatedTimestamp;
        this.canonicalTrade = canonicalTrade;
        this.rawPayload = rawPayload;
        this.validationErrors = validationErrors != null ? validationErrors : new ArrayList<>();
        this.idempotencyKey = idempotencyKey;
    }

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

    public String getSourceSystemTradeId() { return sourceSystemTradeId; }
    public void setSourceSystemTradeId(String sourceSystemTradeId) { this.sourceSystemTradeId = sourceSystemTradeId; }

    public int getTradeVersion() { return tradeVersion; }
    public void setTradeVersion(int tradeVersion) { this.tradeVersion = tradeVersion; }

    public ProductType getProductType() { return productType; }
    public void setProductType(ProductType productType) { this.productType = productType; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public ProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(ProcessingStatus processingStatus) { this.processingStatus = processingStatus; }

    public Instant getReceivedTimestamp() { return receivedTimestamp; }
    public void setReceivedTimestamp(Instant receivedTimestamp) { this.receivedTimestamp = receivedTimestamp; }

    public Instant getUpdatedTimestamp() { return updatedTimestamp; }
    public void setUpdatedTimestamp(Instant updatedTimestamp) { this.updatedTimestamp = updatedTimestamp; }

    public Trade getCanonicalTrade() { return canonicalTrade; }
    public void setCanonicalTrade(Trade canonicalTrade) { this.canonicalTrade = canonicalTrade; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public List<String> getValidationErrors() { return validationErrors; }
    public void setValidationErrors(List<String> validationErrors) { this.validationErrors = validationErrors; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public static class Builder {
        private String id;
        private String rev;
        private String docType = "TRADE";
        private String tradeId;
        private String sourceSystemTradeId;
        private int tradeVersion = 1;
        private ProductType productType;
        private String messageId;
        private String correlationId;
        private String schemaVersion;
        private ProcessingStatus processingStatus;
        private Instant receivedTimestamp;
        private Instant updatedTimestamp;
        private Trade canonicalTrade;
        private String rawPayload;
        private List<String> validationErrors = new ArrayList<>();
        private String idempotencyKey;

        public Builder id(String id) { this.id = id; return this; }
        public Builder rev(String rev) { this.rev = rev; return this; }
        public Builder docType(String docType) { this.docType = docType; return this; }
        public Builder tradeId(String tradeId) { this.tradeId = tradeId; return this; }
        public Builder sourceSystemTradeId(String sourceSystemTradeId) { this.sourceSystemTradeId = sourceSystemTradeId; return this; }
        public Builder tradeVersion(int tradeVersion) { this.tradeVersion = tradeVersion; return this; }
        public Builder productType(ProductType productType) { this.productType = productType; return this; }
        public Builder messageId(String messageId) { this.messageId = messageId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder schemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; return this; }
        public Builder processingStatus(ProcessingStatus processingStatus) { this.processingStatus = processingStatus; return this; }
        public Builder receivedTimestamp(Instant receivedTimestamp) { this.receivedTimestamp = receivedTimestamp; return this; }
        public Builder updatedTimestamp(Instant updatedTimestamp) { this.updatedTimestamp = updatedTimestamp; return this; }
        public Builder canonicalTrade(Trade canonicalTrade) { this.canonicalTrade = canonicalTrade; return this; }
        public Builder rawPayload(String rawPayload) { this.rawPayload = rawPayload; return this; }
        public Builder validationErrors(List<String> validationErrors) { this.validationErrors = validationErrors; return this; }
        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }

        public TradeDocument build() {
            return new TradeDocument(id, rev, docType, tradeId, sourceSystemTradeId, tradeVersion,
                    productType, messageId, correlationId, schemaVersion, processingStatus,
                    receivedTimestamp, updatedTimestamp, canonicalTrade, rawPayload,
                    validationErrors, idempotencyKey);
        }
    }
}
