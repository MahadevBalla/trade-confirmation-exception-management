package com.tcem.exception.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.exception.classifier.ClassificationRulesEngine;
import com.tcem.exception.config.CouchDbProperties;
import com.tcem.exception.couchdb.CouchDbOperations;
import com.tcem.exception.model.ExceptionProcessingResult;
import com.tcem.exception.model.document.IdempotencyDocument;
import com.tcem.exception.producer.DeadLetterProducer;
import com.tcem.exception.producer.ExceptionClassifiedProducer;
import com.tcem.exception.producer.ManualReviewProducer;
import com.tcem.exception.recommendation.RecommendationEngine;
import com.tcem.exception.recommendation.SlaCalculator;
import com.tcem.exception.repository.AuditCouchDbRepository;
import com.tcem.exception.repository.ExceptionCouchDbRepository;
import com.tcem.exception.repository.IdempotencyRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultExceptionProcessingService}.
 * Uses {@link CouchDbOperations} mock + real repository + real engine instances
 * to test the full 7-step pipeline in isolation.
 */
@ExtendWith(MockitoExtension.class)
class DefaultExceptionProcessingServiceTest {

    @Mock
    private CouchDbOperations couchDb;

    @Mock
    private KafkaOperations<String, String> kafkaOperations;

    private ObjectMapper objectMapper;
    private DefaultExceptionProcessingService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        CouchDbProperties props = new CouchDbProperties();
        props.setExceptionsDatabase("exceptions");
        props.setAuditEventsDatabase("audit-events");

        ExceptionCouchDbRepository exceptionRepo = new ExceptionCouchDbRepository(couchDb, props);
        AuditCouchDbRepository auditRepo = new AuditCouchDbRepository(couchDb, props);
        IdempotencyRepository idempotencyRepo = new IdempotencyRepository(couchDb, props);

        ExceptionClassifiedProducer classifiedProducer = new ExceptionClassifiedProducer(kafkaOperations, objectMapper);
        ManualReviewProducer manualReviewProducer = new ManualReviewProducer(kafkaOperations, objectMapper);
        DeadLetterProducer deadLetterProducer = new DeadLetterProducer(kafkaOperations, objectMapper);

        ClassificationRulesEngine classificationEngine = new ClassificationRulesEngine();
        RecommendationEngine recommendationEngine = new RecommendationEngine();
        SlaCalculator slaCalculator = new SlaCalculator();
        ExceptionIdGenerator idGenerator = new ExceptionIdGenerator();

        ExceptionMetricsService metricsService = new ExceptionMetricsService();

        service = new DefaultExceptionProcessingService(
                objectMapper,
                classificationEngine,
                recommendationEngine,
                slaCalculator,
                idGenerator,
                exceptionRepo,
                auditRepo,
                idempotencyRepo,
                classifiedProducer,
                manualReviewProducer,
                deadLetterProducer,
                metricsService
        );
    }

    // -------------------------------------------------------------------------
    // Step 1 & 2: Null/blank/malformed payload → DEAD_LETTER
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Null payload returns DEAD_LETTER and sends DLQ event")
    void nullPayload_returnsDeadLetter() {
        stubKafkaSend(TopicNames.DEADLETTER_TRADE);

        ExceptionProcessingResult result = service.process("TRD-001", null);

        assertThat(result).isEqualTo(ExceptionProcessingResult.DEAD_LETTER);
        verify(kafkaOperations).send(eq(TopicNames.DEADLETTER_TRADE), anyString(), anyString());
    }

    @Test
    @DisplayName("Blank payload returns DEAD_LETTER and sends DLQ event")
    void blankPayload_returnsDeadLetter() {
        stubKafkaSend(TopicNames.DEADLETTER_TRADE);

        ExceptionProcessingResult result = service.process("TRD-001", "   ");

        assertThat(result).isEqualTo(ExceptionProcessingResult.DEAD_LETTER);
        verify(kafkaOperations).send(eq(TopicNames.DEADLETTER_TRADE), anyString(), anyString());
    }

    @Test
    @DisplayName("Malformed JSON payload returns DEAD_LETTER and sends DLQ event")
    void malformedJson_returnsDeadLetter() {
        stubKafkaSend(TopicNames.DEADLETTER_TRADE);

        ExceptionProcessingResult result = service.process("TRD-001", "{not valid json}");

        assertThat(result).isEqualTo(ExceptionProcessingResult.DEAD_LETTER);
        verify(kafkaOperations).send(eq(TopicNames.DEADLETTER_TRADE), anyString(), anyString());
    }

    @Test
    @DisplayName("ExceptionEvent with null exception field returns DEAD_LETTER")
    void nullExceptionField_returnsDeadLetter() throws Exception {
        stubKafkaSend(TopicNames.DEADLETTER_TRADE);
        ExceptionEvent event = ExceptionEvent.builder().exception(null).build();
        String payload = objectMapper.writeValueAsString(event);

        ExceptionProcessingResult result = service.process("TRD-001", payload);

        assertThat(result).isEqualTo(ExceptionProcessingResult.DEAD_LETTER);
    }

    // -------------------------------------------------------------------------
    // Step 4: Idempotency — duplicate suppression
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Duplicate message (idempotency hit) returns DUPLICATE without publishing")
    void duplicateMessage_returnsDuplicate() throws Exception {
        // Idempotency check returns a document (already processed)
        when(couchDb.getDocument(eq("exceptions"), startsWith("idempotency:"), eq(IdempotencyDocument.class)))
                .thenReturn(Optional.of(new IdempotencyDocument()));

        String payload = buildValidPayload("TRD-DUP", ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_STRIKE, EventType.NEW_TRADE);

        ExceptionProcessingResult result = service.process("TRD-DUP", payload);

        assertThat(result).isEqualTo(ExceptionProcessingResult.DUPLICATE);
        verify(kafkaOperations, never()).send(eq(TopicNames.EXCEPTION_CLASSIFIED), anyString(), anyString());
        verify(kafkaOperations, never()).send(eq(TopicNames.MANUAL_REVIEW_REQUIRED), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Step 4: REPLAY bypass
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("REPLAY event type bypasses idempotency check and classifies")
    void replayEvent_bypassesIdempotencyAndClassifies() throws Exception {
        stubKafkaSend(TopicNames.EXCEPTION_CLASSIFIED);
        // Do NOT stub couchDb.getDocument — idempotency should never be called
        String payload = buildValidPayload("TRD-REPLAY", ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_STRIKE, EventType.REPLAY);

        ExceptionProcessingResult result = service.process("TRD-REPLAY", payload);

        assertThat(result).isEqualTo(ExceptionProcessingResult.CLASSIFIED);
        verify(couchDb, never()).getDocument(anyString(), startsWith("idempotency:"), any());
        verify(kafkaOperations).send(eq(TopicNames.EXCEPTION_CLASSIFIED), eq("TRD-REPLAY"), anyString());
    }

    // -------------------------------------------------------------------------
    // Step 8: Happy path — CLASSIFIED
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Valid MISSING_ECONOMIC_DATA exception is classified with confidence 1.0 and published to exception.classified")
    void validException_classified_publishedToExceptionClassified() throws Exception {
        // No prior idempotency record
        when(couchDb.getDocument(eq("exceptions"), startsWith("idempotency:"), eq(IdempotencyDocument.class)))
                .thenReturn(Optional.empty());
        stubKafkaSend(TopicNames.EXCEPTION_CLASSIFIED);

        String payload = buildValidPayload("TRD-001", ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_STRIKE, EventType.NEW_TRADE);

        ExceptionProcessingResult result = service.process("TRD-001", payload);

        assertThat(result).isEqualTo(ExceptionProcessingResult.CLASSIFIED);
        verify(kafkaOperations).send(eq(TopicNames.EXCEPTION_CLASSIFIED), eq("TRD-001"), anyString());
        verify(kafkaOperations, never()).send(eq(TopicNames.MANUAL_REVIEW_REQUIRED), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Step 8: Manual review routing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Low-confidence exception (null category) is routed to manual.review.required")
    void lowConfidenceException_routedToManualReview() throws Exception {
        when(couchDb.getDocument(eq("exceptions"), startsWith("idempotency:"), eq(IdempotencyDocument.class)))
                .thenReturn(Optional.empty());
        stubKafkaSend(TopicNames.MANUAL_REVIEW_REQUIRED);

        // Null category → confidence 0.3 → requiresManualReview = true
        String payload = buildValidPayload("TRD-MR", null, null, EventType.NEW_TRADE);

        ExceptionProcessingResult result = service.process("TRD-MR", payload);

        assertThat(result).isEqualTo(ExceptionProcessingResult.MANUAL_REVIEW);
        verify(kafkaOperations).send(eq(TopicNames.MANUAL_REVIEW_REQUIRED), eq("TRD-MR"), anyString());
        verify(kafkaOperations, never()).send(eq(TopicNames.EXCEPTION_CLASSIFIED), anyString(), anyString());
    }

    @Test
    @DisplayName("ExceptionId is preserved from inbound record if already set")
    void inboundExceptionId_isPreserved() throws Exception {
        when(couchDb.getDocument(eq("exceptions"), startsWith("idempotency:"), eq(IdempotencyDocument.class)))
                .thenReturn(Optional.empty());
        stubKafkaSend(TopicNames.EXCEPTION_CLASSIFIED);

        ExceptionRecord rec = buildRecord("TRD-PRE", ExceptionCategory.TEMPLATE_ISSUE,
                ExceptionSubTheme.NO_TEMPLATE_FOUND, EventType.NEW_TRADE);
        rec.setExceptionId("EXC-EXISTING-001");
        String payload = objectMapper.writeValueAsString(buildEvent(rec, EventType.NEW_TRADE));

        service.process("TRD-PRE", payload);

        // Verify the classified event contains the preserved exceptionId
        verify(kafkaOperations).send(eq(TopicNames.EXCEPTION_CLASSIFIED), eq("TRD-PRE"),
                argThat(json -> json.contains("EXC-EXISTING-001")));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildValidPayload(String tradeId, ExceptionCategory category,
                                     ExceptionSubTheme subTheme, EventType eventType) throws Exception {
        ExceptionRecord rec = buildRecord(tradeId, category, subTheme, eventType);
        return objectMapper.writeValueAsString(buildEvent(rec, eventType));
    }

    private ExceptionRecord buildRecord(String tradeId, ExceptionCategory category,
                                        ExceptionSubTheme subTheme, EventType eventType) {
        ExceptionRecord rec = new ExceptionRecord();
        rec.setTradeId(tradeId);
        rec.setCategory(category);
        rec.setSubTheme(subTheme);
        rec.setAffectedFields(category != null ? "strike" : null);
        rec.setCapturedAt(Instant.now());
        rec.setStatus("OPEN");
        return rec;
    }

    private ExceptionEvent buildEvent(ExceptionRecord rec, EventType eventType) {
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-TEST-001");
        header.setCorrelationId("CORR-TEST-001");
        header.setEventType(eventType);
        header.setSchemaVersion("capstone-fpml-json-1.0");
        header.setSourceService("validation-service");
        header.setCreatedTimestamp(Instant.now());
        return ExceptionEvent.builder().messageHeader(header).exception(rec).build();
    }

    @SuppressWarnings("unchecked")
    private void stubKafkaSend(String topic) {
        RecordMetadata meta = new RecordMetadata(new TopicPartition(topic, 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sr = new SendResult<>(null, meta);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sr);
        when(kafkaOperations.send(eq(topic), anyString(), anyString())).thenReturn(future);
    }
}

