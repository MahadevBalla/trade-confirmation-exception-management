package com.tcem.ingestion.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.ingestion.config.CouchDbProperties;
import com.tcem.ingestion.config.IngestionProperties;
import com.tcem.ingestion.consumer.TradeRawConsumer;
import com.tcem.ingestion.controller.TradeProducerController;
import com.tcem.ingestion.controller.TradeQueryController;
import com.tcem.ingestion.couchdb.InMemoryCouchDbOperations;
import com.tcem.ingestion.model.IngestionMetricsSummary;
import com.tcem.ingestion.model.TradeDetailResponse;
import com.tcem.ingestion.model.TradeListResponse;
import com.tcem.ingestion.generator.SyntheticTradeGenerator;
import com.tcem.ingestion.generator.TradeScenarioType;
import com.tcem.ingestion.model.document.AuditDocument;
import com.tcem.ingestion.model.document.TradeDocument;
import com.tcem.ingestion.producer.DeadLetterProducer;
import com.tcem.ingestion.producer.ExceptionCreatedProducer;
import com.tcem.ingestion.producer.TradeAcceptedProducer;
import com.tcem.ingestion.producer.TradeRawProducer;
import com.tcem.ingestion.repository.AuditCouchDbRepository;
import com.tcem.ingestion.repository.IdempotencyCouchDbRepository;
import com.tcem.ingestion.repository.TradeCouchDbRepository;
import com.tcem.ingestion.service.DefaultTradeIngestionService;
import com.tcem.ingestion.service.IngestionMetricsService;
import com.tcem.ingestion.service.SeedLoaderService;
import com.tcem.ingestion.validator.TradeValidator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test verifying the entire Ingestion Service pipeline (FR1, FR2, FR3, FR11, FR12):
 * - Inbound stream consumption (TradeRawConsumer)
 * - Validation engine (TradeValidator)
 * - Idempotency deduplication filter (IdempotencyCouchDbRepository)
 * - CouchDB persistence (TradeCouchDbRepository, AuditCouchDbRepository)
 * - Downstream Kafka dispatch (trade.accepted, exception.created, deadletter.trade)
 * - Operations REST API querying & metrics reporting (TradeQueryController)
 */
class TradeIngestionPipelineIntegrationTest {

    private ObjectMapper objectMapper;
    private InMemoryCouchDbOperations inMemoryCouchDb;
    private List<CapturedKafkaRecord> capturedRecords;

    private TradeValidator tradeValidator;
    private SyntheticTradeGenerator tradeGenerator;
    private IngestionMetricsService metricsService;
    private DefaultTradeIngestionService ingestionService;
    private TradeRawConsumer rawConsumer;
    private TradeQueryController queryController;
    private TradeProducerController producerController;

    private static class CapturedKafkaRecord {
        final String topic;
        final String key;
        final String payload;

        CapturedKafkaRecord(String topic, String key, String payload) {
            this.topic = topic;
            this.key = key;
            this.payload = payload;
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        inMemoryCouchDb = new InMemoryCouchDbOperations(objectMapper);
        capturedRecords = Collections.synchronizedList(new ArrayList<>());

        CouchDbProperties couchDbProps = new CouchDbProperties();
        couchDbProps.setTradesDatabase("trades");
        couchDbProps.setAuditEventsDatabase("audit-events");

        TradeCouchDbRepository tradeRepository = new TradeCouchDbRepository(inMemoryCouchDb, couchDbProps);
        AuditCouchDbRepository auditRepository = new AuditCouchDbRepository(inMemoryCouchDb, couchDbProps);
        IdempotencyCouchDbRepository idempotencyRepository = new IdempotencyCouchDbRepository(inMemoryCouchDb, couchDbProps);

        KafkaOperations<String, String> kafkaOperations = mock(KafkaOperations.class);
        when(kafkaOperations.send(any(String.class), any(), any())).thenAnswer(invocation -> {
            String topic = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            String payload = invocation.getArgument(2);
            capturedRecords.add(new CapturedKafkaRecord(topic, key, payload));
            RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0, 0, 0, 0, 0);
            return CompletableFuture.completedFuture(new SendResult<>(null, metadata));
        });

        TradeAcceptedProducer tradeAcceptedProducer = new TradeAcceptedProducer(kafkaOperations, objectMapper);
        ExceptionCreatedProducer exceptionCreatedProducer = new ExceptionCreatedProducer(kafkaOperations, objectMapper);
        DeadLetterProducer deadLetterProducer = new DeadLetterProducer(kafkaOperations, objectMapper);
        TradeRawProducer tradeRawProducer = new TradeRawProducer(kafkaOperations, objectMapper);

        tradeValidator = new TradeValidator(objectMapper);
        tradeGenerator = new SyntheticTradeGenerator(objectMapper);
        metricsService = new IngestionMetricsService();

        ingestionService = new DefaultTradeIngestionService(
                objectMapper,
                tradeValidator,
                tradeRepository,
                auditRepository,
                idempotencyRepository,
                tradeAcceptedProducer,
                exceptionCreatedProducer,
                deadLetterProducer,
                metricsService
        );

        rawConsumer = new TradeRawConsumer(ingestionService);
        queryController = new TradeQueryController(tradeRepository, auditRepository, metricsService);

        IngestionProperties ingestionProperties = new IngestionProperties();
        SeedLoaderService seedLoaderService = new SeedLoaderService(
                tradeGenerator,
                tradeRawProducer,
                ingestionProperties,
                objectMapper
        );
        producerController = new TradeProducerController(seedLoaderService);
    }

    @Test
    @DisplayName("End-to-End: Valid standard STP trade is consumed, validated, persisted in CouchDB, logged in audit, and published to trade.accepted")
    void testEndToEnd_ValidStandardStpTrade() throws Exception {
        String payload = tradeGenerator.generateTradeJson(TradeScenarioType.VALID_STANDARD_STP, 1);
        TradeEvent originalEvent = objectMapper.readValue(payload, TradeEvent.class);
        String expectedTradeId = originalEvent.getTrade().getTradeId();

        // 1. Simulating inbound event on trade.raw
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TopicNames.TRADE_RAW, 0, 100L, expectedTradeId, payload);
        rawConsumer.onMessage(record);

        // 2. Verify Kafka dispatch: Exactly 1 message on trade.accepted
        assertThat(capturedRecords).hasSize(1);
        CapturedKafkaRecord acceptedRecord = capturedRecords.get(0);
        assertThat(acceptedRecord.topic).isEqualTo(TopicNames.TRADE_ACCEPTED);
        assertThat(acceptedRecord.key).isEqualTo(expectedTradeId);

        TradeEvent acceptedEvent = objectMapper.readValue(acceptedRecord.payload, TradeEvent.class);
        assertThat(acceptedEvent.getTrade().getTradeId()).isEqualTo(expectedTradeId);
        assertThat(acceptedEvent.getTrade().getFxOptionEconomics().getStrike()).isNotNull();

        // 3. Verify CouchDB persistence in 'trades' database
        Optional<TradeDocument> tradeDocOpt = inMemoryCouchDb.getDocument("trades", expectedTradeId, TradeDocument.class);
        assertThat(tradeDocOpt).isPresent();
        TradeDocument tradeDoc = tradeDocOpt.get();
        assertThat(tradeDoc.getTradeId()).isEqualTo(expectedTradeId);
        assertThat(tradeDoc.getProcessingStatus()).isEqualTo(ProcessingStatus.ACCEPTED);
        assertThat(tradeDoc.getCanonicalTrade()).isNotNull();

        // 4. Verify CouchDB audit trail in 'audit-events' database
        List<AuditDocument> auditList = inMemoryCouchDb.find("audit-events", null, AuditDocument.class);
        assertThat(auditList).isNotEmpty();
        assertThat(auditList.get(0).getTradeId()).isEqualTo(expectedTradeId);
        assertThat(auditList.get(0).getStatus()).isEqualTo(ProcessingStatus.ACCEPTED);
        assertThat(auditList.get(0).getStage()).isEqualTo("SCHEMA_VALIDATION");

        // 5. Verify Operations REST API (GET /trades/{id})
        ResponseEntity<?> tradeResponse = queryController.getTradeById(expectedTradeId);
        assertThat(tradeResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(tradeResponse.getBody()).isInstanceOf(TradeDetailResponse.class);
        TradeDetailResponse detail = (TradeDetailResponse) tradeResponse.getBody();
        assertThat(detail.getTrade().getTradeId()).isEqualTo(expectedTradeId);
        assertThat(detail.getTrade().getProcessingStatus().name()).isEqualTo("ACCEPTED");
        assertThat(detail.getTrade().getCanonicalTrade().getParties()).hasSize(2);

        // 6. Verify Operations REST API (GET /trades?status=ACCEPTED)
        ResponseEntity<TradeListResponse> listResponse = queryController.listTrades("ACCEPTED", 0, 10);
        assertThat(listResponse.getStatusCode().is2xxSuccessful()).isTrue();
        TradeListResponse tradeList = listResponse.getBody();
        assertThat(tradeList).isNotNull();
        assertThat(tradeList.getTrades()).hasSize(1);
        assertThat(tradeList.getTrades().get(0).getTradeId()).isEqualTo(expectedTradeId);

        // 7. Verify Metrics Service
        IngestionMetricsSummary summary = metricsService.getSummary();
        assertThat(summary.getTotalAccepted()).isEqualTo(1L);
        assertThat(summary.getTotalSchemaInvalid()).isEqualTo(0L);
        assertThat(summary.getAcceptanceRatePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("End-to-End: Duplicate message is suppressed from trade.accepted and routed to exception.created with DUPLICATE_MESSAGE")
    void testEndToEnd_DuplicateTradeSuppressed() throws Exception {
        String payload = tradeGenerator.generateTradeJson(TradeScenarioType.VALID_STANDARD_STP, 2);
        TradeEvent event = objectMapper.readValue(payload, TradeEvent.class);
        String tradeId = event.getTrade().getTradeId();

        // First message: Accepted
        rawConsumer.onMessage(new ConsumerRecord<>(TopicNames.TRADE_RAW, 0, 201L, tradeId, payload));
        assertThat(capturedRecords).hasSize(1);
        assertThat(capturedRecords.get(0).topic).isEqualTo(TopicNames.TRADE_ACCEPTED);

        // Second message: Duplicate
        rawConsumer.onMessage(new ConsumerRecord<>(TopicNames.TRADE_RAW, 0, 202L, tradeId, payload));

        // Total Kafka records: 1 on trade.accepted + 1 on exception.created
        assertThat(capturedRecords).hasSize(2);
        CapturedKafkaRecord duplicateExceptionRecord = capturedRecords.get(1);
        assertThat(duplicateExceptionRecord.topic).isEqualTo(TopicNames.EXCEPTION_CREATED);
        assertThat(duplicateExceptionRecord.key).isEqualTo(tradeId);

        ExceptionEvent exceptionEvent = objectMapper.readValue(duplicateExceptionRecord.payload, ExceptionEvent.class);
        assertThat(exceptionEvent.getException().getSubTheme()).isEqualTo(ExceptionSubTheme.DUPLICATE_MESSAGE);
        assertThat(exceptionEvent.getException().getCategory()).isEqualTo(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE);

        // CouchDB trade document remains unchanged in ACCEPTED status
        TradeDocument doc = inMemoryCouchDb.getDocument("trades", tradeId, TradeDocument.class).orElseThrow();
        assertThat(doc.getProcessingStatus()).isEqualTo(ProcessingStatus.ACCEPTED);

        // Metrics verify duplicate counter
        assertThat(metricsService.getSummary().getTotalDuplicates()).isEqualTo(1L);
    }

    @Test
    @DisplayName("End-to-End: Schema validation failure saves SCHEMA_INVALID and dispatches ExceptionEvent to exception.created")
    void testEndToEnd_SchemaInvalidTradeDispatchesException() throws Exception {
        String payload = tradeGenerator.generateTradeJson(TradeScenarioType.MISSING_ECONOMIC_STRIKE, 3);
        TradeEvent event = objectMapper.readValue(payload, TradeEvent.class);
        String tradeId = event.getTrade().getTradeId();

        rawConsumer.onMessage(new ConsumerRecord<>(TopicNames.TRADE_RAW, 0, 301L, tradeId, payload));

        // Kafka: 0 on trade.accepted, 1 on exception.created
        assertThat(capturedRecords).hasSize(1);
        CapturedKafkaRecord excRecord = capturedRecords.get(0);
        assertThat(excRecord.topic).isEqualTo(TopicNames.EXCEPTION_CREATED);
        assertThat(excRecord.key).isEqualTo(tradeId);

        ExceptionEvent exceptionEvent = objectMapper.readValue(excRecord.payload, ExceptionEvent.class);
        assertThat(exceptionEvent.getException().getTradeId()).isEqualTo(tradeId);
        assertThat(exceptionEvent.getException().getCategory()).isEqualTo(ExceptionCategory.MISSING_ECONOMIC_DATA);
        assertThat(exceptionEvent.getException().getSubTheme()).isEqualTo(ExceptionSubTheme.MISSING_STRIKE);
        assertThat(exceptionEvent.getException().getOwnerQueue()).isEqualTo("DATA_REMEDIATION");
        assertThat(exceptionEvent.getException().isReplayMayResolve()).isTrue();

        // CouchDB trade saved as SCHEMA_INVALID
        TradeDocument doc = inMemoryCouchDb.getDocument("trades", tradeId, TradeDocument.class).orElseThrow();
        assertThat(doc.getProcessingStatus()).isEqualTo(ProcessingStatus.SCHEMA_INVALID);

        // REST Query API confirms SCHEMA_INVALID state
        ResponseEntity<?> detailResp = queryController.getTradeById(tradeId);
        TradeDetailResponse detail = (TradeDetailResponse) detailResp.getBody();
        assertThat(detail.getTrade().getProcessingStatus().name()).isEqualTo("SCHEMA_INVALID");
    }

    @Test
    @DisplayName("End-to-End: Corrupted malformed JSON routes to deadletter.trade with full diagnostics")
    void testEndToEnd_MalformedJsonRoutesToDeadLetter() throws Exception {
        String corruptJson = "{\"messageHeader\": { unquoted_broken_syntax }";
        String messageKey = "CORRUPTED-KEY-999";

        rawConsumer.onMessage(new ConsumerRecord<>(TopicNames.TRADE_RAW, 0, 401L, messageKey, corruptJson));

        // Kafka: exactly 1 message on deadletter.trade
        assertThat(capturedRecords).hasSize(1);
        CapturedKafkaRecord dlqRecord = capturedRecords.get(0);
        assertThat(dlqRecord.topic).isEqualTo(TopicNames.DEADLETTER_TRADE);
        assertThat(dlqRecord.key).isEqualTo(messageKey);

        DeadLetterEvent dlqEvent = objectMapper.readValue(dlqRecord.payload, DeadLetterEvent.class);
        assertThat(dlqEvent.getFailedOnTopic()).isEqualTo(TopicNames.TRADE_RAW);
        assertThat(dlqEvent.getFailedInService()).isEqualTo("ingestion-service");
        assertThat(dlqEvent.getFailureReason()).isNotNull();
        assertThat(dlqEvent.getOriginalPayload()).isEqualTo(corruptJson);

        // Metrics verify dead letter counter
        assertThat(metricsService.getSummary().getTotalDeadLetter()).isEqualTo(1L);
    }

    @Test
    @DisplayName("End-to-End: Full batch ingestion over all 10 scenario types updates STP rate and metrics summary accurately")
    void testEndToEnd_FullBatchScenarioIngestionAndMetrics() {
        int seq = 100;
        for (TradeScenarioType scenario : TradeScenarioType.values()) {
            String payload = tradeGenerator.generateTradeJson(scenario, ++seq);
            String key = "KEY-" + seq;
            rawConsumer.onMessage(new ConsumerRecord<>(TopicNames.TRADE_RAW, 0, (long) seq, key, payload));
        }

        // Total scenarios: 10
        IngestionMetricsSummary summary = queryController.getMetricsSummary().getBody();
        assertThat(summary).isNotNull();
        assertThat(summary.getTotalReceived()).isEqualTo(10L);
        assertThat(summary.getTotalAccepted()).isGreaterThanOrEqualTo(1L);
        assertThat(summary.getTotalSchemaInvalid()).isGreaterThanOrEqualTo(1L);
        assertThat(summary.getTotalDeadLetter()).isEqualTo(1L);
        assertThat(summary.getTotalAccepted() + summary.getTotalSchemaInvalid() + summary.getTotalDeadLetter()).isEqualTo(10L);

        double expectedAcceptanceRate = Math.round((double) summary.getTotalAccepted() / 10.0 * 10000.0) / 100.0;
        assertThat(summary.getAcceptanceRatePercent()).isEqualTo(expectedAcceptanceRate);

        long stpEvaluated = summary.getTotalAccepted() + summary.getTotalSchemaInvalid();
        double expectedStpRate = stpEvaluated > 0
                ? Math.round((double) summary.getTotalAccepted() / stpEvaluated * 10000.0) / 100.0
                : 100.0;
        ResponseEntity<Map<String, Object>> stpRateResp = queryController.getStpRate();
        assertThat(stpRateResp.getBody().get("stpRatePercent")).isEqualTo(expectedStpRate);
    }

    @Test
    @DisplayName("End-to-End: Seed loader endpoint safely previews sample-trades.json seed dataset")
    void testEndToEnd_SeedLoaderPreview() {
        ResponseEntity<Map<String, Object>> response = producerController.seedTrades(false);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("totalSeedTrades")).isEqualTo(500);
        assertThat(body.get("publishedToKafka")).isEqualTo(false);
    }
}
