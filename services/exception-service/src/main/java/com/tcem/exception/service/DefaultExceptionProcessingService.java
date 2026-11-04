package com.tcem.exception.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.idempotency.IdempotencyKey;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.exception.classifier.ClassificationResult;
import com.tcem.exception.classifier.ClassificationRulesEngine;
import com.tcem.exception.model.ExceptionProcessingResult;
import com.tcem.exception.model.document.AuditDocument;
import com.tcem.exception.model.document.ExceptionDocument;
import com.tcem.exception.producer.DeadLetterProducer;
import com.tcem.exception.producer.ExceptionClassifiedProducer;
import com.tcem.exception.producer.ManualReviewProducer;
import com.tcem.exception.recommendation.RecommendationEngine;
import com.tcem.exception.recommendation.SlaCalculator;
import com.tcem.exception.repository.AuditCouchDbRepository;
import com.tcem.exception.repository.ExceptionCouchDbRepository;
import com.tcem.exception.repository.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.UUID;

/**
 * Default implementation of the 7-step exception processing pipeline (FR7, FR8, FR9, FR12).
 *
 * <h3>Processing pipeline</h3>
 * <ol>
 *   <li>Guard against null/blank payload → DLQ</li>
 *   <li>Deserialize JSON → {@link ExceptionEvent} → DLQ on failure</li>
 *   <li>Resolve identifiers; build {@link IdempotencyKey}</li>
 *   <li>Idempotency check — skip duplicates (bypass on REPLAY event type)</li>
 *   <li>Assign exceptionId if absent; run {@link ClassificationRulesEngine}</li>
 *   <li>{@link RecommendationEngine} enrich + {@link SlaCalculator} deadline</li>
 *   <li>Persist {@link ExceptionDocument} + {@link AuditDocument} + idempotency record to CouchDB</li>
 *   <li>Route: confidence ≥ 0.6 → {@code exception.classified}; else → {@code manual.review.required}</li>
 * </ol>
 */
@Service
public class DefaultExceptionProcessingService implements ExceptionProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultExceptionProcessingService.class);

    private static final String SOURCE_SERVICE = "exception-service";
    private static final String SCHEMA_VERSION  = "capstone-fpml-json-1.0";

    private final ObjectMapper objectMapper;
    private final ClassificationRulesEngine classificationRulesEngine;
    private final RecommendationEngine recommendationEngine;
    private final SlaCalculator slaCalculator;
    private final ExceptionIdGenerator exceptionIdGenerator;
    private final ExceptionCouchDbRepository exceptionRepository;
    private final AuditCouchDbRepository auditRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ExceptionClassifiedProducer exceptionClassifiedProducer;
    private final ManualReviewProducer manualReviewProducer;
    private final DeadLetterProducer deadLetterProducer;
    private final ExceptionMetricsService metricsService;

    public DefaultExceptionProcessingService(
            ObjectMapper objectMapper,
            ClassificationRulesEngine classificationRulesEngine,
            RecommendationEngine recommendationEngine,
            SlaCalculator slaCalculator,
            ExceptionIdGenerator exceptionIdGenerator,
            ExceptionCouchDbRepository exceptionRepository,
            AuditCouchDbRepository auditRepository,
            IdempotencyRepository idempotencyRepository,
            ExceptionClassifiedProducer exceptionClassifiedProducer,
            ManualReviewProducer manualReviewProducer,
            DeadLetterProducer deadLetterProducer,
            ExceptionMetricsService metricsService) {
        this.objectMapper = objectMapper;
        this.classificationRulesEngine = classificationRulesEngine;
        this.recommendationEngine = recommendationEngine;
        this.slaCalculator = slaCalculator;
        this.exceptionIdGenerator = exceptionIdGenerator;
        this.exceptionRepository = exceptionRepository;
        this.auditRepository = auditRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.exceptionClassifiedProducer = exceptionClassifiedProducer;
        this.manualReviewProducer = manualReviewProducer;
        this.deadLetterProducer = deadLetterProducer;
        this.metricsService = metricsService;
    }

    // =========================================================================
    // Main pipeline entry point
    // =========================================================================

    @Override
    public ExceptionProcessingResult process(String partitionKey, String rawPayload) {
        log.info("Processing exception.created message [key='{}']", partitionKey);
        metricsService.recordReceived();

        // ------------------------------------------------------------------
        // Step 1: Guard against null/blank payload
        // ------------------------------------------------------------------
        if (rawPayload == null || rawPayload.isBlank()) {
            log.error("Received null or empty payload for key '{}'", partitionKey);
            sendDeadLetterForRawFailure(partitionKey, rawPayload,
                    "Payload is null or empty", "IllegalArgumentException", null, false);
            safeSaveAudit(partitionKey, null, null, "EXCEPTION_PARSE",
                    ProcessingStatus.DEAD_LETTER, "Null or empty payload received");
            metricsService.record(ExceptionProcessingResult.DEAD_LETTER);
            return ExceptionProcessingResult.DEAD_LETTER;
        }

        // ------------------------------------------------------------------
        // Step 2: Deserialize JSON → ExceptionEvent
        // ------------------------------------------------------------------
        ExceptionEvent event;
        try {
            event = objectMapper.readValue(rawPayload, ExceptionEvent.class);
        } catch (JsonProcessingException ex) {
            log.error("Malformed JSON for key '{}': {}", partitionKey, ex.getMessage());
            sendDeadLetterForRawFailure(partitionKey, rawPayload,
                    "Malformed JSON: " + ex.getMessage(),
                    ex.getClass().getName(), getStackTraceExcerpt(ex), false);
            safeSaveAudit(partitionKey, null, null, "EXCEPTION_PARSE",
                    ProcessingStatus.DEAD_LETTER, "JSON parse failure: " + ex.getMessage());
            metricsService.record(ExceptionProcessingResult.DEAD_LETTER);
            return ExceptionProcessingResult.DEAD_LETTER;
        } catch (Exception ex) {
            log.error("Unexpected deserialization error for key '{}': {}", partitionKey, ex.getMessage());
            sendDeadLetterForRawFailure(partitionKey, rawPayload,
                    "Deserialization failure: " + ex.getMessage(),
                    ex.getClass().getName(), getStackTraceExcerpt(ex), true);
            safeSaveAudit(partitionKey, null, null, "EXCEPTION_PARSE",
                    ProcessingStatus.DEAD_LETTER, "Unexpected deserialization error: " + ex.getMessage());
            metricsService.record(ExceptionProcessingResult.DEAD_LETTER);
            return ExceptionProcessingResult.DEAD_LETTER;
        }

        ExceptionRecord record = event.getException();
        MessageHeader inboundHeader = event.getMessageHeader();

        if (record == null) {
            log.error("ExceptionEvent.exception is null for key '{}'", partitionKey);
            sendDeadLetterForRawFailure(partitionKey, rawPayload,
                    "ExceptionEvent.exception field is null", "NullPointerException", null, false);
            metricsService.record(ExceptionProcessingResult.DEAD_LETTER);
            return ExceptionProcessingResult.DEAD_LETTER;
        }

        // ------------------------------------------------------------------
        // Step 3: Resolve identifiers and build IdempotencyKey
        // ------------------------------------------------------------------
        String tradeId      = resolveTradeId(record, partitionKey);
        String messageId    = resolveMessageId(inboundHeader);
        String schemaVersion = resolveSchemaVersion(inboundHeader);
        EventType eventType  = resolveEventType(inboundHeader);
        String correlationId = inboundHeader != null ? inboundHeader.getCorrelationId() : null;

        IdempotencyKey idKey = IdempotencyKey.of(tradeId, messageId, schemaVersion, eventType);

        // ------------------------------------------------------------------
        // Step 4: Idempotency check (bypass for REPLAY)
        // ------------------------------------------------------------------
        if (eventType != EventType.REPLAY) {
            boolean isDuplicate = false;
            try {
                isDuplicate = idempotencyRepository.isProcessed(idKey);
            } catch (Exception ex) {
                log.warn("Idempotency check failed for key '{}': {}", idKey.toStorageKey(), ex.getMessage());
            }

            if (isDuplicate) {
                log.warn("Duplicate exception message detected [idempotencyKey={}]", idKey.toStorageKey());
                safeSaveAudit(tradeId, correlationId, eventType.name(), "IDEMPOTENCY_CHECK",
                        ProcessingStatus.RECEIVED,
                        "Duplicate message suppressed; key=" + idKey.toStorageKey());
                metricsService.record(ExceptionProcessingResult.DUPLICATE);
                return ExceptionProcessingResult.DUPLICATE;
            }
        } else {
            log.info("REPLAY event type detected for key '{}'; skipping idempotency check", tradeId);
        }

        // ------------------------------------------------------------------
        // Step 5: Assign exceptionId if absent; run classification engine
        // ------------------------------------------------------------------
        if (record.getExceptionId() == null || record.getExceptionId().isBlank()) {
            record.setExceptionId(exceptionIdGenerator.nextId());
            log.debug("Assigned exceptionId '{}' to tradeId '{}'", record.getExceptionId(), tradeId);
        }

        if (record.getCapturedAt() == null) {
            record.setCapturedAt(Instant.now());
        }

        ClassificationResult classificationResult;
        try {
            classificationResult = classificationRulesEngine.classify(record);
            record.setClassificationConfidence(classificationResult.getClassificationConfidence());
            log.debug("Classification result for exceptionId='{}': category={}, confidence={}",
                    record.getExceptionId(),
                    classificationResult.getConfirmedCategory(),
                    classificationResult.getClassificationConfidence());
        } catch (Exception ex) {
            log.error("Classification engine failed for exceptionId='{}': {}", record.getExceptionId(), ex.getMessage());
            sendDeadLetterForException(tradeId, rawPayload,
                    "Classification engine failure: " + ex.getMessage(),
                    ex.getClass().getName(), getStackTraceExcerpt(ex), true);
            safeSaveAudit(tradeId, correlationId, eventType.name(), "CLASSIFICATION",
                    ProcessingStatus.DEAD_LETTER, "Classification failure: " + ex.getMessage());
            metricsService.record(ExceptionProcessingResult.DEAD_LETTER);
            return ExceptionProcessingResult.DEAD_LETTER;
        }

        // ------------------------------------------------------------------
        // Step 6: Recommendation enrichment + SLA deadline
        // ------------------------------------------------------------------
        try {
            recommendationEngine.enrich(record);
            Instant slaDeadline = slaCalculator.computeDeadline(record.getOperationalImpact(), record.getCapturedAt());
            record.setSlaDeadline(slaDeadline);
            log.debug("Recommendation applied for exceptionId='{}': queue={}, slaDeadline={}",
                    record.getExceptionId(), record.getOwnerQueue(), slaDeadline);
        } catch (Exception ex) {
            log.warn("Recommendation enrichment failed for exceptionId='{}': {} — continuing with partial data",
                    record.getExceptionId(), ex.getMessage());
        }

        // ------------------------------------------------------------------
        // Step 7: Persist ExceptionDocument + AuditDocument + IdempotencyRecord
        // ------------------------------------------------------------------
        safePersistException(record, idKey.toStorageKey());
        safeSaveAudit(tradeId, correlationId, eventType.name(), "CLASSIFICATION",
                ProcessingStatus.EXCEPTION_CAPTURED,
                "Exception classified: category=" + record.getCategory()
                        + ", confidence=" + record.getClassificationConfidence()
                        + ", exceptionId=" + record.getExceptionId());
        safeRecordIdempotency(idKey, classificationResult.requiresManualReview()
                ? ExceptionProcessingResult.MANUAL_REVIEW.name()
                : ExceptionProcessingResult.CLASSIFIED.name());

        // ------------------------------------------------------------------
        // Step 8: Route to downstream topic
        // ------------------------------------------------------------------
        MessageHeader outboundHeader = buildOutboundHeader(tradeId, correlationId, eventType,
                inboundHeader != null ? inboundHeader.getReplayCount() : 0);
        ExceptionEvent outboundEvent = ExceptionEvent.builder()
                .messageHeader(outboundHeader)
                .exception(record)
                .build();

        ExceptionProcessingResult finalResult;
        if (classificationResult.requiresManualReview()) {
            log.warn("Manual review required for exceptionId='{}' (confidence={})",
                    record.getExceptionId(), record.getClassificationConfidence());
            manualReviewProducer.sendManualReviewRequired(tradeId, outboundEvent);
            safeSaveAudit(tradeId, correlationId, eventType.name(), "ROUTING",
                    ProcessingStatus.MANUAL_REVIEW,
                    "Routed to manual.review.required; confidence=" + record.getClassificationConfidence());
            finalResult = ExceptionProcessingResult.MANUAL_REVIEW;
        } else {
            exceptionClassifiedProducer.sendExceptionClassified(tradeId, outboundEvent);
            safeSaveAudit(tradeId, correlationId, eventType.name(), "ROUTING",
                    ProcessingStatus.EXCEPTION_CAPTURED,
                    "Routed to exception.classified; confidence=" + record.getClassificationConfidence());
            finalResult = ExceptionProcessingResult.CLASSIFIED;
        }

        metricsService.record(finalResult);
        return finalResult;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String resolveTradeId(ExceptionRecord record, String fallbackKey) {
        if (record != null && record.getTradeId() != null && !record.getTradeId().isBlank()) {
            return record.getTradeId();
        }
        if (fallbackKey != null && !fallbackKey.isBlank()) {
            return fallbackKey;
        }
        return "UNKNOWN_TRD_" + System.currentTimeMillis();
    }

    private String resolveMessageId(MessageHeader header) {
        if (header != null && header.getMessageId() != null && !header.getMessageId().isBlank()) {
            return header.getMessageId();
        }
        return "MSG-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String resolveSchemaVersion(MessageHeader header) {
        if (header != null && header.getSchemaVersion() != null && !header.getSchemaVersion().isBlank()) {
            return header.getSchemaVersion();
        }
        return SCHEMA_VERSION;
    }

    private EventType resolveEventType(MessageHeader header) {
        if (header != null && header.getEventType() != null) {
            return header.getEventType();
        }
        return EventType.NEW_TRADE;
    }

    private MessageHeader buildOutboundHeader(String tradeId, String correlationId, EventType eventType, int replayCount) {
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-" + UUID.randomUUID().toString().substring(0, 8));
        header.setCorrelationId(correlationId != null ? correlationId : tradeId);
        // workitem-service relies on these to detect a replay's outcome.
        header.setEventType(eventType);
        header.setReplayCount(replayCount);
        header.setSchemaVersion(SCHEMA_VERSION);
        header.setSourceService(SOURCE_SERVICE);
        header.setCreatedTimestamp(Instant.now());
        return header;
    }

    private void safePersistException(ExceptionRecord record, String idempotencyKeyStr) {
        try {
            ExceptionDocument doc = ExceptionDocument.builder()
                    .exceptionId(record.getExceptionId())
                    .tradeId(record.getTradeId())
                    .tradeVersion(record.getTradeVersion())
                    .sourceMessageId(record.getSourceMessageId())
                    .correlationId(record.getCorrelationId())
                    .idempotencyKey(idempotencyKeyStr)
                    .category(record.getCategory())
                    .subTheme(record.getSubTheme())
                    .classificationConfidence(record.getClassificationConfidence())
                    .reasonCode(record.getReasonCode())
                    .affectedFields(record.getAffectedFields())
                    .recommendedAction(record.getRecommendedAction())
                    .ownerQueue(record.getOwnerQueue())
                    .replayMayResolve(record.isReplayMayResolve())
                    .resolutionDifficulty(record.getResolutionDifficulty())
                    .operationalImpact(record.getOperationalImpact())
                    .status(record.getStatus() != null ? record.getStatus() : "OPEN")
                    .capturedAt(record.getCapturedAt())
                    .classifiedAt(Instant.now())
                    .slaDeadline(record.getSlaDeadline())
                    .build();
            exceptionRepository.save(doc);
            log.debug("Persisted ExceptionDocument for exceptionId='{}'", record.getExceptionId());
        } catch (Exception ex) {
            log.warn("CouchDB exception save failed for exceptionId='{}': {}",
                    record.getExceptionId(), ex.getMessage());
        }
    }

    private void safeSaveAudit(String tradeId, String correlationId, String eventType,
                                String stage, ProcessingStatus status, String details) {
        try {
            String auditId = "audit:" + tradeId + ":" + System.currentTimeMillis()
                    + ":" + UUID.randomUUID().toString().substring(0, 8);
            AuditDocument audit = AuditDocument.builder()
                    .id(auditId)
                    .tradeId(tradeId)
                    .correlationId(correlationId)
                    .eventType(eventType)
                    .stage(stage)
                    .status(status)
                    .details(details)
                    .sourceService(SOURCE_SERVICE)
                    .timestamp(Instant.now())
                    .build();
            auditRepository.save(audit);
        } catch (Exception ex) {
            log.warn("CouchDB audit save failed for tradeId='{}': {}", tradeId, ex.getMessage());
        }
    }

    private void safeRecordIdempotency(IdempotencyKey key, String result) {
        try {
            idempotencyRepository.record(key, result);
        } catch (Exception ex) {
            log.warn("CouchDB idempotency record failed for key '{}': {}",
                    key.toStorageKey(), ex.getMessage());
        }
    }

    private void sendDeadLetterForRawFailure(String key, String payload, String reason,
                                              String exClass, String stackTrace, boolean replayable) {
        try {
            MessageHeader header = new MessageHeader();
            header.setMessageId("DLQ-" + UUID.randomUUID().toString().substring(0, 8));
            header.setEventType(EventType.NEW_TRADE);
            header.setSourceService(SOURCE_SERVICE);
            header.setCreatedTimestamp(Instant.now());

            DeadLetterEvent dlq = DeadLetterEvent.builder()
                    .messageHeader(header)
                    .originalPayload(payload)
                    .failedOnTopic(TopicNames.EXCEPTION_CREATED)
                    .failedInService(SOURCE_SERVICE)
                    .failureReason(reason)
                    .exceptionClass(exClass)
                    .stackTraceExcerpt(stackTrace)
                    .failedAt(Instant.now())
                    .replayable(replayable)
                    .dlqStatus("PENDING_REPLAY")
                    .build();

            deadLetterProducer.sendDeadLetter(key, dlq);
        } catch (Exception ex) {
            log.error("Failed to send dead-letter event for key '{}': {}", key, ex.getMessage());
        }
    }

    private void sendDeadLetterForException(String tradeId, String payload, String reason,
                                             String exClass, String stackTrace, boolean replayable) {
        sendDeadLetterForRawFailure(tradeId, payload, reason, exClass, stackTrace, replayable);
    }

    private String getStackTraceExcerpt(Throwable t) {
        if (t == null) return null;
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String full = sw.toString();
        return full.length() > 2000 ? full.substring(0, 2000) : full;
    }
}
