package com.tcem.exception.repository;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.exception.config.CouchDbProperties;
import com.tcem.exception.couchdb.InMemoryCouchDbOperations;
import com.tcem.exception.model.document.ExceptionDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExceptionCouchDbRepository using the InMemoryCouchDbOperations test double.
 */
class ExceptionCouchDbRepositoryTest {

    private ExceptionCouchDbRepository repository;
    private InMemoryCouchDbOperations inMemory;

    @BeforeEach
    void setUp() {
        inMemory = new InMemoryCouchDbOperations();
        CouchDbProperties props = new CouchDbProperties();
        repository = new ExceptionCouchDbRepository(inMemory, props);
        inMemory.ensureDatabaseExists(props.getExceptionsDatabase());
    }

    @Test
    void save_persistsDocumentAndSetsRev() {
        ExceptionDocument doc = ExceptionDocument.builder()
                .exceptionId("EXC-30001")
                .tradeId("TRD-10001")
                .category(ExceptionCategory.MISSING_ECONOMIC_DATA)
                .subTheme(ExceptionSubTheme.MISSING_STRIKE)
                .status("OPEN")
                .capturedAt(Instant.now())
                .build();

        ExceptionDocument saved = repository.save(doc);

        assertThat(saved.getId()).isEqualTo("EXC-30001");
        assertThat(saved.getRev()).isNotBlank();
        assertThat(saved.getClassifiedAt()).isNotNull();
    }

    @Test
    void findById_returnsDocument_whenPresent() {
        ExceptionDocument doc = ExceptionDocument.builder()
                .exceptionId("EXC-30001")
                .tradeId("TRD-10001")
                .category(ExceptionCategory.BUSINESS_RULE_FAILURE)
                .status("OPEN")
                .build();
        repository.save(doc);

        Optional<ExceptionDocument> result = repository.findById("EXC-30001");

        assertThat(result).isPresent();
        assertThat(result.get().getTradeId()).isEqualTo("TRD-10001");
        assertThat(result.get().getCategory()).isEqualTo(ExceptionCategory.BUSINESS_RULE_FAILURE);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        Optional<ExceptionDocument> result = repository.findById("EXC-99999");
        assertThat(result).isEmpty();
    }

    @Test
    void findByTradeId_returnsAllExceptionsForTrade() {
        ExceptionDocument doc1 = ExceptionDocument.builder()
                .exceptionId("EXC-30001").tradeId("TRD-10001")
                .category(ExceptionCategory.MISSING_ECONOMIC_DATA).status("OPEN").build();
        ExceptionDocument doc2 = ExceptionDocument.builder()
                .exceptionId("EXC-30002").tradeId("TRD-10001")
                .category(ExceptionCategory.TEMPLATE_ISSUE).status("OPEN").build();
        ExceptionDocument doc3 = ExceptionDocument.builder()
                .exceptionId("EXC-30003").tradeId("TRD-10002")
                .category(ExceptionCategory.BUSINESS_RULE_FAILURE).status("OPEN").build();
        repository.save(doc1);
        repository.save(doc2);
        repository.save(doc3);

        List<ExceptionDocument> results = repository.findByTradeId("TRD-10001");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ExceptionDocument::getTradeId)
                .containsOnly("TRD-10001");
    }

    @Test
    void findByStatus_filtersCorrectly() {
        ExceptionDocument open = ExceptionDocument.builder()
                .exceptionId("EXC-30001").tradeId("TRD-10001").status("OPEN").build();
        ExceptionDocument resolved = ExceptionDocument.builder()
                .exceptionId("EXC-30002").tradeId("TRD-10002").status("RESOLVED").build();
        repository.save(open);
        repository.save(resolved);

        List<ExceptionDocument> openDocs = repository.findByStatus("OPEN", 50, 0);
        assertThat(openDocs).hasSize(1);
        assertThat(openDocs.get(0).getStatus()).isEqualTo("OPEN");
    }

    @Test
    void findByCategory_filtersCorrectly() {
        ExceptionDocument missing = ExceptionDocument.builder()
                .exceptionId("EXC-30001").tradeId("TRD-10001")
                .category(ExceptionCategory.MISSING_ECONOMIC_DATA).status("OPEN").build();
        ExceptionDocument template = ExceptionDocument.builder()
                .exceptionId("EXC-30002").tradeId("TRD-10002")
                .category(ExceptionCategory.TEMPLATE_ISSUE).status("OPEN").build();
        repository.save(missing);
        repository.save(template);

        List<ExceptionDocument> results = repository.findByCategory(ExceptionCategory.MISSING_ECONOMIC_DATA, 50, 0);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCategory()).isEqualTo(ExceptionCategory.MISSING_ECONOMIC_DATA);
    }

    @Test
    void countAll_returnsCorrectTotal() {
        repository.save(ExceptionDocument.builder().exceptionId("EXC-30001").tradeId("T1").build());
        repository.save(ExceptionDocument.builder().exceptionId("EXC-30002").tradeId("T2").build());
        repository.save(ExceptionDocument.builder().exceptionId("EXC-30003").tradeId("T3").build());

        assertThat(repository.countAll()).isEqualTo(3);
    }

    @Test
    void countAll_returnsCorrectTotal_whenExceptionsExceedOneThousand() {
        for (int i = 1; i <= 1500; i++) {
            repository.save(ExceptionDocument.builder()
                    .exceptionId("EXC-" + i)
                    .tradeId("TRD-" + i)
                    .build());
        }

        assertThat(repository.countAll()).isEqualTo(1500);
    }

    @Test
    void countByStatus_returnsFilteredCount() {
        repository.save(ExceptionDocument.builder().exceptionId("EXC-30001").tradeId("T1").status("OPEN").build());
        repository.save(ExceptionDocument.builder().exceptionId("EXC-30002").tradeId("T2").status("OPEN").build());
        repository.save(ExceptionDocument.builder().exceptionId("EXC-30003").tradeId("T3").status("RESOLVED").build());

        assertThat(repository.countByStatus("OPEN")).isEqualTo(2);
        assertThat(repository.countByStatus("RESOLVED")).isEqualTo(1);
    }
}

