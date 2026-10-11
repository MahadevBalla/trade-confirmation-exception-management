package com.tcem.ingestion.repository;

import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.idempotency.IdempotencyKey;
import com.tcem.ingestion.config.CouchDbProperties;
import com.tcem.ingestion.couchdb.CouchDbOperations;
import com.tcem.ingestion.model.document.IdempotencyDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * CouchDB repository for tracking idempotency keys to ensure duplicate suppression.
 */
@Repository
public class IdempotencyCouchDbRepository {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCouchDbRepository.class);

    private final CouchDbOperations couchDb;
    private final CouchDbProperties properties;

    public IdempotencyCouchDbRepository(CouchDbOperations couchDb, CouchDbProperties properties) {
        this.couchDb = couchDb;
        this.properties = properties;
    }

    /**
     * Checks if this idempotency key has already been processed by the service.
     */
    public boolean isProcessed(IdempotencyKey key) {
        if (key == null) {
            return false;
        }
        return couchDb.getDocument(
                properties.getTradesDatabase(),
                key.toStorageKey(),
                IdempotencyDocument.class
        ).isPresent();
    }

    /**
     * Records that an idempotency key has been processed with the given status.
     */
    public void record(IdempotencyKey key, ProcessingStatus status) {
        if (key == null) {
            return;
        }
        IdempotencyDocument document = IdempotencyDocument.builder()
                .id(key.toStorageKey())
                .tradeId(key.getTradeId())
                .messageId(key.getMessageId())
                .schemaVersion(key.getSchemaVersion())
                .eventType(key.getEventType() != null ? key.getEventType().name() : "NEW_TRADE")
                .processingStatus(status != null ? status : ProcessingStatus.RECEIVED)
                .processedAt(Instant.now())
                .build();

        couchDb.putDocument(properties.getTradesDatabase(), key.toStorageKey(), document);
        log.debug("Recorded idempotency key: {}", key.toStorageKey());
    }
}
