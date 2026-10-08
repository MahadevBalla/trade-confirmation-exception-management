package com.tcem.common.constants;

/**
 * Processing status of a trade through the pipeline.
 * <p>
 * Each transition is an immutable audit event written to CouchDB and Kafka.
 * </p>
 */
public enum ProcessingStatus {

    /** Message received on trade.raw; not yet schema-validated. */
    RECEIVED,

    /** Failed ingestion-service schema validation (FR2). Routed to DLQ. */
    SCHEMA_INVALID,

    /** Passed schema validation; on trade.accepted. */
    ACCEPTED,

    /** Being enriched by enrichment-service (FR4). */
    ENRICHING,

    /** Enrichment failed (e.g. unknown counterparty). */
    ENRICHMENT_FAILED,

    /** Enrichment complete; on trade.enriched. */
    ENRICHED,

    /** STP rules engine running (FR5). */
    VALIDATING,

    /** Passed STP; confirmation generation triggered. */
    STP_PASSED,

    /** Failed STP; exception created. */
    STP_FAILED,

    /** Confirmation output generated (FR6). */
    CONFIRMED,

    /** Exception captured and classified (FR7–FR8). */
    EXCEPTION_CAPTURED,

    /** Work item linked (FR10). */
    WORKITEM_LINKED,

    /** Under manual review (FR9). */
    MANUAL_REVIEW,

    /** Resolved after manual intervention. */
    RESOLVED,

    /** Message permanently irrecoverable; in dead-letter topic (FR12). */
    DEAD_LETTER,

    /** Message replayed from DLQ (FR12). */
    REPLAYED
}
