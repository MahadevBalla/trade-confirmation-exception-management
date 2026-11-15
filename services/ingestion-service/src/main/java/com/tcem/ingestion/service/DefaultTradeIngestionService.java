package com.tcem.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.idempotency.IdempotencyKey;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.ingestion.model.IngestionResult;
import com.tcem.ingestion.model.document.AuditDocument;
import com.tcem.ingestion.model.document.TradeDocument;
import com.tcem.ingestion.producer.DeadLetterProducer;
import com.tcem.ingestion.producer.ExceptionCreatedProducer;
import com.tcem.ingestion.producer.TradeAcceptedProducer;
import com.tcem.ingestion.repository.AuditCouchDbRepository;
import com.tcem.ingestion.repository.IdempotencyCouchDbRepository;
import com.tcem.ingestion.repository.TradeCouchDbRepository;
import com.tcem.ingestion.validator.TradeValidator;
import com.tcem.ingestion.validator.ValidationError;
import com.tcem.ingestion.validator.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of TradeIngestionService (FR1, FR2, FR3, FR12).
 */
@Service
public class DefaultTradeIngestionService implements TradeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTradeIngestionService.class);

    private final ObjectMapper objectMapper;
    private final TradeValidator tradeValidator;
    private final TradeCouchDbRepository tradeRepository;
    private final AuditCouchDbRepository auditRepository;
    private final IdempotencyCouchDbRepository idempotencyRepository;
    private final TradeAcceptedProducer tradeAcceptedProducer;
    private final ExceptionCreatedProducer exceptionCreatedProducer;
    private final DeadLetterProducer deadLetterProducer;
    private final IngestionMetricsService metricsService;

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultTradeIngestionService(ObjectMapper objectMapper,
                                        TradeValidator tradeValidator,
                                        TradeCouchDbRepository tradeRepository,
                                        AuditCouchDbRepository auditRepository,
                                        IdempotencyCouchDbRepository idempotencyRepository,
                                        TradeAcceptedProducer tradeAcceptedProducer,
                                        ExceptionCreatedProducer exceptionCreatedProducer,
                                        DeadLetterProducer deadLetterProducer,
                                        IngestionMetricsService metricsService) {
        this.objectMapper = objectMapper;
        this.tradeValidator = tradeValidator;
        this.tradeRepository = tradeRepository;
        this.auditRepository = auditRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.tradeAcceptedProducer = tradeAcceptedProducer;
        this.exceptionCreatedProducer = exceptionCreatedProducer;
        this.deadLetterProducer = deadLetterProducer;
        this.metricsService = metricsService != null ? metricsService : new IngestionMetricsService();
    }

    public DefaultTradeIngestionService(ObjectMapper objectMapper,
                                        TradeValidator tradeValidator,
                                        TradeCouchDbRepository tradeRepository,
                                        AuditCouchDbRepository auditRepository,
                                        IdempotencyCouchDbRepository idempotencyRepository,
                                        TradeAcceptedProducer tradeAcceptedProducer,
                                        ExceptionCreatedProducer exceptionCreatedProducer,
                                        DeadLetterProducer deadLetterProducer) {
        this(objectMapper, tradeValidator, tradeRepository, auditRepository, idempotencyRepository,
                tradeAcceptedProducer, exceptionCreatedProducer, deadLetterProducer, new IngestionMetricsService());
    }

    @Override
    public IngestionResult processRawTrade(String partitionKey, String rawPayload) {
        metricsService.recordReceived();
        log.info("Processing raw trade message with key: '{}'", partitionKey);

        // -------------------------------------------------------------------
        // 1. Guard against null or blank payload (Dead-letter route)
        // -------------------------------------------------------------------
        if (rawPayload == null || rawPayload.trim().isEmpty()) {
            metricsService.recordDeadLetter();
            log.error("Received null or empty raw payload for key '{}'", partitionKey);
            DeadLetterEvent dlqEvent = buildDeadLetterEvent(partitionKey, rawPayload,
                    "Payload is null or empty", "IllegalArgumentException", null, false);
            deadLetterProducer.sendDeadLetter(partitionKey, dlqEvent);
            safeSaveAudit(partitionKey, null, "DEAD_LETTER", "INGESTION_PARSE",
                    ProcessingStatus.DEAD_LETTER, "Empty or null payload received");
            return IngestionResult.deadLetter(partitionKey, "Empty or null payload", "Empty payload");
        }

        // -------------------------------------------------------------------
        // 2. Parse JSON into TradeEvent
        // -------------------------------------------------------------------
        TradeEvent tradeEvent;
        try {
            tradeEvent = objectMapper.readValue(rawPayload, TradeEvent.class);
        } catch (JsonProcessingException ex) {
            metricsService.recordDeadLetter();
            log.error("Malformed JSON received for key '{}': {}", partitionKey, ex.getMessage());
            DeadLetterEvent dlqEvent = buildDeadLetterEvent(partitionKey, rawPayload,
                    "Malformed JSON: " + ex.getMessage(), ex.getClass().getName(), getStackTraceExcerpt(ex), false);
            deadLetterProducer.sendDeadLetter(partitionKey, dlqEvent);
            safeSaveAudit(partitionKey, null, "DEAD_LETTER", "INGESTION_PARSE",
                    ProcessingStatus.DEAD_LETTER, "Fatal JSON parsing error: " + ex.getOriginalMessage());
            return IngestionResult.deadLetter(partitionKey, "Malformed JSON", ex.getMessage());
        } catch (Exception ex) {
            metricsService.recordDeadLetter();
            log.error("Unexpected error deserializing trade payload for key '{}': {}", partitionKey, ex.getMessage());
            DeadLetterEvent dlqEvent = buildDeadLetterEvent(partitionKey, rawPayload,
                    "Unexpected deserialization failure: " + ex.getMessage(), ex.getClass().getName(), getStackTraceExcerpt(ex), true);
            deadLetterProducer.sendDeadLetter(partitionKey, dlqEvent);
            safeSaveAudit(partitionKey, null, "DEAD_LETTER", "INGESTION_PARSE",
                    ProcessingStatus.DEAD_LETTER, "Unexpected error: " + ex.getMessage());
            return IngestionResult.deadLetter(partitionKey, "Deserialization failure", ex.getMessage());
        }

        // -------------------------------------------------------------------
        // 3. Extract Identifiers & Build Idempotency Key
        // -------------------------------------------------------------------
        String tradeId = resolveTradeId(tradeEvent, partitionKey);
        String messageId = resolveMessageId(tradeEvent);
        String schemaVersion = resolveSchemaVersion(tradeEvent);
        EventType eventType = resolveEventType(tradeEvent);

        IdempotencyKey idKey = IdempotencyKey.of(tradeId, messageId, schemaVersion, eventType);

        // -------------------------------------------------------------------
        // 4. Idempotency Check (Duplicate suppression)
        // -------------------------------------------------------------------
        // Every replay attempt of a message shares one key, so replays bypass the guard. Without this, attempt 2
        // emits a duplicate exception that still carries the REPLAY marker and is misread as a successful replay.
        boolean isDuplicate = false;
        if (eventType != EventType.REPLAY) {
            try {
                isDuplicate = idempotencyRepository.isProcessed(idKey);
            } catch (Exception ex) {
                log.warn("Failed to check idempotency in CouchDB for key '{}': {}", idKey.toStorageKey(), ex.getMessage());
            }
        }

        if (isDuplicate) {
            metricsService.recordDuplicate();
            log.warn("Duplicate message detected for idempotency key: {}", idKey.toStorageKey());
            String correlationId = tradeEvent.getMessageHeader() != null ? tradeEvent.getMessageHeader().getCorrelationId() : null;

            safeSaveAudit(tradeId, correlationId, eventType.name(), "IDEMPOTENCY_CHECK",
                    ProcessingStatus.RECEIVED, "Duplicate message suppressed for key: " + idKey.toStorageKey());

            // Emit duplicate exception to exception.created (FR7)
            ExceptionRecord dupRecord = new ExceptionRecord();
            dupRecord.setExceptionId("EXC-DUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            dupRecord.setTradeId(tradeId);
            dupRecord.setSourceMessageId(messageId);
            dupRecord.setCorrelationId(correlationId);
            dupRecord.setCategory(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE);
            dupRecord.setSubTheme(ExceptionSubTheme.DUPLICATE_MESSAGE);
            dupRecord.setClassificationConfidence(1.0);
            dupRecord.setReasonCode("DUPLICATE_TRADE_MESSAGE");
            dupRecord.setAffectedFields("messageHeader.messageId, trade.tradeId");
            dupRecord.setRecommendedAction("Duplicate message detected and suppressed. Verify upstream producer retry policy.");
            dupRecord.setOwnerQueue("OPERATIONS_REVIEW");
            dupRecord.setReplayMayResolve(false);
            dupRecord.setResolutionDifficulty("LOW");
            dupRecord.setOperationalImpact("LOW");
            dupRecord.setCapturedAt(Instant.now());
            dupRecord.setStatus("OPEN");

            ExceptionEvent dupEvent = new ExceptionEvent(
                    tradeEvent.getMessageHeader() != null ? tradeEvent.getMessageHeader() : createHeader(tradeId, messageId),
                    dupRecord
            );
            exceptionCreatedProducer.sendExceptionCreated(tradeId, dupEvent);

            return IngestionResult.duplicate(tradeId, messageId, idKey.toStorageKey());
        }

        // -------------------------------------------------------------------
        // 5. Schema Validation Engine (FR2)
        // -------------------------------------------------------------------
        ValidationResult validationResult = tradeValidator.validate(tradeEvent);
        String correlationId = tradeEvent.getMessageHeader() != null ? tradeEvent.getMessageHeader().getCorrelationId() : null;

        if (validationResult.isValid()) {
            // ===============================================================
            // Validation SUCCESS: Persist, Audit, Record Idempotency, and Publish
            // ===============================================================
            metricsService.recordAccepted();
            log.info("Trade '{}' successfully validated against schema '{}'", tradeId, schemaVersion);

            TradeDocument tradeDoc = TradeDocument.builder()
                    .id(tradeId)
                    .tradeId(tradeId)
                    .sourceSystemTradeId(tradeEvent.getTrade() != null ? tradeEvent.getTrade().getSourceSystemTradeId() : null)
                    .tradeVersion(tradeEvent.getTrade() != null ? tradeEvent.getTrade().getTradeVersion() : 1)
                    .productType(tradeEvent.getTrade() != null ? tradeEvent.getTrade().getProductType() : null)
                    .messageId(messageId)
                    .correlationId(correlationId)
                    .schemaVersion(schemaVersion)
                    .processingStatus(ProcessingStatus.ACCEPTED)
                    .canonicalTrade(tradeEvent.getTrade())
                    .rawPayload(rawPayload)
                    .idempotencyKey(idKey.toStorageKey())
                    .validationErrors(Collections.emptyList())
                    .build();

            safeSaveTrade(tradeDoc);
            safeRecordIdempotency(idKey, ProcessingStatus.ACCEPTED);
            safeSaveAudit(tradeId, correlationId, eventType.name(), "SCHEMA_VALIDATION",
                    ProcessingStatus.ACCEPTED, "Trade successfully validated and accepted");

            tradeAcceptedProducer.sendTradeAccepted(tradeId, tradeEvent);
            return IngestionResult.accepted(tradeId, messageId, idKey.toStorageKey());

        } else {
            // ===============================================================
            // Validation FAILURE: Persist SCHEMA_INVALID, Audit, and Publish Exceptions
            // ===============================================================
            metricsService.recordSchemaInvalid();
            List<String> errorMessages = validationResult.getErrors().stream()
                    .map(ValidationError::getMessage)
                    .toList();
            log.warn("Validation failed for trade '{}' with {} errors: {}", tradeId, errorMessages.size(), validationResult.getSummary());

            TradeDocument tradeDoc = TradeDocument.builder()
                    .id(tradeId)
                    .tradeId(tradeId)
                    .sourceSystemTradeId(tradeEvent.getTrade() != null ? tradeEvent.getTrade().getSourceSystemTradeId() : null)
                    .tradeVersion(tradeEvent.getTrade() != null ? tradeEvent.getTrade().getTradeVersion() : 1)
                    .productType(tradeEvent.getTrade() != null ? tradeEvent.getTrade().getProductType() : null)
                    .messageId(messageId)
                    .correlationId(correlationId)
                    .schemaVersion(schemaVersion)
                    .processingStatus(ProcessingStatus.SCHEMA_INVALID)
                    .canonicalTrade(tradeEvent.getTrade())
                    .rawPayload(rawPayload)
                    .idempotencyKey(idKey.toStorageKey())
                    .validationErrors(errorMessages)
                    .build();

            safeSaveTrade(tradeDoc);
            safeRecordIdempotency(idKey, ProcessingStatus.SCHEMA_INVALID);
            safeSaveAudit(tradeId, correlationId, eventType.name(), "SCHEMA_VALIDATION",
                    ProcessingStatus.SCHEMA_INVALID, "Validation failed: " + validationResult.getSummary());

            // Convert validation errors to canonical ExceptionRecords and publish to exception.created
            List<ExceptionRecord> exceptionRecords = validationResult.toExceptionRecords(tradeEvent);
            for (ExceptionRecord excRecord : exceptionRecords) {
                MessageHeader excHeader = tradeEvent.getMessageHeader() != null
                        ? tradeEvent.getMessageHeader()
                        : createHeader(tradeId, messageId);

                ExceptionEvent excEvent = new ExceptionEvent(excHeader, excRecord);
                exceptionCreatedProducer.sendExceptionCreated(tradeId, excEvent);
            }

            return IngestionResult.rejected(tradeId, messageId, idKey.toStorageKey(),
                    validationResult.getSummary(), errorMessages);
        }
    }

    // -----------------------------------------------------------------------
    // Helper Methods
    // -----------------------------------------------------------------------

    private String resolveTradeId(TradeEvent tradeEvent, String fallbackKey) {
        if (tradeEvent != null && tradeEvent.getTrade() != null && tradeEvent.getTrade().getTradeId() != null
                && !tradeEvent.getTrade().getTradeId().isBlank()) {
            return tradeEvent.getTrade().getTradeId();
        }
        if (fallbackKey != null && !fallbackKey.isBlank()) {
            return fallbackKey;
        }
        return "UNKNOWN_TRD_" + System.currentTimeMillis();
    }

    private String resolveMessageId(TradeEvent tradeEvent) {
        if (tradeEvent != null && tradeEvent.getMessageHeader() != null
                && tradeEvent.getMessageHeader().getMessageId() != null
                && !tradeEvent.getMessageHeader().getMessageId().isBlank()) {
            return tradeEvent.getMessageHeader().getMessageId();
        }
        return "MSG-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String resolveSchemaVersion(TradeEvent tradeEvent) {
        if (tradeEvent != null && tradeEvent.getMessageHeader() != null
                && tradeEvent.getMessageHeader().getSchemaVersion() != null
                && !tradeEvent.getMessageHeader().getSchemaVersion().isBlank()) {
            return tradeEvent.getMessageHeader().getSchemaVersion();
        }
        return "capstone-fpml-json-1.0";
    }

    private EventType resolveEventType(TradeEvent tradeEvent) {
        if (tradeEvent != null && tradeEvent.getMessageHeader() != null
                && tradeEvent.getMessageHeader().getEventType() != null) {
            return tradeEvent.getMessageHeader().getEventType();
        }
        return EventType.NEW_TRADE;
    }

    private DeadLetterEvent buildDeadLetterEvent(String key, String payload, String reason,
                                                 String exClass, String stackTrace, boolean replayable) {
        MessageHeader header = new MessageHeader();
        header.setMessageId("DLQ-" + UUID.randomUUID().toString().substring(0, 8));
        header.setEventType(EventType.NEW_TRADE);
        header.setSourceService("ingestion-service");
        header.setCreatedTimestamp(Instant.now());

        return DeadLetterEvent.builder()
                .messageHeader(header)
                .originalPayload(payload)
                .failedOnTopic(TopicNames.TRADE_RAW)
                .failedInService("ingestion-service")
                .failureReason(reason)
                .exceptionClass(exClass)
                .stackTraceExcerpt(stackTrace)
                .failedAt(Instant.now())
                .replayCount(0)
                .replayable(replayable)
                .dlqStatus("PENDING_REPLAY")
                .build();
    }

    private MessageHeader createHeader(String tradeId, String messageId) {
        MessageHeader header = new MessageHeader();
        header.setMessageId(messageId);
        header.setCorrelationId(tradeId);
        header.setEventType(EventType.NEW_TRADE);
        header.setSchemaVersion("capstone-fpml-json-1.0");
        header.setSourceService("ingestion-service");
        header.setCreatedTimestamp(Instant.now());
        return header;
    }

    private String getStackTraceExcerpt(Throwable t) {
        if (t == null) return null;
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String full = sw.toString();
        return full.length() > 2000 ? full.substring(0, 2000) : full;
    }

    private void safeSaveTrade(TradeDocument doc) {
        try {
            tradeRepository.save(doc);
        } catch (Exception ex) {
            log.warn("CouchDB trade save failed for trade '{}': {}", doc.getTradeId(), ex.getMessage());
        }
    }

    private void safeRecordIdempotency(IdempotencyKey key, ProcessingStatus status) {
        try {
            idempotencyRepository.record(key, status);
        } catch (Exception ex) {
            log.warn("CouchDB idempotency record failed for key '{}': {}", key.toStorageKey(), ex.getMessage());
        }
    }

    private void safeSaveAudit(String tradeId, String correlationId, String eventType,
                               String stage, ProcessingStatus status, String details) {
        try {
            AuditDocument audit = AuditDocument.builder()
                    .tradeId(tradeId)
                    .correlationId(correlationId)
                    .eventType(eventType)
                    .stage(stage)
                    .status(status)
                    .details(details)
                    .sourceService("ingestion-service")
                    .timestamp(Instant.now())
                    .build();
            auditRepository.save(audit);
        } catch (Exception ex) {
            log.warn("CouchDB audit event save failed for trade '{}': {}", tradeId, ex.getMessage());
        }
    }
}
