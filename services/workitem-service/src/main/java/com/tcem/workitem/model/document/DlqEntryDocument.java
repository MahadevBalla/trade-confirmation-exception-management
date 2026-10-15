package com.tcem.workitem.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tcem.common.model.message.MessageHeader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * CouchDB document in 'dlq-entries': one record per message received on 'deadletter.trade' (FR12).
 * Keeps the verbatim original payload so it can be replayed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DlqEntryDocument {

    public static final String STATUS_PENDING_REPLAY = "PENDING_REPLAY";
    public static final String STATUS_REPLAYING = "REPLAYING";
    public static final String STATUS_REPLAY_SUCCEEDED = "REPLAY_SUCCEEDED";
    public static final String STATUS_PERMANENTLY_FAILED = "PERMANENTLY_FAILED";

    @JsonProperty("_id")
    private String id;

    @JsonProperty("_rev")
    private String rev;

    private String docType = "DLQ_ENTRY";
    private String tradeId;
    private String messageId;
    private String correlationId;
    private MessageHeader originalMessageHeader;
    private String originalPayload;
    private String failedOnTopic;
    private String failedInService;
    private String failureReason;
    private String exceptionClass;
    private String stackTraceExcerpt;
    private Instant failedAt;
    private Instant receivedAt;
    private Instant lastReplayAt;
    private Instant updatedAt;
    private int replayCount;
    private boolean replayable;
    private String dlqStatus = STATUS_PENDING_REPLAY;
    private String linkedWorkItemId;

    /**
     * Set on {@code DLQ_ALIAS} marker docs only: the entry a later failure of a replayed message was merged
     * into. Lets a redelivered dead letter be recognised as already handled.
     */
    private String aliasOf;

    /** One line per failure of this message, oldest first. */
    private List<String> failureHistory = new ArrayList<>();

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

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public MessageHeader getOriginalMessageHeader() { return originalMessageHeader; }
    public void setOriginalMessageHeader(MessageHeader originalMessageHeader) { this.originalMessageHeader = originalMessageHeader; }

    public String getOriginalPayload() { return originalPayload; }
    public void setOriginalPayload(String originalPayload) { this.originalPayload = originalPayload; }

    public String getFailedOnTopic() { return failedOnTopic; }
    public void setFailedOnTopic(String failedOnTopic) { this.failedOnTopic = failedOnTopic; }

    public String getFailedInService() { return failedInService; }
    public void setFailedInService(String failedInService) { this.failedInService = failedInService; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getExceptionClass() { return exceptionClass; }
    public void setExceptionClass(String exceptionClass) { this.exceptionClass = exceptionClass; }

    public String getStackTraceExcerpt() { return stackTraceExcerpt; }
    public void setStackTraceExcerpt(String stackTraceExcerpt) { this.stackTraceExcerpt = stackTraceExcerpt; }

    public Instant getFailedAt() { return failedAt; }
    public void setFailedAt(Instant failedAt) { this.failedAt = failedAt; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Instant getLastReplayAt() { return lastReplayAt; }
    public void setLastReplayAt(Instant lastReplayAt) { this.lastReplayAt = lastReplayAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public int getReplayCount() { return replayCount; }
    public void setReplayCount(int replayCount) { this.replayCount = replayCount; }

    public boolean isReplayable() { return replayable; }
    public void setReplayable(boolean replayable) { this.replayable = replayable; }

    public String getDlqStatus() { return dlqStatus; }
    public void setDlqStatus(String dlqStatus) { this.dlqStatus = dlqStatus; }

    public String getLinkedWorkItemId() { return linkedWorkItemId; }
    public void setLinkedWorkItemId(String linkedWorkItemId) { this.linkedWorkItemId = linkedWorkItemId; }

    public String getAliasOf() { return aliasOf; }
    public void setAliasOf(String aliasOf) { this.aliasOf = aliasOf; }

    public List<String> getFailureHistory() { return failureHistory; }
    public void setFailureHistory(List<String> failureHistory) {
        this.failureHistory = failureHistory != null ? failureHistory : new ArrayList<>();
    }

    public static DlqEntryDocument alias(String id, String targetId) {
        DlqEntryDocument alias = new DlqEntryDocument();
        alias.setId(id);
        alias.setDocType("DLQ_ALIAS");
        alias.setAliasOf(targetId);
        alias.setDlqStatus(null);
        return alias;
    }
}
