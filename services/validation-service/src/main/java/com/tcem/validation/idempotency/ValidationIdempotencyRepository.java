package com.tcem.validation.idempotency;

import com.tcem.common.idempotency.IdempotencyKey;
import com.tcem.validation.config.CouchDbProperties;
import com.tcem.validation.couchdb.CouchDbOperations;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Durable duplicate-message marker for the validation-service (audit issue C1-03).
 * <p>
 * Mirrors {@code EnrichmentIdempotencyRepository}: one small CouchDB document per processed
 * {@link IdempotencyKey} in the {@code validation-idempotency} database. The document id is
 * prefixed with {@code validation-} so it can never collide with markers written by other services.
 * <p>
 * This marker covers the case the confirmation-based guard cannot: a trade that failed STP never
 * produces a {@code ConfirmationDocument}, so without this store a redelivered message would
 * re-run STP and emit a duplicate {@code exception.created}.
 */
@Repository
public class ValidationIdempotencyRepository {

    private final CouchDbOperations couchDb;
    private final String database;

    public ValidationIdempotencyRepository(CouchDbOperations couchDb, CouchDbProperties properties) {
        this.couchDb = couchDb;
        this.database = properties.getIdempotencyDatabase();
    }

    /** Returns {@code true} if this exact message was already fully processed. */
    public boolean isProcessed(IdempotencyKey key) {
        return couchDb.getDocument(database, documentId(key), Map.class).isPresent();
    }

    /** Marks the message as processed. Call only after all downstream publishes succeeded. */
    public void record(IdempotencyKey key) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("docType", "VALIDATION_IDEMPOTENCY");
        document.put("idempotencyKey", key.toStorageKey());
        document.put("processedAt", Instant.now().toString());
        couchDb.putDocument(database, documentId(key), document);
    }

    private String documentId(IdempotencyKey key) {
        return "validation-" + key.toStorageKey();
    }
}
