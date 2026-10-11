package com.tcem.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.common.model.message.MessageHeader;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Dead-letter queue envelope for the {@code deadletter.trade} topic (FR12).
 * <p>
 * Any service that encounters an irrecoverable processing failure produces
 * this event. The workitem-service consumes it to:
 * <ol>
 *   <li>Store the DLQ entry in CouchDB ({@code dlq_entries} database).</li>
 *   <li>Optionally create a {@link WorkItemEvent} of type {@code DATA_FIX}.</li>
 *   <li>Expose the replay button in the UI (FR11, FR12).</li>
 * </ol>
 *
 * <h3>Replay protocol</h3>
 * When a user triggers replay from the UI:
 * <ol>
 *   <li>The workitem-service clears the idempotency record for this message.</li>
 *   <li>It increments {@code replayCount} in the original {@code MessageHeader}.</li>
 *   <li>It republishes the {@code originalPayload} to the appropriate upstream topic.</li>
 *   <li>The {@link MessageHeader#getEventType()} is set to {@code REPLAY}.</li>
 * </ol>
 *
 * <h3>DLQ policy</h3>
 * After {@code maxReplayAttempts} replays without success, the entry is marked
 * {@code PERMANENTLY_FAILED} and a {@code HIGH} priority work item is created.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeadLetterEvent {

    /** Header of the <em>DLQ message itself</em> (not the original message header). */
    private MessageHeader messageHeader;

    /** Header copied from the original failing message (for traceability). */
    private MessageHeader originalMessageHeader;

    /**
     * Raw JSON bytes of the original message that failed.
     * Stored as a string (base64 or escaped JSON) so it can be replayed verbatim.
     */
    private String originalPayload;

    /** Topic on which the original message was being processed. */
    private String failedOnTopic;

    /** Service that produced this DLQ entry. Example: {@code enrichment-service} */
    private String failedInService;

    /**
     * Human-readable reason for the failure.
     * Example: {@code "Enrichment timeout after 3 retries: counterparty PTY-CP-999 not found"}
     */
    private String failureReason;

    /**
     * Exception class name if the failure was caused by a Java exception.
     * Example: {@code java.util.concurrent.TimeoutException}
     */
    private String exceptionClass;

    /** Stack trace excerpt (first 2000 characters) for diagnostics. */
    private String stackTraceExcerpt;

    /** UTC timestamp when the failure occurred. */
    private Instant failedAt;

    /**
     * Number of replay attempts made so far (copied from the message header).
     * Stops replay after a configured threshold.
     */
    private int replayCount = 0;

    /**
     * Whether a replay is safe for this failure type.
     * {@code true} for transient failures (timeout, network).
     * {@code false} for permanent failures (malformed JSON, unknown schema version).
     */
    private boolean replayable = true;

    /**
     * Current DLQ entry status.
     * Values: {@code PENDING_REPLAY}, {@code REPLAYING}, {@code REPLAY_SUCCEEDED},
     * {@code PERMANENTLY_FAILED}
     */
    private String dlqStatus = "PENDING_REPLAY";

    public DeadLetterEvent() {}

    public DeadLetterEvent(MessageHeader messageHeader, MessageHeader originalMessageHeader,
                           String originalPayload, String failedOnTopic, String failedInService,
                           String failureReason, String exceptionClass, String stackTraceExcerpt,
                           Instant failedAt, int replayCount, boolean replayable, String dlqStatus) {
        this.messageHeader = messageHeader;
        this.originalMessageHeader = originalMessageHeader;
        this.originalPayload = originalPayload;
        this.failedOnTopic = failedOnTopic;
        this.failedInService = failedInService;
        this.failureReason = failureReason;
        this.exceptionClass = exceptionClass;
        this.stackTraceExcerpt = stackTraceExcerpt;
        this.failedAt = failedAt;
        this.replayCount = replayCount;
        this.replayable = replayable;
        this.dlqStatus = dlqStatus != null ? dlqStatus : "PENDING_REPLAY";
    }

    public static Builder builder() {
        return new Builder();
    }

    public MessageHeader getMessageHeader() { return messageHeader; }
    public void setMessageHeader(MessageHeader messageHeader) { this.messageHeader = messageHeader; }

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

    public int getReplayCount() { return replayCount; }
    public void setReplayCount(int replayCount) { this.replayCount = replayCount; }

    public boolean isReplayable() { return replayable; }
    public void setReplayable(boolean replayable) { this.replayable = replayable; }

    public String getDlqStatus() { return dlqStatus; }
    public void setDlqStatus(String dlqStatus) { this.dlqStatus = dlqStatus; }

    public static class Builder {
        private MessageHeader messageHeader;
        private MessageHeader originalMessageHeader;
        private String originalPayload;
        private String failedOnTopic;
        private String failedInService;
        private String failureReason;
        private String exceptionClass;
        private String stackTraceExcerpt;
        private Instant failedAt;
        private int replayCount = 0;
        private boolean replayable = true;
        private String dlqStatus = "PENDING_REPLAY";

        public Builder messageHeader(MessageHeader messageHeader) { this.messageHeader = messageHeader; return this; }
        public Builder originalMessageHeader(MessageHeader originalMessageHeader) { this.originalMessageHeader = originalMessageHeader; return this; }
        public Builder originalPayload(String originalPayload) { this.originalPayload = originalPayload; return this; }
        public Builder failedOnTopic(String failedOnTopic) { this.failedOnTopic = failedOnTopic; return this; }
        public Builder failedInService(String failedInService) { this.failedInService = failedInService; return this; }
        public Builder failureReason(String failureReason) { this.failureReason = failureReason; return this; }
        public Builder exceptionClass(String exceptionClass) { this.exceptionClass = exceptionClass; return this; }
        public Builder stackTraceExcerpt(String stackTraceExcerpt) { this.stackTraceExcerpt = stackTraceExcerpt; return this; }
        public Builder failedAt(Instant failedAt) { this.failedAt = failedAt; return this; }
        public Builder replayCount(int replayCount) { this.replayCount = replayCount; return this; }
        public Builder replayable(boolean replayable) { this.replayable = replayable; return this; }
        public Builder dlqStatus(String dlqStatus) { this.dlqStatus = dlqStatus; return this; }

        public DeadLetterEvent build() {
            return new DeadLetterEvent(messageHeader, originalMessageHeader, originalPayload,
                    failedOnTopic, failedInService, failureReason, exceptionClass, stackTraceExcerpt,
                    failedAt, replayCount, replayable, dlqStatus);
        }
    }
}
