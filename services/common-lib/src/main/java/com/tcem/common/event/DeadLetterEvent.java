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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    @Builder.Default
    private int replayCount = 0;

    /**
     * Whether a replay is safe for this failure type.
     * {@code true} for transient failures (timeout, network).
     * {@code false} for permanent failures (malformed JSON, unknown schema version).
     */
    @Builder.Default
    private boolean replayable = true;

    /**
     * Current DLQ entry status.
     * Values: {@code PENDING_REPLAY}, {@code REPLAYING}, {@code REPLAY_SUCCEEDED},
     * {@code PERMANENTLY_FAILED}
     */
    @Builder.Default
    private String dlqStatus = "PENDING_REPLAY";
}
