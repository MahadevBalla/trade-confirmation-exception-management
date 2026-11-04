package com.tcem.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.ingestion.config.CouchDbProperties;
import com.tcem.ingestion.config.IngestionProperties;
import com.tcem.ingestion.couchdb.InMemoryCouchDbOperations;
import com.tcem.ingestion.generator.SyntheticTradeGenerator;
import com.tcem.ingestion.model.document.TradeDocument;
import com.tcem.ingestion.producer.TradeRawProducer;
import com.tcem.ingestion.repository.TradeCouchDbRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SeedLoaderServiceTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final TradeRawProducer producer = mock(TradeRawProducer.class);
    private TradeCouchDbRepository repository;
    private SeedLoaderService service;

    @BeforeEach
    void setUp() {
        CouchDbProperties props = new CouchDbProperties();
        props.setTradesDatabase("trades");
        repository = new TradeCouchDbRepository(new InMemoryCouchDbOperations(mapper), props);
        service = new SeedLoaderService(new SyntheticTradeGenerator(mapper), producer, repository, new IngestionProperties(), mapper);
    }

    @Test
    @DisplayName("the first seed publishes the 500 trades")
    void firstSeedPublishes() {
        service.loadSeedData(true, false);

        verify(producer).sendBatch(anyList());
    }

    @Test
    @DisplayName("a second seed is refused and publishes nothing, including a double click before any trade is stored")
    void secondSeedRefused() {
        service.loadSeedData(true, false);

        assertThatThrownBy(() -> service.loadSeedData(true, false)).isInstanceOf(SeedAlreadyLoadedException.class);
        verify(producer, times(1)).sendBatch(anyList());
    }

    @Test
    @DisplayName("a seed is refused when its trades are already stored, as after a restart")
    void refusedWhenTradesExist() {
        repository.save(TradeDocument.builder().id("TRD-000001").tradeId("TRD-000001").build());

        assertThatThrownBy(() -> service.loadSeedData(true, false)).isInstanceOf(SeedAlreadyLoadedException.class);
        verify(producer, never()).sendBatch(anyList());
    }

    @Test
    @DisplayName("other trades don't block the seed")
    void otherTradesDontBlock() {
        repository.save(TradeDocument.builder().id("TRD-123456").tradeId("TRD-123456").build());

        service.loadSeedData(true, false);

        verify(producer).sendBatch(anyList());
    }

    @Test
    @DisplayName("force publishes anyway")
    void forceOverrides() {
        repository.save(TradeDocument.builder().id("TRD-000001").tradeId("TRD-000001").build());

        service.loadSeedData(true, true);
        service.loadSeedData(true, true);

        verify(producer, times(2)).sendBatch(anyList());
    }

    @Test
    @DisplayName("a preview that publishes nothing is never refused, and doesn't count as the seed")
    void previewNotGuarded() {
        service.loadSeedData(false, false);
        service.loadSeedData(false, false);
        verify(producer, never()).sendBatch(anyList());

        service.loadSeedData(true, false);
        verify(producer).sendBatch(anyList());
    }

    @Test
    @DisplayName("a seed that fails to publish can be tried again")
    void failedSeedCanRetry() {
        doThrow(new IllegalStateException("broker down")).when(producer).sendBatch(anyList());
        assertThatThrownBy(() -> service.loadSeedData(true, false)).isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.reset(producer);

        service.loadSeedData(true, false);

        verify(producer).sendBatch(anyList());
    }

    @SuppressWarnings("unchecked")
    private Set<String> publishedTradeIds() throws Exception {
        ArgumentCaptor<List<String>> batches = ArgumentCaptor.forClass(List.class);
        verify(producer, org.mockito.Mockito.atLeastOnce()).sendBatch(batches.capture());
        Set<String> ids = new HashSet<>();
        for (List<String> batch : batches.getAllValues()) {
            for (String payload : batch) {
                JsonNode id = mapper.readTree(payload).path("trade").path("tradeId");
                ids.add(id.asText());
            }
        }
        return ids;
    }

    @Test
    @DisplayName("published batches get new trade ids that don't repeat the seed or each other")
    void publishedBatchesAreNew() throws Exception {
        service.generateAndPublish(5, true);
        service.generateAndPublish(5, true);

        Set<String> ids = publishedTradeIds();
        assertThat(ids).hasSize(10).doesNotContain("TRD-000001", "TRD-000002", "TRD-000003", "TRD-000004", "TRD-000005");
    }

    @Test
    @DisplayName("an unpublished preview keeps its predictable ids")
    void previewKeepsIds() {
        assertThat(service.generateAndPublish(1, false).get("sampleTrade").toString()).contains("TRD-000001");
    }
}
