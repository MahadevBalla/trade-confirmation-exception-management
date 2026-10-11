package com.tcem.ingestion.repository;

import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.constants.ProductType;
import com.tcem.ingestion.config.CouchDbProperties;
import com.tcem.ingestion.couchdb.CouchDbOperations;
import com.tcem.ingestion.couchdb.CouchDbResponse;
import com.tcem.ingestion.couchdb.FindQueryRequest;
import com.tcem.ingestion.model.document.TradeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeCouchDbRepositoryTest {

    @Mock
    private CouchDbOperations couchDb;

    private CouchDbProperties properties;
    private TradeCouchDbRepository repository;

    @BeforeEach
    void setUp() {
        properties = new CouchDbProperties();
        properties.setTradesDatabase("trades");
        repository = new TradeCouchDbRepository(couchDb, properties);
    }

    @Test
    void findById_returnsDocumentFromCouchDb() {
        TradeDocument doc = TradeDocument.builder()
                .id("TRD-10001")
                .tradeId("TRD-10001")
                .processingStatus(ProcessingStatus.ACCEPTED)
                .build();

        when(couchDb.getDocument("trades", "TRD-10001", TradeDocument.class))
                .thenReturn(Optional.of(doc));

        Optional<TradeDocument> result = repository.findById("TRD-10001");

        assertThat(result).isPresent();
        assertThat(result.get().getTradeId()).isEqualTo("TRD-10001");
        assertThat(result.get().getProcessingStatus()).isEqualTo(ProcessingStatus.ACCEPTED);
    }

    @Test
    void save_storesDocumentWithRev() {
        TradeDocument doc = TradeDocument.builder()
                .tradeId("TRD-10001")
                .productType(ProductType.FX_OPTION)
                .processingStatus(ProcessingStatus.RECEIVED)
                .build();

        CouchDbResponse response = CouchDbResponse.builder()
                .ok(true)
                .id("TRD-10001")
                .rev("1-abc")
                .build();

        when(couchDb.putDocument(eq("trades"), eq("TRD-10001"), any(TradeDocument.class)))
                .thenReturn(response);

        TradeDocument saved = repository.save(doc);

        assertThat(saved.getId()).isEqualTo("TRD-10001");
        assertThat(saved.getRev()).isEqualTo("1-abc");
        assertThat(saved.getReceivedTimestamp()).isNotNull();
        assertThat(saved.getUpdatedTimestamp()).isNotNull();
        verify(couchDb).putDocument(eq("trades"), eq("TRD-10001"), any(TradeDocument.class));
    }

    @Test
    void findByStatus_delegatesToCouchDbFind() {
        TradeDocument doc = TradeDocument.builder()
                .id("TRD-10001")
                .tradeId("TRD-10001")
                .processingStatus(ProcessingStatus.ACCEPTED)
                .build();

        when(couchDb.find(eq("trades"), any(FindQueryRequest.class), eq(TradeDocument.class)))
                .thenReturn(List.of(doc));

        List<TradeDocument> results = repository.findByStatus(ProcessingStatus.ACCEPTED, 10, 0);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTradeId()).isEqualTo("TRD-10001");
    }
}
