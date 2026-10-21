package com.tcem.validation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.trade.Trade;
import com.tcem.validation.config.ValidationProperties;
import com.tcem.validation.confirmation.ConfirmationService;
import com.tcem.validation.exception.StpExceptionMapper;
import com.tcem.validation.model.StpValidatedEvent;
import com.tcem.validation.producer.DeadLetterProducer;
import com.tcem.validation.producer.ExceptionCreatedProducer;
import com.tcem.validation.producer.StpValidatedProducer;
import com.tcem.validation.repository.ConfirmationCouchDbRepository;
import com.tcem.validation.repository.ConfirmationDocument;
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
                confirmationRepository, validationProperties);

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
        verify(exceptionCreatedProducer).sendExceptionCreated(eq("TRD-002"), eq(mockException));
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
}
