package com.tcem.exception.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.exception.classifier.ClassificationRulesEngine;
import com.tcem.exception.config.CouchDbProperties;
import com.tcem.exception.consumer.ExceptionCreatedConsumer;
import com.tcem.exception.controller.ExceptionQueryController;
import com.tcem.exception.couchdb.InMemoryCouchDbOperations;
import com.tcem.exception.model.ExceptionMetricsSummary;
import com.tcem.exception.model.ExceptionProcessingResult;
import com.tcem.exception.model.document.ExceptionDocument;
import com.tcem.exception.model.document.AuditDocument;
import com.tcem.exception.producer.DeadLetterProducer;
import com.tcem.exception.producer.ExceptionClassifiedProducer;
import com.tcem.exception.producer.ManualReviewProducer;
import com.tcem.exception.recommendation.RecommendationEngine;
import com.tcem.exception.recommendation.SlaCalculator;
import com.tcem.exception.repository.AuditCouchDbRepository;
import com.tcem.exception.repository.ExceptionCouchDbRepository;
import com.tcem.exception.repository.IdempotencyRepository;
import com.tcem.exception.service.DefaultExceptionProcessingService;
import com.tcem.exception.service.ExceptionIdGenerator;
import com.tcem.exception.service.ExceptionMetricsService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
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
 * End-to-end integration test verifying the entire Exception Service pipeline (FR7, FR8, FR9, FR11, FR12).
 *
 * <p>Uses {@link InMemoryCouchDbOperations} as a full in-memory CouchDB double and a
 * capturing mock {@link KafkaOperations} to intercept all outbound Kafka messages.
 * No external infrastructure (CouchDB, Kafka broker) is required.
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>Happy path — valid exception classified and published to {@code exception.classified}</li>
 *   <li>Manual review routing — {@code COUNTERPARTY_MATCHING_DISPUTE} category → {@code manual.review.required}</li>
 *   <li>Idempotency duplicate suppression — same message re-published → only one output</li>
 *   <li>REPLAY bypass — {@code eventType=REPLAY} re-classified even if idempotency key exists</li>
 *   <li>Malformed JSON → dead-letter event on {@code deadletter.trade}</li>
 *   <li>Null payload → dead-letter event on {@code deadletter.trade}</li>
 *   <li>ExceptionId preservation — inbound exceptionId carried through to output</li>
 *   <li>REST API — GET /exceptions/{id} returns persisted document after processing</li>
 *   <li>REST API — GET /exceptions/metrics/summary reflects accurate counters</li>
 * </ol>
 */
class ExceptionServicePipelineIntegrationTest {

    private ObjectMapper objectMapper;
    private InMemoryCouchDbOperations inMemoryCouchDb;
    private List<CapturedKafkaRecord> capturedRecords;

    private ExceptionMetricsService metricsService;
    private DefaultExceptionProcessingService processingService;
    private ExceptionCreatedConsumer consumer;
    private ExceptionQueryController queryController;

    // -------------------------------------------------------------------------
    // Helper: captured Kafka record
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        inMemoryCouchDb = new InMemoryCouchDbOperations(objectMapper);
        capturedRecords = Collections.synchronizedList(new ArrayList<>());

        // CouchDB repositories backed by in-memory double
        CouchDbProperties props = new CouchDbProperties();
        props.setExceptionsDatabase("exceptions");
        props.setAuditEventsDatabase("audit-events");

        ExceptionCouchDbRepository exceptionRepository = new ExceptionCouchDbRepository(inMemoryCouchDb, props);
        AuditCouchDbRepository auditRepository         = new AuditCouchDbRepository(inMemoryCouchDb, props);
        IdempotencyRepository idempotencyRepository    = new IdempotencyRepository(inMemoryCouchDb, props);

        // Capturing mock KafkaOperations — records every send call
        KafkaOperations<String, String> kafkaOperations = mock(KafkaOperations.class);
        when(kafkaOperations.send(any(String.class), any(), any())).thenAnswer(invocation -> {
            String topic   = invocation.getArgument(0);
            String key     = invocation.getArgument(1);
            String payload = invocation.getArgument(2);
            capturedRecords.add(new CapturedKafkaRecord(topic, key, payload));
            RecordMetadata meta = new RecordMetadata(new TopicPartition(topic, 0), 0, 0, 0, 0, 0);
            return CompletableFuture.completedFuture(new SendResult<>(null, meta));
        });

        // Producers
        ExceptionClassifiedProducer classifiedProducer   = new ExceptionClassifiedProducer(kafkaOperations, objectMapper);
        ManualReviewProducer manualReviewProducer         = new ManualReviewProducer(kafkaOperations, objectMapper);
        DeadLetterProducer deadLetterProducer             = new DeadLetterProducer(kafkaOperations, objectMapper);

        // Processing components
        metricsService = new ExceptionMetricsService();

        processingService = new DefaultExceptionProcessingService(
                objectMapper,
                new ClassificationRulesEngine(),
                new RecommendationEngine(),
                new SlaCalculator(),
                new ExceptionIdGenerator(),
                exceptionRepository,
                auditRepository,
                idempotencyRepository,
                classifiedProducer,
                manualReviewProducer,
                deadLetterProducer,
                metricsService
        );

        consumer       = new ExceptionCreatedConsumer(processingService);
        queryController = new ExceptionQueryController(exceptionRepository, auditRepository, metricsService);
    }

    // -------------------------------------------------------------------------
    // Test 1: Happy path — CLASSIFIED
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("E2E: Valid MISSING_ECONOMIC_DATA exception is classified (confidence 1.0) and published to exception.classified")
    void happyPath_validException_publishedToExceptionClassified() throws Exception {
        String tradeId = "TRD-E2E-001";
        String payload = buildPayload(tradeId, ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_STRIKE, EventType.NEW_TRADE, null);

        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 1L, tradeId, payload));

        // Exactly 1 message on exception.classified
        assertThat(capturedRecords).hasSize(1);
        CapturedKafkaRecord record = capturedRecords.get(0);
        assertThat(record.topic).isEqualTo(TopicNames.EXCEPTION_CLASSIFIED);
        assertThat(record.key).isEqualTo(tradeId);

        // Outbound payload is a valid ExceptionEvent
        ExceptionEvent outbound = objectMapper.readValue(record.payload, ExceptionEvent.class);
        assertThat(outbound.getException().getTradeId()).isEqualTo(tradeId);
        assertThat(outbound.getException().getCategory()).isEqualTo(ExceptionCategory.MISSING_ECONOMIC_DATA);
        assertThat(outbound.getException().getClassificationConfidence()).isEqualTo(1.0);
        assertThat(outbound.getException().getOwnerQueue()).isEqualTo("DATA_REMEDIATION");
        assertThat(outbound.getException().getSlaDeadline()).isNotNull();
        assertThat(outbound.getException().getExceptionId()).startsWith("EXC-");

        // CouchDB: ExceptionDocument persisted
        List<ExceptionDocument> docs = inMemoryCouchDb.find("exceptions", null, ExceptionDocument.class);
        Optional<ExceptionDocument> excDoc = docs.stream()
                .filter(d -> tradeId.equals(d.getTradeId())).findFirst();
        assertThat(excDoc).isPresent();
        assertThat(excDoc.get().getClassificationConfidence()).isEqualTo(1.0);
        assertThat(excDoc.get().getStatus()).isEqualTo("OPEN");

        // CouchDB: Audit trail has at least one CLASSIFICATION entry
        List<AuditDocument> auditDocs = inMemoryCouchDb.find("audit-events", null, AuditDocument.class);
        assertThat(auditDocs).isNotEmpty();
        assertThat(auditDocs.stream().anyMatch(a -> "CLASSIFICATION".equals(a.getStage()))).isTrue();

        // Metrics reflect the result
        assertThat(metricsService.getSummary().getTotalReceived()).isEqualTo(1L);
        assertThat(metricsService.getSummary().getTotalClassified()).isEqualTo(1L);
        assertThat(metricsService.getSummary().getTotalManualReview()).isZero();
        assertThat(metricsService.getSummary().getClassificationRatePercent()).isEqualTo(100.0);
    }

    // -------------------------------------------------------------------------
    // Test 2: Manual review routing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("E2E: COUNTERPARTY_MATCHING_DISPUTE is routed to manual.review.required (always manual)")
    void counterpartyDispute_routedToManualReview() throws Exception {
        String tradeId = "TRD-E2E-002";
        String payload = buildPayload(tradeId, ExceptionCategory.COUNTERPARTY_MATCHING_DISPUTE,
                ExceptionSubTheme.COUNTERPARTY_ID_MISMATCH, EventType.NEW_TRADE, null);

        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 2L, tradeId, payload));

        // Exactly 1 message on manual.review.required
        assertThat(capturedRecords).hasSize(1);
        CapturedKafkaRecord record = capturedRecords.get(0);
        assertThat(record.topic).isEqualTo(TopicNames.MANUAL_REVIEW_REQUIRED);
        assertThat(record.key).isEqualTo(tradeId);

        ExceptionEvent outbound = objectMapper.readValue(record.payload, ExceptionEvent.class);
        assertThat(outbound.getException().getCategory()).isEqualTo(ExceptionCategory.COUNTERPARTY_MATCHING_DISPUTE);
        assertThat(outbound.getException().getClassificationConfidence()).isLessThan(0.6);

        // Metrics
        assertThat(metricsService.getSummary().getTotalManualReview()).isEqualTo(1L);
        assertThat(metricsService.getSummary().getTotalClassified()).isZero();
    }

    // -------------------------------------------------------------------------
    // Test 3: Idempotency duplicate suppression
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("E2E: Duplicate message (same tradeId + messageId) is suppressed — no second Kafka output")
    void duplicateMessage_suppressedByIdempotency() throws Exception {
        String tradeId = "TRD-E2E-003";
        String payload = buildPayload(tradeId, ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_NOTIONAL, EventType.NEW_TRADE, "FIXED-MSG-ID-003");

        // First delivery — should be classified
        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 3L, tradeId, payload));
        assertThat(capturedRecords).hasSize(1);
        assertThat(capturedRecords.get(0).topic).isEqualTo(TopicNames.EXCEPTION_CLASSIFIED);

        // Second delivery (same payload = same idempotency key) — should be suppressed
        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 4L, tradeId, payload));

        // Still only 1 message — duplicate was suppressed
        assertThat(capturedRecords).hasSize(1);

        // Metrics: 2 received, 1 classified, 1 duplicate
        assertThat(metricsService.getSummary().getTotalReceived()).isEqualTo(2L);
        assertThat(metricsService.getSummary().getTotalClassified()).isEqualTo(1L);
        assertThat(metricsService.getSummary().getTotalDuplicates()).isEqualTo(1L);
    }

    // -------------------------------------------------------------------------
    // Test 4: REPLAY bypass
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("E2E: REPLAY eventType bypasses idempotency — re-classified even if key already exists")
    void replayEvent_bypassesIdempotency_andIsReclassified() throws Exception {
        String tradeId = "TRD-E2E-004";
        // Same fixed message ID for both messages
        String payload = buildPayload(tradeId, ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_STRIKE, EventType.NEW_TRADE, "FIXED-MSG-ID-004");

        // First delivery — normal NEW_TRADE → classified + idempotency recorded
        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 5L, tradeId, payload));
        assertThat(capturedRecords).hasSize(1);
        assertThat(capturedRecords.get(0).topic).isEqualTo(TopicNames.EXCEPTION_CLASSIFIED);

        // Second delivery — REPLAY eventType → bypasses idempotency → also classified
        String replayPayload = buildPayload(tradeId, ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_STRIKE, EventType.REPLAY, "FIXED-MSG-ID-004");
        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 6L, tradeId, replayPayload));

        // 2 messages on exception.classified (both classified)
        assertThat(capturedRecords).hasSize(2);
        assertThat(capturedRecords.get(1).topic).isEqualTo(TopicNames.EXCEPTION_CLASSIFIED);

        // Metrics: 2 received, 2 classified, 0 duplicates
        assertThat(metricsService.getSummary().getTotalReceived()).isEqualTo(2L);
        assertThat(metricsService.getSummary().getTotalClassified()).isEqualTo(2L);
        assertThat(metricsService.getSummary().getTotalDuplicates()).isZero();
    }

    // -------------------------------------------------------------------------
    // Test 5: Malformed JSON → DLQ
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("E2E: Malformed JSON payload is routed to deadletter.trade with full diagnostics")
    void malformedJson_routedToDeadLetter() throws Exception {
        String corruptJson = "{\"messageHeader\": { unquoted_key }}";
        String key = "BAD-MSG-KEY-001";

        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 7L, key, corruptJson));

        // Exactly 1 message on deadletter.trade
        assertThat(capturedRecords).hasSize(1);
        CapturedKafkaRecord dlqRecord = capturedRecords.get(0);
        assertThat(dlqRecord.topic).isEqualTo(TopicNames.DEADLETTER_TRADE);
        assertThat(dlqRecord.key).isEqualTo(key);

        DeadLetterEvent dlqEvent = objectMapper.readValue(dlqRecord.payload, DeadLetterEvent.class);
        assertThat(dlqEvent.getFailedOnTopic()).isEqualTo(TopicNames.EXCEPTION_CREATED);
        assertThat(dlqEvent.getFailedInService()).isEqualTo("exception-service");
        assertThat(dlqEvent.getFailureReason()).contains("Malformed JSON");
        assertThat(dlqEvent.getOriginalPayload()).isEqualTo(corruptJson);

        // Metrics
        assertThat(metricsService.getSummary().getTotalDeadLetter()).isEqualTo(1L);
        assertThat(metricsService.getSummary().getTotalClassified()).isZero();
    }

    // -------------------------------------------------------------------------
    // Test 6: Null payload → DLQ
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("E2E: Null payload is routed to deadletter.trade")
    void nullPayload_routedToDeadLetter() {
        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 8L, "NULL-KEY", null));

        assertThat(capturedRecords).hasSize(1);
        assertThat(capturedRecords.get(0).topic).isEqualTo(TopicNames.DEADLETTER_TRADE);
        assertThat(metricsService.getSummary().getTotalDeadLetter()).isEqualTo(1L);
    }

    // -------------------------------------------------------------------------
    // Test 7: ExceptionId preservation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("E2E: Inbound exceptionId is preserved in outbound exception.classified event")
    void inboundExceptionId_preservedInOutput() throws Exception {
        String tradeId     = "TRD-E2E-007";
        String existingId  = "EXC-PRESERVED-999";
        String payload     = buildPayloadWithExceptionId(tradeId, ExceptionCategory.TEMPLATE_ISSUE,
                ExceptionSubTheme.NO_TEMPLATE_FOUND, EventType.NEW_TRADE, existingId);

        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 9L, tradeId, payload));

        assertThat(capturedRecords).hasSize(1);
        assertThat(capturedRecords.get(0).topic).isEqualTo(TopicNames.EXCEPTION_CLASSIFIED);

        ExceptionEvent outbound = objectMapper.readValue(capturedRecords.get(0).payload, ExceptionEvent.class);
        assertThat(outbound.getException().getExceptionId()).isEqualTo(existingId);
    }

    // -------------------------------------------------------------------------
    // Test 8: REST API — GET /exceptions/{id} after processing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("E2E: GET /exceptions/{id} returns persisted ExceptionDocument after pipeline processing")
    void restApi_getById_returnsPersistedDocument() throws Exception {
        String tradeId    = "TRD-E2E-008";
        String exceptionId = "EXC-API-TEST-001";
        String payload     = buildPayloadWithExceptionId(tradeId, ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_STRIKE, EventType.NEW_TRADE, exceptionId);

        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 10L, tradeId, payload));

        // REST API should return the persisted document
        ResponseEntity<?> response = queryController.getExceptionById(exceptionId);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Test 9: REST API — metrics summary reflects all processed outcomes
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("E2E: GET /exceptions/metrics/summary reflects accurate totals after mixed-outcome processing")
    void restApi_metricsSummary_reflectsAllOutcomes() throws Exception {
        // 1 classified
        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 11L, "TRD-M1",
                buildPayload("TRD-M1", ExceptionCategory.MISSING_ECONOMIC_DATA,
                        ExceptionSubTheme.MISSING_STRIKE, EventType.NEW_TRADE, "MSG-M1")));

        // 1 manual review (null category → confidence 0.3)
        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 12L, "TRD-M2",
                buildPayload("TRD-M2", null, null, EventType.NEW_TRADE, "MSG-M2")));

        // 1 dead letter
        consumer.onMessage(new ConsumerRecord<>(TopicNames.EXCEPTION_CREATED, 0, 13L, "TRD-M3",
                "{invalid json}"));

        // REST API metrics
        ResponseEntity<ExceptionMetricsSummary> response = queryController.getMetricsSummary();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        ExceptionMetricsSummary summary = response.getBody();
        assertThat(summary).isNotNull();
        assertThat(summary.getTotalReceived()).isEqualTo(3L);
        assertThat(summary.getTotalClassified()).isEqualTo(1L);
        assertThat(summary.getTotalManualReview()).isEqualTo(1L);
        assertThat(summary.getTotalDeadLetter()).isEqualTo(1L);
        assertThat(summary.getTotalDuplicates()).isZero();
        // classificationRate = (1 classified + 1 manual) / (3 received - 0 duplicates) = 2/3 = 66.67%
        assertThat(summary.getClassificationRatePercent()).isEqualTo(66.67);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String buildPayload(String tradeId, ExceptionCategory category,
                                ExceptionSubTheme subTheme, EventType eventType,
                                String fixedMessageId) throws Exception {
        return buildPayloadWithExceptionId(tradeId, category, subTheme, eventType, null, fixedMessageId);
    }

    private String buildPayloadWithExceptionId(String tradeId, ExceptionCategory category,
                                               ExceptionSubTheme subTheme, EventType eventType,
                                               String exceptionId) throws Exception {
        return buildPayloadWithExceptionId(tradeId, category, subTheme, eventType, exceptionId, null);
    }

    private String buildPayloadWithExceptionId(String tradeId, ExceptionCategory category,
                                               ExceptionSubTheme subTheme, EventType eventType,
                                               String exceptionId, String fixedMessageId) throws Exception {
        ExceptionRecord record = new ExceptionRecord();
        record.setTradeId(tradeId);
        record.setCategory(category);
        record.setSubTheme(subTheme);
        record.setAffectedFields(category != null ? "someField" : null);
        record.setCapturedAt(Instant.now());
        record.setStatus("OPEN");
        if (exceptionId != null) {
            record.setExceptionId(exceptionId);
        }

        MessageHeader header = new MessageHeader();
        header.setMessageId(fixedMessageId != null ? fixedMessageId : "MSG-" + tradeId);
        header.setCorrelationId("CORR-" + tradeId);
        header.setEventType(eventType);
        header.setSchemaVersion("capstone-fpml-json-1.0");
        header.setSourceService("validation-service");
        header.setCreatedTimestamp(Instant.now());

        ExceptionEvent event = ExceptionEvent.builder()
                .messageHeader(header)
                .exception(record)
                .build();

        return objectMapper.writeValueAsString(event);
    }
}

