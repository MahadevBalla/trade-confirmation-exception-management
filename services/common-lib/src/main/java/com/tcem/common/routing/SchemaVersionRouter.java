package com.tcem.common.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.message.TradeEvent;

import java.io.IOException;

/**
 * Deserialises a raw Kafka message payload into the current canonical
 * {@link TradeEvent} model, handling all supported schema versions.
 *
 * <h3>Version strategy</h3>
 * The {@code messageHeader.schemaVersion} field in each message selects the
 * migration path:
 * <ul>
 *   <li>{@code capstone-fpml-json-1.0} → current canonical model; no migration needed.</li>
 *   <li>{@code capstone-fpml-json-1.1} → reserved; add migration logic in
 *       {@link #migrateFrom1_1(JsonNode)} when introduced.</li>
 *   <li>Unknown version → throws {@link UnsupportedSchemaVersionException}; the
 *       calling service should route the message to the DLQ with reason code
 *       {@code SCHEMA_VERSION_UNSUPPORTED}.</li>
 * </ul>
 *
 * <h3>Backward compatibility rules</h3>
 * <ul>
 *   <li>Adding an optional field to an existing version = no migration needed
 *       (Jackson ignores unknown fields on read; nulls on missing on older
 *       messages).</li>
 *   <li>Renaming / removing a field = bump MAJOR version, add migration here.</li>
 * </ul>
 *
 * <h3>AMEND_TRADE version-2 scenario</h3>
 * If a version 2 of a trade arrives with {@code schemaVersion = 1.0} and only
 * non-confirmable fields changed, the router returns the canonical
 * {@link TradeEvent} as-is. The {@link com.tcem.common.registry.ConfirmationFingerprint}
 * then determines no new confirmation is needed.
 *
 * <h3>Thread safety</h3>
 * Stateless; use a shared singleton.
 */
public final class SchemaVersionRouter {

    private final ObjectMapper objectMapper;

    public SchemaVersionRouter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Deserialises and migrates a raw JSON byte array into the current canonical
     * {@link TradeEvent}.
     *
     * @param rawJson raw Kafka message value bytes
     * @return the canonical {@link TradeEvent}
     * @throws UnsupportedSchemaVersionException if the version is not registered
     * @throws IOException                        if JSON parsing fails
     */
    public TradeEvent route(byte[] rawJson) throws IOException {
        JsonNode root = objectMapper.readTree(rawJson);
        String schemaVersion = extractSchemaVersion(root);

        return switch (schemaVersion) {
            case SchemaVersions.V1_0 -> objectMapper.treeToValue(root, TradeEvent.class);
            case SchemaVersions.V1_1 -> migrateFrom1_1(root);
            default -> throw new UnsupportedSchemaVersionException(
                    "No migration path registered for schemaVersion: " + schemaVersion
                    + ". Register a handler in SchemaVersionRouter or route to DLQ.");
        };
    }

    /**
     * Convenience overload for string payloads (e.g. from tests).
     */
    public TradeEvent route(String rawJson) throws IOException {
        return route(rawJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // -----------------------------------------------------------------------
    // Private migration methods
    // -----------------------------------------------------------------------

    private String extractSchemaVersion(JsonNode root) {
        JsonNode header = root.get("messageHeader");
        if (header == null || header.get("schemaVersion") == null) {
            // Treat missing version as v1.0 for backward compatibility
            return SchemaVersions.V1_0;
        }
        return header.get("schemaVersion").asText(SchemaVersions.V1_0);
    }

    /**
     * Placeholder migration from {@code 1.1} → canonical model.
     * Implement when v1.1 is introduced. Until then, this is unreachable.
     */
    private TradeEvent migrateFrom1_1(JsonNode root) throws IOException {
        // Example migration: if 1.1 renames a field, transform the JsonNode here
        // before delegating to the standard deserialiser.
        // For now: treat as equivalent to 1.0.
        return objectMapper.treeToValue(root, TradeEvent.class);
    }

    // -----------------------------------------------------------------------
    // Checked exception
    // -----------------------------------------------------------------------

    /**
     * Thrown when an incoming message carries an unrecognised {@code schemaVersion}.
     * The calling service must route the message to {@code deadletter.trade} with
     * {@code DLQ_REASON = SCHEMA_VERSION_UNSUPPORTED}.
     */
    public static class UnsupportedSchemaVersionException extends RuntimeException {
        public UnsupportedSchemaVersionException(String message) {
            super(message);
        }
    }
}
