package com.tcem.exception.repository;

import com.tcem.common.idempotency.IdempotencyKey;
import com.tcem.exception.config.CouchDbProperties;
import com.tcem.exception.couchdb.CouchDbOperations;
import com.tcem.exception.model.document.IdempotencyDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * CouchDB repository for tracking idempotency keys to prevent duplicate exception processing.
 * Idempotency documents are stored in the 'exceptions' database alongside exception records.
 */
@Repository
public class IdempotencyRepository {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyRepository.class);

    private final CouchDbOperations couchDb;
    private final CouchDbProperties properties;

    public IdempotencyRepository(CouchDbOperations couchDb, CouchDbProperties properties) {
        this.couchDb = couchDb;
        this.properties = properties;
    }

    /**
     * Returns true if this idempotency key has already been processed.
     */
    public boolean isProcessed(IdempotencyKey key) {
        if (key == null) {
            return false;
        }
        return couchDb.getDocument(
                properties.getExceptionsDatabase(),
                key.toStorageKey(),
                IdempotencyDocument.class
        ).isPresent();
    }

    /**
     * Records that an idempotency key has been processed with the given result label.
     */
    public void record(IdempotencyKey key, String processingResult) {
        if (key == null) {
            return;
        }
        IdempotencyDocument document = IdempotencyDocument.builder()
                .id(key.toStorageKey())
                .tradeId(key.getTradeId())
                .messageId(key.getMessageId())
                .schemaVersion(key.getSchemaVersion())
                .eventType(key.getEventType() != null ? key.getEventType().name() : "EXCEPTION_CREATED")
                .processingResult(processingResult != null ? processingResult : "CLASSIFIED")
                .processedAt(Instant.now())
                .build();

        couchDb.putDocument(properties.getExceptionsDatabase(), key.toStorageKey(), document);
        log.debug("Recorded idempotency key: {}", key.toStorageKey());
    }
}

