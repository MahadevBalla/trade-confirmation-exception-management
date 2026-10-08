package com.tcem.common.model.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.common.constants.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Envelope header present on every Kafka message produced by this platform.
 * <p>
 * Inspired by FpML's message header concepts: reusable identifiers, correlation
 * chain, and schema version for forward-compatible evolution.
 * </p>
 *
 * <h3>Schema versioning</h3>
 * The {@code schemaVersion} field drives the {@link com.tcem.common.routing.SchemaVersionRouter}.
 * Current canonical version: {@code capstone-fpml-json-1.0}.
 * When a new version is introduced, add a constant to {@link SchemaVersions} and
 * register a migration path in the router – existing topics stay backward-compatible.
 *
 * <h3>Idempotency</h3>
 * The combination of {@code tradeId + messageId + schemaVersion + eventType} forms
 * the idempotency key (see {@link com.tcem.common.idempotency.IdempotencyKey}).
 * Services MUST check this key before processing a message to prevent duplicate writes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageHeader {

    /**
     * Globally unique message identifier (UUID or platform-assigned sequence).
     * Example: {@code MSG-000001}
     */
    private String messageId;

    /**
     * Saga/correlation ID threading all messages belonging to the same business
     * transaction (trade + all its exceptions, confirmations, work items).
     * Example: {@code CORR-10001}
     */
    private String correlationId;

    /**
     * Lifecycle event discriminator.
     * The {@link com.tcem.common.routing.SchemaVersionRouter} uses this together
     * with {@code schemaVersion} to select the correct deserialisation path.
     */
    private EventType eventType;

    /**
     * Schema version of the message payload.
     * Must match one of the constants in {@link SchemaVersions}.
     * <p>
     * Example: {@code capstone-fpml-json-1.0}
     * </p>
     */
    private String schemaVersion;

    /**
     * ISO-8601 UTC timestamp when this message was created by the producing service.
     */
    private Instant createdTimestamp;

    /**
     * Name of the service that produced this message.
     * Used for distributed tracing and DLQ diagnostics.
     * Example: {@code ingestion-service}
     */
    private String sourceService;

    /**
     * Number of times this message has been replayed from the DLQ (FR12).
     * Zero on the first attempt. Services use this to detect persistent failures.
     */
    @Builder.Default
    private int replayCount = 0;
}
