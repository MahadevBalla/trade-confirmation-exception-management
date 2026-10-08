package com.tcem.common.constants;

/**
 * Kafka message-header key constants.
 * <p>
 * Header values are always UTF-8 encoded strings. Services must propagate
 * these headers downstream to preserve the correlation chain.
 * </p>
 */
public final class HeaderKeys {

    private HeaderKeys() {}

    /** Unique identifier for the logical message (from {@code MessageHeader.messageId}). */
    public static final String MESSAGE_ID = "tcem.messageId";

    /** Correlation / saga ID threading related messages together. */
    public static final String CORRELATION_ID = "tcem.correlationId";

    /** Trade ID – used as the Kafka partition key for ordering within a trade. */
    public static final String TRADE_ID = "tcem.tradeId";

    /** Schema version string matching {@code MessageHeader.schemaVersion}. */
    public static final String SCHEMA_VERSION = "tcem.schemaVersion";

    /** Event type discriminator (e.g. {@code NEW_TRADE}, {@code AMEND_TRADE}). */
    public static final String EVENT_TYPE = "tcem.eventType";

    /** Originating service name for distributed-trace correlation. */
    public static final String SOURCE_SERVICE = "tcem.sourceService";

    /**
     * Idempotency key – composite of tradeId + messageId + schemaVersion + eventType.
     * Services that perform writes must check this key before processing.
     */
    public static final String IDEMPOTENCY_KEY = "tcem.idempotencyKey";

    /** ISO-8601 timestamp when the message was produced. */
    public static final String PRODUCED_AT = "tcem.producedAt";

    /** Number of replay attempts (0 = first attempt). */
    public static final String REPLAY_COUNT = "tcem.replayCount";

    /** Reason text when a message is routed to the DLQ. */
    public static final String DLQ_REASON = "tcem.dlq.reason";
}
