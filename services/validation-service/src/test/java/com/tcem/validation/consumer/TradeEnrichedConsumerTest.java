package com.tcem.validation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.idempotency.IdempotencyKey;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.trade.Trade;
import com.tcem.validation.config.ValidationProperties;
import com.tcem.validation.confirmation.ConfirmationService;
import com.tcem.validation.exception.StpExceptionMapper;
import com.tcem.validation.idempotency.ValidationIdempotencyRepository;
import com.tcem.validation.model.StpValidatedEvent;
import com.tcem.validation.producer.DeadLetterProducer;
import com.tcem.validation.producer.ExceptionCreatedProducer;
import com.tcem.validation.producer.StpValidatedProducer;
import com.tcem.validation.repository.ConfirmationCouchDbRepository;
import com.tcem.validation.repository.ConfirmationDocument;
import com.tcem.validation.repository.HeldTradeRepository;
import com.tcem.validation.rules.StpRulesEngine;
import com.tcem.validation.rules.StpValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TradeEnrichedConsumer}.
 * <p>
 * All dependencies are mocked; no Spring context is loaded.
 * The {@link ObjectMapper} is a real instance (with {@link JavaTimeModule}) because
 * JSON serialisation is core to the consumer's behaviour and cheap to run in tests.
 */
@ExtendWith(MockitoExtension.class)
class TradeEnrichedConsumerTest {

    @Mock private StpRulesEngine stpRulesEngine;
    @Mock private StpValidatedProducer stpValidatedProducer;
    @Mock private ConfirmationService confirmationService;
    @Mock private StpExceptionMapper stpExceptionMapper;
    @Mock private ExceptionCreatedProducer exceptionCreatedProducer;
    @Mock private DeadLetterProducer deadLetterProducer;
    @Mock private ConfirmationCouchDbRepository confirmationRepository;
    @Mock private ValidationIdempotencyRepository idempotencyRepository;
    @Mock private HeldTradeRepository heldTradeRepository;

    private ObjectMapper objectMapper;
    private ValidationProperties validationProperties;
    private TradeEnrichedConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        validationProperties = new ValidationProperties();
        validationProperties.setServiceName("validation-service");

        consumer = new TradeEnrichedConsumer(
                objectMapper, stpRulesEngine, stpValidatedProducer,
                confirmationService, stpExceptionMapper,
                exceptionCreatedProducer, deadLetterProducer,
                confirmationRepository, idempotencyRepository, heldTradeRepository, validationProperties);

        // Lenient stubs: these are "safety nets" so the async send methods don't NPE.
        // Not every test exercises every producer path, so lenient avoids false
        // UnnecessaryStubbingExceptions from Mockito strict-stub mode.
        lenient().when(stpValidatedProducer.sendStpValidated(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        lenient().when(exceptionCreatedProducer.sendExceptionCreated(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        lenient().when(deadLetterProducer.sendDeadLetter(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TradeEvent buildTradeEvent(String tradeId, int version, EventType eventType) {
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-001");
        header.setCorrelationId("CORR-001");
        header.setSchemaVersion(SchemaVersions.CURRENT);
        header.setCreatedTimestamp(Instant.now());
        header.setSourceService("enrichment-service");
        header.setEventType(eventType);

        Trade trade = new Trade();
        trade.setTradeId(tradeId);
        trade.setTradeVersion(version);

        return new TradeEvent(header, trade);
    }

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private StpValidationResult passedResult() {
        return new StpValidationResult(); // no failures added → passed
    }

    private StpValidationResult failedResult() {
        StpValidationResult result = new StpValidationResult();
        result.addFailure(
                ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_NOTIONAL,
                "notionalAmount is null",
                "fxOptionEconomics.notionalAmount");
        return result;
    }

    // -----------------------------------------------------------------------
    // Test: happy path — NEW_TRADE, STP passed
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("NEW_TRADE: STP passed → STP event published, confirmation generated")
    void newTrade_stpPassed_publishesStpAndConfirmation() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-001", 1, EventType.NEW_TRADE);
        when(confirmationRepository.findLatestForTrade("TRD-001")).thenReturn(Optional.empty());
        when(stpRulesEngine.evaluate(any())).thenReturn(passedResult());

        consumer.onMessage(toJson(event));

        verify(stpRulesEngine).evaluate(any(TradeEvent.class));
        verify(stpValidatedProducer).sendStpValidated(eq("TRD-001"), any(StpValidatedEvent.class));
        verify(confirmationService).processConfirmation(any(MessageHeader.class), any(Trade.class));
        verifyNoInteractions(stpExceptionMapper, exceptionCreatedProducer, deadLetterProducer);
    }

    // -----------------------------------------------------------------------
    // Test: STP failed — exceptions routed correctly
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("NEW_TRADE: STP failed → STP event published, exceptions dispatched, no confirmation")
    void newTrade_stpFailed_publishesExceptions() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-002", 1, EventType.NEW_TRADE);
        when(confirmationRepository.findLatestForTrade("TRD-002")).thenReturn(Optional.empty());
        when(stpRulesEngine.evaluate(any())).thenReturn(failedResult());

        ExceptionEvent mockException = new ExceptionEvent(new MessageHeader(), null);
        when(stpExceptionMapper.mapToExceptionEvents(any(), any(), any()))
                .thenReturn(List.of(mockException));

        consumer.onMessage(toJson(event));

        verify(stpValidatedProducer).sendStpValidated(eq("TRD-002"), any(StpValidatedEvent.class));
        verify(stpExceptionMapper).mapToExceptionEvents(any(), any(), any());
        var inOrder = inOrder(heldTradeRepository, exceptionCreatedProducer);
        inOrder.verify(heldTradeRepository).hold("TRD-002", toJson(event));
        inOrder.verify(exceptionCreatedProducer).sendExceptionCreated(eq("TRD-002"), eq(mockException));
        verifyNoInteractions(confirmationService, deadLetterProducer);
    }

    // -----------------------------------------------------------------------
    // Test: CANCEL_TRADE — no STP, no confirmation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("CANCEL_TRADE: skipped entirely — no STP, no confirmation, no DLQ")
    void cancelTrade_skipsProcessing() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-003", 2, EventType.CANCEL_TRADE);

        consumer.onMessage(toJson(event));

        verifyNoInteractions(stpRulesEngine, stpValidatedProducer,
                confirmationService, stpExceptionMapper,
                exceptionCreatedProducer, deadLetterProducer);
    }

    // -----------------------------------------------------------------------
    // Test: idempotency guard — already processed version is skipped
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Idempotency: already-processed version skipped for NEW_TRADE")
    void idempotency_alreadyProcessed_skips() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-004", 1, EventType.NEW_TRADE);

        ConfirmationDocument existing = new ConfirmationDocument();
        existing.setTradeId("TRD-004");
        existing.setTradeVersion(1); // same version → idempotent skip
        when(confirmationRepository.findLatestForTrade("TRD-004")).thenReturn(Optional.of(existing));

        consumer.onMessage(toJson(event));

        verifyNoInteractions(stpRulesEngine, stpValidatedProducer,
                confirmationService, stpExceptionMapper,
                exceptionCreatedProducer, deadLetterProducer);
    }

    // -----------------------------------------------------------------------
    // Test: REPLAY bypasses idempotency guard
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("REPLAY: bypasses idempotency guard and reprocesses the trade")
    void replay_bypassesIdempotencyGuard() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-005", 1, EventType.REPLAY);
        // Repository has a document for the same version — but REPLAY must not skip
        ConfirmationDocument existing = new ConfirmationDocument();
        existing.setTradeId("TRD-005");
        existing.setTradeVersion(1);
        // No stubbing for findLatestForTrade — it must NOT be called for REPLAY
        when(stpRulesEngine.evaluate(any())).thenReturn(passedResult());

        consumer.onMessage(toJson(event));

        // Repository should NOT be consulted for idempotency on REPLAY
        verifyNoInteractions(confirmationRepository);
        verify(stpRulesEngine).evaluate(any(TradeEvent.class));
        verify(confirmationService).processConfirmation(any(), any());
    }

    // -----------------------------------------------------------------------
    // Test: malformed JSON → dead letter
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Malformed JSON: routes to DLQ, no downstream processing")
    void malformedJson_routesToDeadLetter() {
        String badPayload = "{ this is not valid json }";

        consumer.onMessage(badPayload);

        verify(deadLetterProducer).sendDeadLetter(any(), any());
        verifyNoInteractions(stpRulesEngine, stpValidatedProducer,
                confirmationService, stpExceptionMapper,
                exceptionCreatedProducer, confirmationRepository);
    }

    // -----------------------------------------------------------------------
    // Test: null trade in payload → dead letter
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Null trade in TradeEvent: routes to DLQ")
    void nullTrade_routesToDeadLetter() throws Exception {
        TradeEvent event = new TradeEvent();
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-NULL");
        header.setEventType(EventType.NEW_TRADE);
        event.setMessageHeader(header);
        event.setTrade(null); // deliberately null

        consumer.onMessage(toJson(event));

        verify(deadLetterProducer).sendDeadLetter(any(), any());
        verifyNoInteractions(stpRulesEngine, stpValidatedProducer,
                confirmationService, stpExceptionMapper, exceptionCreatedProducer);
    }

    // -----------------------------------------------------------------------
    // Test: multiple STP failures → one ExceptionEvent published per failure
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Multiple STP failures: one ExceptionEvent published per failure")
    void multipleSTPFailures_publishOneEventEach() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-006", 1, EventType.NEW_TRADE);
        when(confirmationRepository.findLatestForTrade("TRD-006")).thenReturn(Optional.empty());

        StpValidationResult multiFailResult = new StpValidationResult();
        multiFailResult.addFailure(ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_NOTIONAL, "notional null", "notionalAmount");
        multiFailResult.addFailure(ExceptionCategory.BUSINESS_RULE_FAILURE,
                ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE, "cp ineligible", "parties");
        when(stpRulesEngine.evaluate(any())).thenReturn(multiFailResult);

        ExceptionEvent ex1 = new ExceptionEvent(new MessageHeader(), null);
        ExceptionEvent ex2 = new ExceptionEvent(new MessageHeader(), null);
        when(stpExceptionMapper.mapToExceptionEvents(any(), any(), any()))
                .thenReturn(List.of(ex1, ex2));

        consumer.onMessage(toJson(event));

        ArgumentCaptor<ExceptionEvent> captor = ArgumentCaptor.forClass(ExceptionEvent.class);
        verify(exceptionCreatedProducer, times(2)).sendExceptionCreated(eq("TRD-006"), captor.capture());
        assertThat(captor.getAllValues()).containsExactly(ex1, ex2);
        verifyNoInteractions(confirmationService, deadLetterProducer);
    }

    // -----------------------------------------------------------------------
    // Test: AMEND_TRADE, different version not yet processed — allowed through
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AMEND_TRADE: different version processed — idempotency allows through")
    void amendTrade_differentVersion_allowedThrough() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-007", 2, EventType.AMEND_TRADE);

        ConfirmationDocument existing = new ConfirmationDocument();
        existing.setTradeId("TRD-007");
        existing.setTradeVersion(1); // version 1 already confirmed, version 2 is new
        when(confirmationRepository.findLatestForTrade("TRD-007")).thenReturn(Optional.of(existing));
        when(stpRulesEngine.evaluate(any())).thenReturn(passedResult());

        consumer.onMessage(toJson(event));

        verify(stpRulesEngine).evaluate(any(TradeEvent.class));
        verify(stpValidatedProducer).sendStpValidated(eq("TRD-007"), any());
        verify(confirmationService).processConfirmation(any(), any());
    }

    // -----------------------------------------------------------------------
    // Audit C1-03: durable IdempotencyKey guard (duplicate exception storms)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Idempotency key: duplicate delivery of an STP-failed trade publishes exceptions only once")
    void duplicateDelivery_stpFailed_publishesExceptionsOnce() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-020", 1, EventType.NEW_TRADE);
        when(confirmationRepository.findLatestForTrade("TRD-020")).thenReturn(Optional.empty());
        when(stpRulesEngine.evaluate(any())).thenReturn(failedResult());
        ExceptionEvent mockException = new ExceptionEvent(new MessageHeader(), null);
        when(stpExceptionMapper.mapToExceptionEvents(any(), any(), any())).thenReturn(List.of(mockException));
        // 1st delivery: not seen before; 2nd delivery (Kafka redelivery): key already recorded
        when(idempotencyRepository.isProcessed(any(IdempotencyKey.class))).thenReturn(false, true);

        String payload = toJson(event);
        consumer.onMessage(payload);
        consumer.onMessage(payload);

        verify(stpRulesEngine, times(1)).evaluate(any(TradeEvent.class));
        verify(exceptionCreatedProducer, times(1)).sendExceptionCreated(eq("TRD-020"), eq(mockException));
        verify(idempotencyRepository, times(1)).record(any(IdempotencyKey.class));
    }

    @Test
    @DisplayName("Idempotency key: already-recorded message is skipped before STP runs")
    void idempotencyKeyAlreadyRecorded_skips() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-021", 1, EventType.NEW_TRADE);
        when(idempotencyRepository.isProcessed(any(IdempotencyKey.class))).thenReturn(true);

        consumer.onMessage(toJson(event));

        verify(idempotencyRepository, never()).record(any());
        verifyNoInteractions(stpRulesEngine, stpValidatedProducer, confirmationService,
                stpExceptionMapper, exceptionCreatedProducer, deadLetterProducer);
    }

    @Test
    @DisplayName("Idempotency key: recorded with the message's own identity after successful processing")
    void successfulProcessing_recordsKeyForThisMessage() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-022", 1, EventType.NEW_TRADE);
        when(confirmationRepository.findLatestForTrade("TRD-022")).thenReturn(Optional.empty());
        when(stpRulesEngine.evaluate(any())).thenReturn(passedResult());

        consumer.onMessage(toJson(event));

        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotencyRepository).record(captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                IdempotencyKey.of("TRD-022", "MSG-001", SchemaVersions.CURRENT, EventType.NEW_TRADE));
    }

    @Test
    @DisplayName("Idempotency key: NOT recorded when processing fails, so redelivery/replay can retry")
    void processingFailure_doesNotRecordKey() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-023", 1, EventType.NEW_TRADE);
        when(confirmationRepository.findLatestForTrade("TRD-023")).thenReturn(Optional.empty());
        when(stpRulesEngine.evaluate(any())).thenThrow(new IllegalStateException("boom"));

        consumer.onMessage(toJson(event));

        verify(idempotencyRepository, never()).record(any());
        verify(deadLetterProducer).sendDeadLetter(any(), any(DeadLetterEvent.class));
    }

    @Test
    @DisplayName("Idempotency key: REPLAY bypasses the key check")
    void replay_bypassesIdempotencyKeyCheck() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-024", 1, EventType.REPLAY);
        when(stpRulesEngine.evaluate(any())).thenReturn(passedResult());

        consumer.onMessage(toJson(event));

        verify(idempotencyRepository, never()).isProcessed(any());
        verify(stpRulesEngine).evaluate(any(TradeEvent.class));
    }

    @Test
    @DisplayName("Missing messageId: routed to DLQ (cannot build an idempotency key)")
    void missingMessageId_routesToDeadLetter() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-025", 1, EventType.NEW_TRADE);
        event.getMessageHeader().setMessageId(null);

        consumer.onMessage(toJson(event));

        verify(deadLetterProducer).sendDeadLetter(any(), any(DeadLetterEvent.class));
        verifyNoInteractions(stpRulesEngine, stpValidatedProducer, confirmationService,
                stpExceptionMapper, exceptionCreatedProducer, idempotencyRepository);
    }

    // -----------------------------------------------------------------------
    // Audit C1-04: SchemaVersionRouter is used for deserialisation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Unsupported schemaVersion: DLQ with SCHEMA_VERSION_UNSUPPORTED, non-replayable, no processing")
    void unsupportedSchemaVersion_routesToDeadLetter() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-030", 1, EventType.NEW_TRADE);
        event.getMessageHeader().setSchemaVersion("capstone-fpml-json-9.9");

        consumer.onMessage(toJson(event));

        ArgumentCaptor<DeadLetterEvent> captor = ArgumentCaptor.forClass(DeadLetterEvent.class);
        verify(deadLetterProducer).sendDeadLetter(any(), captor.capture());
        assertThat(captor.getValue().getFailureReason()).startsWith("SCHEMA_VERSION_UNSUPPORTED");
        assertThat(captor.getValue().isReplayable()).isFalse();
        assertThat(captor.getValue().getOriginalMessageHeader()).isNotNull();
        verifyNoInteractions(stpRulesEngine, stpValidatedProducer, confirmationService,
                stpExceptionMapper, exceptionCreatedProducer, confirmationRepository, idempotencyRepository);
    }

    @Test
    @DisplayName("Schema v1.1 (reserved migration path) is accepted and processed")
    void schemaV1_1_isRoutedAndProcessed() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-031", 1, EventType.NEW_TRADE);
        event.getMessageHeader().setSchemaVersion(SchemaVersions.V1_1);
        when(confirmationRepository.findLatestForTrade("TRD-031")).thenReturn(Optional.empty());
        when(stpRulesEngine.evaluate(any())).thenReturn(passedResult());

        consumer.onMessage(toJson(event));

        verify(stpRulesEngine).evaluate(any(TradeEvent.class));
        verifyNoInteractions(deadLetterProducer);
    }

    @Test
    @DisplayName("Replay identity (eventType, replayCount) survives onto stp.validated")
    void replay_keepsReplayIdentityOnStpValidated() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-030", 1, EventType.REPLAY);
        event.getMessageHeader().setReplayCount(2);
        when(stpRulesEngine.evaluate(any())).thenReturn(passedResult());

        consumer.onMessage(toJson(event));

        ArgumentCaptor<StpValidatedEvent> captor = ArgumentCaptor.forClass(StpValidatedEvent.class);
        verify(stpValidatedProducer).sendStpValidated(eq("TRD-030"), captor.capture());
        assertThat(captor.getValue().getMessageHeader().getEventType()).isEqualTo(EventType.REPLAY);
        assertThat(captor.getValue().getMessageHeader().getReplayCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Dead letters are keyed by tradeId so workitem-service can match the replay outcome")
    void deadLetter_isKeyedByTradeId() throws Exception {
        TradeEvent event = buildTradeEvent("TRD-031", 1, EventType.NEW_TRADE);
        when(confirmationRepository.findLatestForTrade("TRD-031")).thenReturn(Optional.empty());
        when(stpRulesEngine.evaluate(any())).thenThrow(new IllegalStateException("boom"));

        consumer.onMessage(toJson(event));

        verify(deadLetterProducer).sendDeadLetter(eq("TRD-031"), any(DeadLetterEvent.class));
    }
}
