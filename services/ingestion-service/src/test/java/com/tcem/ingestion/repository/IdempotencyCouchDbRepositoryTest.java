package com.tcem.ingestion.repository;

import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.idempotency.IdempotencyKey;
import com.tcem.ingestion.config.CouchDbProperties;
import com.tcem.ingestion.couchdb.CouchDbOperations;
import com.tcem.ingestion.model.document.IdempotencyDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyCouchDbRepositoryTest {

    @Mock
    private CouchDbOperations couchDb;

    private CouchDbProperties properties;
    private IdempotencyCouchDbRepository repository;

    @BeforeEach
    void setUp() {
        properties = new CouchDbProperties();
        properties.setTradesDatabase("trades");
        repository = new IdempotencyCouchDbRepository(couchDb, properties);
    }

    @Test
    void isProcessed_returnsTrue_whenDocumentExists() {
        IdempotencyKey key = IdempotencyKey.of("TRD-10001", "MSG-001", "capstone-fpml-json-1.0", EventType.NEW_TRADE);
        IdempotencyDocument doc = IdempotencyDocument.builder()
                .id(key.toStorageKey())
                .processingStatus(ProcessingStatus.ACCEPTED)
                .build();

        when(couchDb.getDocument("trades", key.toStorageKey(), IdempotencyDocument.class))
                .thenReturn(Optional.of(doc));

        boolean processed = repository.isProcessed(key);

        assertThat(processed).isTrue();
    }

    @Test
    void isProcessed_returnsFalse_whenDocumentMissing() {
        IdempotencyKey key = IdempotencyKey.of("TRD-10001", "MSG-001", "capstone-fpml-json-1.0", EventType.NEW_TRADE);

        when(couchDb.getDocument("trades", key.toStorageKey(), IdempotencyDocument.class))
                .thenReturn(Optional.empty());

        boolean processed = repository.isProcessed(key);

        assertThat(processed).isFalse();
    }

    @Test
    void record_persistsIdempotencyDocument() {
        IdempotencyKey key = IdempotencyKey.of("TRD-10001", "MSG-001", "capstone-fpml-json-1.0", EventType.NEW_TRADE);

        repository.record(key, ProcessingStatus.ACCEPTED);

        verify(couchDb).putDocument(eq("trades"), eq(key.toStorageKey()), any(IdempotencyDocument.class));
    }
}
