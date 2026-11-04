package com.tcem.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.constants.TopicNames;
import com.tcem.ingestion.config.CouchDbProperties;
import com.tcem.ingestion.couchdb.InMemoryCouchDbOperations;
import com.tcem.ingestion.model.document.TradeDocument;
import com.tcem.ingestion.repository.TradeCouchDbRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradeLifecycleServiceTest {

    private static final String TRADE = "TRD-1";
    private static final String REPLAY_HEADER = "\"messageHeader\":{\"eventType\":\"REPLAY\",\"replayCount\":1}";

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private TradeCouchDbRepository repository;
    private TradeLifecycleService service;

    @BeforeEach
    void setUp() {
        CouchDbProperties props = new CouchDbProperties();
        props.setTradesDatabase("trades");
        repository = new TradeCouchDbRepository(new InMemoryCouchDbOperations(mapper), props);
        service = new TradeLifecycleService(repository, mapper);
        repository.save(TradeDocument.builder().id(TRADE).tradeId(TRADE).processingStatus(ProcessingStatus.ACCEPTED).build());
    }

    private ProcessingStatus status() {
        return repository.findById(TRADE).orElseThrow().getProcessingStatus();
    }

    private void event(String topic, String value) {
        service.onPipelineEvent(topic, TRADE, value);
    }

    @Test
    @DisplayName("each topic moves the trade to its status, in pipeline order")
    void happyPath() {
        event(TopicNames.TRADE_ENRICHED, "{\"trade\":{\"tradeId\":\"TRD-1\"}}");
        assertThat(status()).isEqualTo(ProcessingStatus.ENRICHED);
        event(TopicNames.STP_VALIDATED, "{\"stpPassed\":true}");
        assertThat(status()).isEqualTo(ProcessingStatus.STP_PASSED);
        event(TopicNames.CONFIRMATION_GENERATED, "{}");
        assertThat(status()).isEqualTo(ProcessingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("a failed trade reaches STP_FAILED, EXCEPTION_CAPTURED, then MANUAL_REVIEW")
    void failurePath() {
        event(TopicNames.STP_VALIDATED, "{\"stpPassed\":false}");
        assertThat(status()).isEqualTo(ProcessingStatus.STP_FAILED);
        event(TopicNames.EXCEPTION_CREATED, "{}");
        assertThat(status()).isEqualTo(ProcessingStatus.EXCEPTION_CAPTURED);
        event(TopicNames.MANUAL_REVIEW_REQUIRED, "{}");
        assertThat(status()).isEqualTo(ProcessingStatus.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("late events from other topics cannot move a status backwards")
    void outOfOrder() {
        event(TopicNames.CONFIRMATION_GENERATED, "{}");
        event(TopicNames.STP_VALIDATED, "{\"stpPassed\":true}");
        event(TopicNames.TRADE_ENRICHED, "{\"trade\":{\"tradeId\":\"TRD-1\"}}");
        event(TopicNames.DEADLETTER_TRADE, "{}");
        assertThat(status()).isEqualTo(ProcessingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("the enriched trade replaces the stored one, even when it arrives late")
    void enrichedTradeStored() {
        event(TopicNames.CONFIRMATION_GENERATED, "{}");
        event(TopicNames.TRADE_ENRICHED, "{\"trade\":{\"tradeId\":\"TRD-1\",\"parties\":[{\"partyId\":\"P\",\"legalEntityName\":\"Meridian\"}]}}");

        TradeDocument stored = repository.findById(TRADE).orElseThrow();
        assertThat(stored.getCanonicalTrade().getParties().get(0).getLegalEntityName()).isEqualTo("Meridian");
        assertThat(stored.getProcessingStatus()).isEqualTo(ProcessingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("a cancel wins over everything and is final")
    void cancelIsFinal() {
        event(TopicNames.CONFIRMATION_GENERATED, "{}");
        event(TopicNames.STP_VALIDATED, "{\"cancelled\":true}");
        assertThat(status()).isEqualTo(ProcessingStatus.CANCELLED);

        event(TopicNames.CONFIRMATION_GENERATED, "{" + REPLAY_HEADER + "}");
        assertThat(status()).isEqualTo(ProcessingStatus.CANCELLED);
    }

    @Test
    @DisplayName("only a replay brings a trade back from DEAD_LETTER")
    void deadLetterAndReplay() {
        event(TopicNames.DEADLETTER_TRADE, "{}");
        assertThat(status()).isEqualTo(ProcessingStatus.DEAD_LETTER);

        event(TopicNames.STP_VALIDATED, "{\"stpPassed\":true}");
        assertThat(status()).isEqualTo(ProcessingStatus.DEAD_LETTER);

        event(TopicNames.STP_VALIDATED, "{\"stpPassed\":true," + REPLAY_HEADER + "}");
        assertThat(status()).isEqualTo(ProcessingStatus.STP_PASSED);
    }

    @Test
    @DisplayName("a schema-invalid trade keeps its status when its own exception comes back")
    void schemaInvalidKept() {
        repository.save(TradeDocument.builder().id("TRD-2").tradeId("TRD-2").processingStatus(ProcessingStatus.SCHEMA_INVALID).build());

        service.onPipelineEvent(TopicNames.EXCEPTION_CREATED, "TRD-2", "{}");

        assertThat(repository.findById("TRD-2").orElseThrow().getProcessingStatus()).isEqualTo(ProcessingStatus.SCHEMA_INVALID);
    }

    @Test
    @DisplayName("unknown trades, placeholder keys and unparseable records are ignored")
    void ignored() {
        service.onPipelineEvent(TopicNames.CONFIRMATION_GENERATED, "TRD-NOPE", "{}");
        service.onPipelineEvent(TopicNames.DEADLETTER_TRADE, "UNKNOWN_KEY_1", "{}");
        service.onPipelineEvent(TopicNames.DEADLETTER_TRADE, null, "{}");
        event(TopicNames.CONFIRMATION_GENERATED, "{broken");

        assertThat(status()).isEqualTo(ProcessingStatus.ACCEPTED);
        assertThat(repository.findById("TRD-NOPE")).isEmpty();
    }
}
