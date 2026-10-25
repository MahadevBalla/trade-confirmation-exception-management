package com.tcem.exception.couchdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.exception.model.document.ExceptionDocument;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InMemoryCouchDbOperations — the CouchDB test double used across all Phase 1 tests.
 */
class InMemoryCouchDbOperationsTest {

    private InMemoryCouchDbOperations inMemory;
    private static final String DB = "exceptions";

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        inMemory = new InMemoryCouchDbOperations(mapper);
    }

    @Test
    void isHealthy_returnsTrue_byDefault() {
        assertThat(inMemory.isHealthy()).isTrue();
    }

    @Test
    void isHealthy_returnsFalse_whenSetUnhealthy() {
        inMemory.setHealthy(false);
        assertThat(inMemory.isHealthy()).isFalse();
    }

    @Test
    void ensureDatabaseExists_createsDatabase() {
        boolean result = inMemory.ensureDatabaseExists(DB);
        assertThat(result).isTrue();
    }

    @Test
    void putAndGetDocument_roundtrip_succeeds() {
        inMemory.ensureDatabaseExists(DB);
        ExceptionDocument doc = ExceptionDocument.builder()
                .id("EXC-30001")
                .exceptionId("EXC-30001")
                .tradeId("TRD-10001")
                .category(ExceptionCategory.MISSING_ECONOMIC_DATA)
                .subTheme(ExceptionSubTheme.MISSING_STRIKE)
                .status("OPEN")
                .capturedAt(Instant.now())
                .build();

        CouchDbResponse response = inMemory.putDocument(DB, "EXC-30001", doc);
        assertThat(response.getOk()).isTrue();
        assertThat(response.getId()).isEqualTo("EXC-30001");
        assertThat(response.getRev()).isNotBlank();

        Optional<ExceptionDocument> retrieved = inMemory.getDocument(DB, "EXC-30001", ExceptionDocument.class);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getTradeId()).isEqualTo("TRD-10001");
        assertThat(retrieved.get().getCategory()).isEqualTo(ExceptionCategory.MISSING_ECONOMIC_DATA);
    }

    @Test
    void getDocument_returnsEmpty_whenNotFound() {
        inMemory.ensureDatabaseExists(DB);
        Optional<ExceptionDocument> result = inMemory.getDocument(DB, "nonexistent", ExceptionDocument.class);
        assertThat(result).isEmpty();
    }

    @Test
    void getDocument_returnsEmpty_whenDatabaseNotFound() {
        Optional<ExceptionDocument> result = inMemory.getDocument("unknown-db", "anyId", ExceptionDocument.class);
        assertThat(result).isEmpty();
    }

    @Test
    void find_matchesBySelector() {
        inMemory.ensureDatabaseExists(DB);
        ExceptionDocument doc1 = ExceptionDocument.builder()
                .id("EXC-30001").exceptionId("EXC-30001")
                .tradeId("TRD-10001").status("OPEN").build();
        ExceptionDocument doc2 = ExceptionDocument.builder()
                .id("EXC-30002").exceptionId("EXC-30002")
                .tradeId("TRD-10002").status("RESOLVED").build();
        inMemory.putDocument(DB, "EXC-30001", doc1);
        inMemory.putDocument(DB, "EXC-30002", doc2);

        FindQueryRequest query = FindQueryRequest.builder()
                .selector(Map.of("docType", "EXCEPTION", "status", "OPEN"))
                .limit(50)
                .build();

        List<ExceptionDocument> results = inMemory.find(DB, query, ExceptionDocument.class);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExceptionId()).isEqualTo("EXC-30001");
    }

    @Test
    void find_returnsEmpty_whenNoMatch() {
        inMemory.ensureDatabaseExists(DB);
        FindQueryRequest query = FindQueryRequest.builder()
                .selector(Map.of("docType", "EXCEPTION", "status", "CLOSED"))
                .build();
        List<ExceptionDocument> results = inMemory.find(DB, query, ExceptionDocument.class);
        assertThat(results).isEmpty();
    }

    @Test
    void find_returnsEmpty_whenDatabaseAbsent() {
        FindQueryRequest query = FindQueryRequest.builder()
                .selector(Map.of("docType", "EXCEPTION"))
                .build();
        List<ExceptionDocument> results = inMemory.find("unknown-db", query, ExceptionDocument.class);
        assertThat(results).isEmpty();
    }

    @Test
    void putDocument_overwritesExistingDocument() {
        inMemory.ensureDatabaseExists(DB);
        ExceptionDocument v1 = ExceptionDocument.builder()
                .id("EXC-30001").exceptionId("EXC-30001").tradeId("TRD-10001").status("OPEN").build();
        ExceptionDocument v2 = ExceptionDocument.builder()
                .id("EXC-30001").exceptionId("EXC-30001").tradeId("TRD-10001").status("RESOLVED").build();

        inMemory.putDocument(DB, "EXC-30001", v1);
        inMemory.putDocument(DB, "EXC-30001", v2);

        Optional<ExceptionDocument> retrieved = inMemory.getDocument(DB, "EXC-30001", ExceptionDocument.class);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus()).isEqualTo("RESOLVED");
    }

    @Test
    void clear_removesAllDatabases() {
        inMemory.ensureDatabaseExists(DB);
        inMemory.putDocument(DB, "EXC-30001",
                ExceptionDocument.builder().id("EXC-30001").exceptionId("EXC-30001").build());
        inMemory.clear();

        Optional<ExceptionDocument> result = inMemory.getDocument(DB, "EXC-30001", ExceptionDocument.class);
        assertThat(result).isEmpty();
    }
}

