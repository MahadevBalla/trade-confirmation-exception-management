package com.tcem.common.idempotency;

import com.tcem.common.constants.EventType;

import java.util.Objects;

/**
 * Composite idempotency key stamped on every service write.
 * <p>
 * The four components together uniquely identify a single processing attempt
 * for a specific version of a specific trade message:
 * <ul>
 *   <li>{@code tradeId} – identifies the trade</li>
 *   <li>{@code messageId} – identifies the exact message received</li>
 *   <li>{@code schemaVersion} – identifies the payload format</li>
 *   <li>{@code eventType} – identifies the lifecycle operation</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * Before processing any Kafka message, a service must:
 * <ol>
 *   <li>Build an {@code IdempotencyKey} using {@link #of(String, String, String, EventType)}.</li>
 *   <li>Check its local idempotency store (CouchDB or Redis) for {@link #toStorageKey()}.</li>
 *   <li>If already processed: skip (return early, do not re-publish downstream).</li>
 *   <li>If not processed: process, then write the key to the store atomically.</li>
 * </ol>
 *
 * <h3>DLQ replay</h3>
 * When a message is replayed from the DLQ (FR12), its {@code replayCount}
 * is incremented in the {@code MessageHeader} but the idempotency key stays
 * the same. Services must clear the idempotency record before replay is triggered
 * so the message is reprocessed (the replay UI button does this via the
 * workitem-service API).
 *
 * <h3>Storage key format</h3>
 * {@code idempotency:{tradeId}:{messageId}:{schemaVersion}:{eventType}}
 * </p>
 */
public final class IdempotencyKey {

    private final String tradeId;
    private final String messageId;
    private final String schemaVersion;
    private final EventType eventType;

    private IdempotencyKey(String tradeId, String messageId, String schemaVersion, EventType eventType) {
        this.tradeId = tradeId;
        this.messageId = messageId;
        this.schemaVersion = schemaVersion;
        this.eventType = eventType;
    }

    /**
     * Builds an {@code IdempotencyKey} from message header fields and the trade ID.
     *
     * @param tradeId       trade identifier (from {@code TradeEvent.trade.tradeId})
     * @param messageId     from {@code MessageHeader.messageId}
     * @param schemaVersion from {@code MessageHeader.schemaVersion}
     * @param eventType     from {@code MessageHeader.eventType}
     * @return a fully constructed idempotency key
     * @throws NullPointerException if any argument is null
     */
    public static IdempotencyKey of(String tradeId,
                                    String messageId,
                                    String schemaVersion,
                                    EventType eventType) {
        Objects.requireNonNull(tradeId, "tradeId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        return new IdempotencyKey(tradeId, messageId, schemaVersion, eventType);
    }

    public String getTradeId()       { return tradeId; }
    public String getMessageId()     { return messageId; }
    public String getSchemaVersion() { return schemaVersion; }
    public EventType getEventType()  { return eventType; }

    /**
     * Returns the canonical storage key string used as the CouchDB document ID
     * or Redis key.
     */
    public String toStorageKey() {
        return "idempotency:" + tradeId + ":" + messageId + ":" + schemaVersion + ":" + eventType.name();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyKey that)) return false;
        return Objects.equals(tradeId, that.tradeId)
                && Objects.equals(messageId, that.messageId)
                && Objects.equals(schemaVersion, that.schemaVersion)
                && eventType == that.eventType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeId, messageId, schemaVersion, eventType);
    }

    @Override
    public String toString() {
        return "IdempotencyKey{" + toStorageKey() + "}";
    }
}
