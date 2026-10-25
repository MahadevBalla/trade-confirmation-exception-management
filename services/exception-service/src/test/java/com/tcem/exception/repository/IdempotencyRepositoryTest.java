package com.tcem.exception.repository;

import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.idempotency.IdempotencyKey;
import com.tcem.common.constants.EventType;
import com.tcem.exception.config.CouchDbProperties;
import com.tcem.exception.couchdb.InMemoryCouchDbOperations;
import com.tcem.exception.model.document.IdempotencyDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for IdempotencyRepository using the InMemoryCouchDbOperations test double.
 */
class IdempotencyRepositoryTest {

    private IdempotencyRepository repository;
    private InMemoryCouchDbOperations inMemory;
    private CouchDbProperties props;

    @BeforeEach
    void setUp() {
        inMemory = new InMemoryCouchDbOperations();
        props = new CouchDbProperties();
        repository = new IdempotencyRepository(inMemory, props);
        inMemory.ensureDatabaseExists(props.getExceptionsDatabase());
    }

    @Test
    void isProcessed_returnsFalse_whenKeyNotRecorded() {
        IdempotencyKey key = IdempotencyKey.of("TRD-10001", "MSG-001", "capstone-fpml-json-1.0", EventType.NEW_TRADE);
        assertThat(repository.isProcessed(key)).isFalse();
    }

    @Test
    void isProcessed_returnsTrue_afterRecording() {
        IdempotencyKey key = IdempotencyKey.of("TRD-10001", "MSG-001", "capstone-fpml-json-1.0", EventType.NEW_TRADE);
        repository.record(key, "CLASSIFIED");
        assertThat(repository.isProcessed(key)).isTrue();
    }

    @Test
    void isProcessed_returnsFalse_forNullKey() {
        assertThat(repository.isProcessed(null)).isFalse();
    }

    @Test
    void record_storesCorrectFields() {
        IdempotencyKey key = IdempotencyKey.of("TRD-10002", "MSG-002", "capstone-fpml-json-1.0", EventType.AMEND_TRADE);
        repository.record(key, "MANUAL_REVIEW");

        Optional<IdempotencyDocument> stored = inMemory.getDocument(
                props.getExceptionsDatabase(),
                key.toStorageKey(),
                IdempotencyDocument.class
        );
        assertThat(stored).isPresent();
        assertThat(stored.get().getTradeId()).isEqualTo("TRD-10002");
        assertThat(stored.get().getMessageId()).isEqualTo("MSG-002");
        assertThat(stored.get().getEventType()).isEqualTo("AMEND_TRADE");
        assertThat(stored.get().getProcessingResult()).isEqualTo("MANUAL_REVIEW");
        assertThat(stored.get().getProcessedAt()).isNotNull();
    }

    @Test
    void record_doesNothing_forNullKey() {
        // Should not throw
        repository.record(null, "CLASSIFIED");
    }

    @Test
    void differentKeys_areIndependent() {
        IdempotencyKey key1 = IdempotencyKey.of("TRD-10001", "MSG-001", "capstone-fpml-json-1.0", EventType.NEW_TRADE);
        IdempotencyKey key2 = IdempotencyKey.of("TRD-10002", "MSG-002", "capstone-fpml-json-1.0", EventType.NEW_TRADE);

        repository.record(key1, "CLASSIFIED");

        assertThat(repository.isProcessed(key1)).isTrue();
        assertThat(repository.isProcessed(key2)).isFalse();
    }
}

