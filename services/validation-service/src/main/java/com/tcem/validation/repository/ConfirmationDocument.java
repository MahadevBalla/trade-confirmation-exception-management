package com.tcem.validation.repository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tcem.common.constants.ConfirmationStatus;

import java.time.Instant;

/**
 * CouchDB document representing a confirmation within the 'confirmations' database.
 * <p>
 * Implements an isolated persistence document pattern decoupled from transport-level event contracts:
 * self-contained document with explicit getters, setters, constructors, and builder.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfirmationDocument {

    @JsonProperty("_id")
    private String id;

    @JsonProperty("_rev")
    private String rev;

    private String docType = "CONFIRMATION";
    private String confirmationId;
    private String tradeId;
    private int tradeVersion = 1;
    private String sourceMessageId;
    private String templateId;
    private String templateVersion;
    private ConfirmationStatus status;
    private String confirmationFingerprint;
    private String previousConfirmationFingerprint;
    private String confirmationBody;
    private Instant generatedAt;
    private String generatedBy;

    public ConfirmationDocument() {}

    public ConfirmationDocument(String id, String rev, String docType, String confirmationId,
                                String tradeId, int tradeVersion, String sourceMessageId,
                                String templateId, String templateVersion, ConfirmationStatus status,
                                String confirmationFingerprint, String previousConfirmationFingerprint,
                                String confirmationBody, Instant generatedAt, String generatedBy) {
        this.id = id;
        this.rev = rev;
        this.docType = docType != null ? docType : "CONFIRMATION";
        this.confirmationId = confirmationId;
        this.tradeId = tradeId;
        this.tradeVersion = tradeVersion;
        this.sourceMessageId = sourceMessageId;
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.status = status;
        this.confirmationFingerprint = confirmationFingerprint;
        this.previousConfirmationFingerprint = previousConfirmationFingerprint;
        this.confirmationBody = confirmationBody;
        this.generatedAt = generatedAt;
        this.generatedBy = generatedBy;
    }

    public static Builder builder() {
        return new Builder();
    }

    // -----------------------------------------------------------------------
    // Getters and Setters
    // -----------------------------------------------------------------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRev() { return rev; }
    public void setRev(String rev) { this.rev = rev; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getConfirmationId() { return confirmationId; }
    public void setConfirmationId(String confirmationId) { this.confirmationId = confirmationId; }

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    public int getTradeVersion() { return tradeVersion; }
    public void setTradeVersion(int tradeVersion) { this.tradeVersion = tradeVersion; }

    public String getSourceMessageId() { return sourceMessageId; }
    public void setSourceMessageId(String sourceMessageId) { this.sourceMessageId = sourceMessageId; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(String templateVersion) { this.templateVersion = templateVersion; }

    public ConfirmationStatus getStatus() { return status; }
    public void setStatus(ConfirmationStatus status) { this.status = status; }

    public String getConfirmationFingerprint() { return confirmationFingerprint; }
    public void setConfirmationFingerprint(String confirmationFingerprint) { this.confirmationFingerprint = confirmationFingerprint; }

    public String getPreviousConfirmationFingerprint() { return previousConfirmationFingerprint; }
    public void setPreviousConfirmationFingerprint(String previousConfirmationFingerprint) { this.previousConfirmationFingerprint = previousConfirmationFingerprint; }

    public String getConfirmationBody() { return confirmationBody; }
    public void setConfirmationBody(String confirmationBody) { this.confirmationBody = confirmationBody; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private String id;
        private String rev;
        private String docType = "CONFIRMATION";
        private String confirmationId;
        private String tradeId;
        private int tradeVersion = 1;
        private String sourceMessageId;
        private String templateId;
        private String templateVersion;
        private ConfirmationStatus status;
        private String confirmationFingerprint;
        private String previousConfirmationFingerprint;
        private String confirmationBody;
        private Instant generatedAt;
        private String generatedBy;

        public Builder id(String id) { this.id = id; return this; }
        public Builder rev(String rev) { this.rev = rev; return this; }
        public Builder docType(String docType) { this.docType = docType; return this; }
        public Builder confirmationId(String confirmationId) { this.confirmationId = confirmationId; return this; }
        public Builder tradeId(String tradeId) { this.tradeId = tradeId; return this; }
        public Builder tradeVersion(int tradeVersion) { this.tradeVersion = tradeVersion; return this; }
        public Builder sourceMessageId(String sourceMessageId) { this.sourceMessageId = sourceMessageId; return this; }
        public Builder templateId(String templateId) { this.templateId = templateId; return this; }
        public Builder templateVersion(String templateVersion) { this.templateVersion = templateVersion; return this; }
        public Builder status(ConfirmationStatus status) { this.status = status; return this; }
        public Builder confirmationFingerprint(String confirmationFingerprint) { this.confirmationFingerprint = confirmationFingerprint; return this; }
        public Builder previousConfirmationFingerprint(String previousConfirmationFingerprint) { this.previousConfirmationFingerprint = previousConfirmationFingerprint; return this; }
        public Builder confirmationBody(String confirmationBody) { this.confirmationBody = confirmationBody; return this; }
        public Builder generatedAt(Instant generatedAt) { this.generatedAt = generatedAt; return this; }
        public Builder generatedBy(String generatedBy) { this.generatedBy = generatedBy; return this; }

        public ConfirmationDocument build() {
            return new ConfirmationDocument(id, rev, docType, confirmationId, tradeId, tradeVersion,
                    sourceMessageId, templateId, templateVersion, status, confirmationFingerprint,
                    previousConfirmationFingerprint, confirmationBody, generatedAt, generatedBy);
        }
    }
}
