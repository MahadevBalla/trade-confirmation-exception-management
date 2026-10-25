package com.tcem.exception.repository;

import com.tcem.common.constants.ProcessingStatus;
import com.tcem.exception.config.CouchDbProperties;
import com.tcem.exception.couchdb.InMemoryCouchDbOperations;
import com.tcem.exception.model.document.AuditDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AuditCouchDbRepository using the InMemoryCouchDbOperations test double.
 */
class AuditCouchDbRepositoryTest {

    private AuditCouchDbRepository repository;
    private InMemoryCouchDbOperations inMemory;
    private CouchDbProperties props;

    @BeforeEach
    void setUp() {
        inMemory = new InMemoryCouchDbOperations();
        props = new CouchDbProperties();
        repository = new AuditCouchDbRepository(inMemory, props);
        inMemory.ensureDatabaseExists(props.getAuditEventsDatabase());
    }

    @Test
    void save_generatesTidAndTimestampWhenMissing() {
        AuditDocument doc = AuditDocument.builder()
                .tradeId("TRD-10001")
                .exceptionId("EXC-30001")
                .stage("EXCEPTION_CLASSIFICATION")
                .status(ProcessingStatus.EXCEPTION_CAPTURED)
                .details("Exception classified successfully")
                .build();

        AuditDocument saved = repository.save(doc);

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getId()).startsWith("audit:TRD-10001:");
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(saved.getRev()).isNotBlank();
    }

    @Test
    void save_preservesExplicitId() {
        AuditDocument doc = AuditDocument.builder()
                .id("audit:TRD-10001:custom")
                .tradeId("TRD-10001")
                .exceptionId("EXC-30001")
                .stage("CLASSIFICATION")
                .build();

        AuditDocument saved = repository.save(doc);

        assertThat(saved.getId()).isEqualTo("audit:TRD-10001:custom");
    }

    @Test
    void save_setsSourceService_asExceptionService() {
        AuditDocument doc = AuditDocument.builder()
                .tradeId("TRD-10001")
                .stage("EXCEPTION_CLASSIFICATION")
                .build();

        AuditDocument saved = repository.save(doc);

        assertThat(saved.getSourceService()).isEqualTo("exception-service");
    }

    @Test
    void findByTradeId_returnsMatchingAuditEntries() {
        AuditDocument doc1 = AuditDocument.builder()
                .tradeId("TRD-10001").exceptionId("EXC-30001")
                .stage("EXCEPTION_CAPTURED").build();
        AuditDocument doc2 = AuditDocument.builder()
                .tradeId("TRD-10001").exceptionId("EXC-30001")
                .stage("EXCEPTION_CLASSIFIED").build();
        AuditDocument doc3 = AuditDocument.builder()
                .tradeId("TRD-10002").exceptionId("EXC-30002")
                .stage("EXCEPTION_CAPTURED").build();

        repository.save(doc1);
        repository.save(doc2);
        repository.save(doc3);

        List<AuditDocument> results = repository.findByTradeId("TRD-10001");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(AuditDocument::getTradeId).containsOnly("TRD-10001");
    }

    @Test
    void findByTradeId_returnsEmpty_whenNoMatch() {
        List<AuditDocument> results = repository.findByTradeId("TRD-99999");
        assertThat(results).isEmpty();
    }

    @Test
    void findByExceptionId_returnsMatchingEntries() {
        AuditDocument doc1 = AuditDocument.builder()
                .tradeId("TRD-10001").exceptionId("EXC-30001")
                .stage("EXCEPTION_CAPTURED").build();
        AuditDocument doc2 = AuditDocument.builder()
                .tradeId("TRD-10001").exceptionId("EXC-30002")
                .stage("EXCEPTION_CAPTURED").build();

        repository.save(doc1);
        repository.save(doc2);

        List<AuditDocument> results = repository.findByExceptionId("EXC-30001");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExceptionId()).isEqualTo("EXC-30001");
    }

    @Test
    void findByExceptionId_returnsEmpty_whenNoMatch() {
        List<AuditDocument> results = repository.findByExceptionId("EXC-99999");
        assertThat(results).isEmpty();
    }

    @Test
    void multipleAuditEntriesForSameTrade_areStoredSeparately() {
        // Each save generates a unique ID, so same trade can have many audit events
        for (int i = 0; i < 5; i++) {
            repository.save(AuditDocument.builder()
                    .tradeId("TRD-10001")
                    .exceptionId("EXC-30001")
                    .stage("STAGE_" + i)
                    .build());
        }

        List<AuditDocument> results = repository.findByTradeId("TRD-10001");
        assertThat(results).hasSize(5);
    }
}

