package com.tcem.ingestion.model;

import com.tcem.common.constants.ProcessingStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome result of processing a raw trade message through the ingestion pipeline.
 */
public class IngestionResult {

    public enum Status {
        ACCEPTED,
        DUPLICATE,
        REJECTED,
        DEAD_LETTER
    }

    private final Status status;
    private final String tradeId;
    private final String messageId;
    private final String idempotencyKey;
    private final String details;
    private final List<String> errors;

    public IngestionResult(Status status, String tradeId, String messageId, String idempotencyKey,
                           String details, List<String> errors) {
        this.status = status;
        this.tradeId = tradeId;
        this.messageId = messageId;
        this.idempotencyKey = idempotencyKey;
        this.details = details;
        this.errors = errors != null ? new ArrayList<>(errors) : Collections.emptyList();
    }

    public static IngestionResult accepted(String tradeId, String messageId, String idempotencyKey) {
        return new IngestionResult(Status.ACCEPTED, tradeId, messageId, idempotencyKey,
                "Trade accepted and schema validated", Collections.emptyList());
    }

    public static IngestionResult duplicate(String tradeId, String messageId, String idempotencyKey) {
        return new IngestionResult(Status.DUPLICATE, tradeId, messageId, idempotencyKey,
                "Duplicate message suppressed by idempotency filter", Collections.emptyList());
    }

    public static IngestionResult rejected(String tradeId, String messageId, String idempotencyKey,
                                          String details, List<String> errors) {
        return new IngestionResult(Status.REJECTED, tradeId, messageId, idempotencyKey, details, errors);
    }

    public static IngestionResult deadLetter(String tradeId, String details, String errorReason) {
        return new IngestionResult(Status.DEAD_LETTER, tradeId, null, null, details,
                List.of(errorReason != null ? errorReason : "Fatal error"));
    }

    public Status getStatus() { return status; }
    public String getTradeId() { return tradeId; }
    public String getMessageId() { return messageId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getDetails() { return details; }
    public List<String> getErrors() { return Collections.unmodifiableList(errors); }

    public boolean isAccepted() { return status == Status.ACCEPTED; }
    public boolean isDuplicate() { return status == Status.DUPLICATE; }
    public boolean isRejected() { return status == Status.REJECTED; }
    public boolean isDeadLetter() { return status == Status.DEAD_LETTER; }

    @Override
    public String toString() {
        return "IngestionResult{" +
                "status=" + status +
                ", tradeId='" + tradeId + '\'' +
                ", messageId='" + messageId + '\'' +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", details='" + details + '\'' +
                ", errors=" + errors +
                '}';
    }
}
