package com.tcem.common.model.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.common.constants.ProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Immutable audit event appended on every trade status transition.
 * <p>
 * Stored in CouchDB ({@code audit_events} database) and never updated.
 * The full sequence of audit events for a trade forms its immutable history
 * (spec §13 NFR: Auditability).
 * Every status transition and manual action must be captured.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditEvent {

    /** Unique audit event ID. Example: {@code AUD-50001} */
    private String auditEventId;

    /** Trade this event belongs to. */
    private String tradeId;

    /** Message ID that caused this transition. */
    private String messageId;

    /** Correlation ID for linking to the full saga. */
    private String correlationId;

    /** Previous processing status (null on first event). */
    private ProcessingStatus fromStatus;

    /** New processing status after this event. */
    private ProcessingStatus toStatus;

    /** Service that recorded this transition. Example: {@code ingestion-service} */
    private String serviceName;

    /** UTC timestamp of the transition. */
    private Instant occurredAt;

    /**
     * Free-text detail about the transition.
     * Example: {@code "Schema validation failed: missing field 'strike'"}
     */
    private String detail;

    /**
     * User ID if the transition was triggered by a manual action.
     * Null for automated pipeline transitions.
     */
    private String actorUserId;

    /**
     * Whether this event was triggered by a replay (FR12).
     */
    @Builder.Default
    private boolean isReplay = false;
}
