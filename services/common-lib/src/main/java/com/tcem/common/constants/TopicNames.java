package com.tcem.common.constants;

/**
 * Kafka topic name constants.
 * <p>
 * All services must use these constants when producing or consuming; never
 * hard-code topic strings in service code.
 * </p>
 * Source: docs/kafka-topics.md
 */
public final class TopicNames {

    private TopicNames() {}

    // -----------------------------------------------------------------------
    // Core trade pipeline topics
    // -----------------------------------------------------------------------

    /** Raw FpML-inspired JSON as received from the producer / seed loader. */
    public static final String TRADE_RAW = "trade.raw";

    /** Passed ingestion-service schema validation (FR2). */
    public static final String TRADE_ACCEPTED = "trade.accepted";

    /** After reference-data enrichment (FR4). */
    public static final String TRADE_ENRICHED = "trade.enriched";

    // -----------------------------------------------------------------------
    // STP outcome topics
    // -----------------------------------------------------------------------

    /** STP decision made (FR5). Fan-out point for confirmation vs exception path. */
    public static final String STP_VALIDATED = "stp.validated";

    /** Confirmation output produced (FR6). */
    public static final String CONFIRMATION_GENERATED = "confirmation.generated";

    /** Raised when STP fails (FR7). */
    public static final String EXCEPTION_CREATED = "exception.created";

    /** Theme / sub-theme + recommendation attached (FR8, FR9). */
    public static final String EXCEPTION_CLASSIFIED = "exception.classified";

    // -----------------------------------------------------------------------
    // Work-item and review topics
    // -----------------------------------------------------------------------

    /** Work item created or linked (FR10). */
    public static final String WORKITEM_LINKED = "workitem.linked";

    /** Routed to manual review queue. */
    public static final String MANUAL_REVIEW_REQUIRED = "manual.review.required";

    // -----------------------------------------------------------------------
    // Resilience topics
    // -----------------------------------------------------------------------

    /**
     * Irrecoverable messages with diagnostic context (FR12).
     * Any service may produce to this topic on unrecoverable failure.
     */
    public static final String DEADLETTER_TRADE = "deadletter.trade";
}
