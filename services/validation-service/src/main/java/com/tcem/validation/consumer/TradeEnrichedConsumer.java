package com.tcem.validation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.model.message.MessageHeader;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kafka consumer for the {@code trade.enriched} topic — entry point for the full
 * STP validation pipeline (FR5, FR6, FR7).
 *
 * <h3>Processing flow</h3>
 * <ol>
 *   <li>Deserialise the incoming JSON payload to a {@link TradeEvent}.</li>
 *   <li>Extract the {@link MessageHeader} and {@link Trade}.</li>
 *   <li>Idempotency guard: if the trade version has already been confirmed, skip
 *       reprocessing (unless the event type is {@code REPLAY}).</li>
 *   <li>Handle {@code CANCEL_TRADE}: log and skip — no STP, no confirmation.</li>
 *   <li>Run the {@link StpRulesEngine} against the full {@link TradeEvent}.</li>
 *   <li>Always publish the STP decision to {@code stp.validated}.</li>
 *   <li>On STP pass: delegate to {@link ConfirmationService} (fingerprint check,
 *       persistence, confirmation.generated).</li>
 *   <li>On STP fail: map each failure to an {@link ExceptionEvent} via
 *       {@link StpExceptionMapper} and publish each to {@code exception.created}.</li>
 *   <li>On irrecoverable parse / unexpected error: publish to {@code deadletter.trade}
 *       via {@link DeadLetterProducer}.</li>
 * </ol>
 *
 * <h3>Idempotency</h3>
 * For {@code NEW_TRADE} and {@code AMEND_TRADE} events the consumer checks whether a
 * {@link ConfirmationDocument} already exists for the exact {@code tradeId + tradeVersion}
 * combination. If found, the message is skipped to prevent duplicate confirmations.
 * {@code REPLAY} events bypass this guard so an operator can force reprocessing after
 * a data fix.
 *
 * <h3>Thread safety</h3>
 * Stateless Spring {@code @Service}; thread-safe as long as all injected collaborators
 * are themselves thread-safe (all are Spring singletons with no mutable state).
 */
@Service
public class TradeEnrichedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeEnrichedConsumer.class);

    private final ObjectMapper objectMapper;
    private final StpRulesEngine stpRulesEngine;
    private final StpValidatedProducer stpValidatedProducer;
    private final ConfirmationService confirmationService;
    private final StpExceptionMapper stpExceptionMapper;
    private final ExceptionCreatedProducer exceptionCreatedProducer;
    private final DeadLetterProducer deadLetterProducer;
    private final ConfirmationCouchDbRepository confirmationRepository;
    private final ValidationProperties validationProperties;

    public TradeEnrichedConsumer(ObjectMapper objectMapper,
                                 StpRulesEngine stpRulesEngine,
                                 StpValidatedProducer stpValidatedProducer,
                                 ConfirmationService confirmationService,
                                 StpExceptionMapper stpExceptionMapper,
                                 ExceptionCreatedProducer exceptionCreatedProducer,
                                 DeadLetterProducer deadLetterProducer,
                                 ConfirmationCouchDbRepository confirmationRepository,
                                 ValidationProperties validationProperties) {
        this.objectMapper = objectMapper;
        this.stpRulesEngine = stpRulesEngine;
        this.stpValidatedProducer = stpValidatedProducer;
        this.confirmationService = confirmationService;
        this.stpExceptionMapper = stpExceptionMapper;
        this.exceptionCreatedProducer = exceptionCreatedProducer;
        this.deadLetterProducer = deadLetterProducer;
        this.confirmationRepository = confirmationRepository;
        this.validationProperties = validationProperties;
    }

    /**
     * Processes a single {@code trade.enriched} message.
     *
     * @param payload raw JSON string of the Kafka message value
     */
    @KafkaListener(
            topics = TopicNames.TRADE_ENRICHED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(String payload) {
        // -----------------------------------------------------------------------
        // Step 1: Deserialise
        // -----------------------------------------------------------------------
        TradeEvent tradeEvent;
        try {
            tradeEvent = objectMapper.readValue(payload, TradeEvent.class);
        } catch (Exception ex) {
            log.error("Failed to deserialise trade.enriched payload — routing to DLQ: {}", ex.getMessage());
            sendToDeadLetter(null, payload, ex, "Deserialization failure");
            return;
        }

        MessageHeader header = tradeEvent.getMessageHeader();
        Trade trade = tradeEvent.getTrade();

        if (header == null || trade == null) {
            log.error("Received trade.enriched message with null header or trade — routing to DLQ");
            sendToDeadLetter(null, payload, null, "Null header or trade in TradeEvent");
            return;
        }

        String tradeId = trade.getTradeId();
        int tradeVersion = trade.getTradeVersion();
        EventType eventType = header.getEventType();

        log.info("Received trade.enriched [tradeId={}, version={}, eventType={}]",
                tradeId, tradeVersion, eventType);

        try {
            // -----------------------------------------------------------------------
            // Step 2: CANCEL_TRADE — no STP evaluation, no confirmation
            // -----------------------------------------------------------------------
            if (EventType.CANCEL_TRADE == eventType) {
                log.info("Skipping STP evaluation for CANCEL_TRADE [tradeId={}, version={}]",
                        tradeId, tradeVersion);
                return;
            }

            // -----------------------------------------------------------------------
            // Step 3: Idempotency guard
            //         REPLAY events always bypass this check so operators can force
            //         reprocessing after a data fix.
            // -----------------------------------------------------------------------
            if (EventType.REPLAY != eventType) {
                Optional<ConfirmationDocument> existing =
                        confirmationRepository.findLatestForTrade(tradeId);
                if (existing.isPresent()
                        && existing.get().getTradeVersion() == tradeVersion) {
                    log.info("Idempotency: trade already processed [tradeId={}, version={}] — skipping",
                            tradeId, tradeVersion);
                    return;
                }
            }

            // -----------------------------------------------------------------------
            // Step 4: STP evaluation
            // -----------------------------------------------------------------------
            StpValidationResult stpResult = stpRulesEngine.evaluate(tradeEvent);

            // -----------------------------------------------------------------------
            // Step 5: Publish STP decision (always, regardless of outcome)
            // -----------------------------------------------------------------------
            MessageHeader stpOutboundHeader = buildOutboundHeader(header.getCorrelationId());
            StpValidatedEvent stpEvent = StpValidatedEvent.of(
                    stpOutboundHeader, tradeId, tradeVersion, stpResult);
            stpValidatedProducer.sendStpValidated(tradeId, stpEvent);

            // -----------------------------------------------------------------------
            // Step 6a: STP passed → generate and publish confirmation
            // -----------------------------------------------------------------------
            if (stpResult.isStpPassed()) {
                log.info("STP PASSED for tradeId={}, version={} — generating confirmation",
                        tradeId, tradeVersion);
                confirmationService.processConfirmation(header, trade);

            // -----------------------------------------------------------------------
            // Step 6b: STP failed → map each failure to ExceptionEvent and publish
            // -----------------------------------------------------------------------
            } else {
                log.info("STP FAILED for tradeId={}, version={} with {} failures — routing exceptions",
                        tradeId, tradeVersion, stpResult.getFailures().size());
                List<ExceptionEvent> exceptionEvents =
                        stpExceptionMapper.mapToExceptionEvents(header, trade, stpResult);
                for (ExceptionEvent exceptionEvent : exceptionEvents) {
                    exceptionCreatedProducer.sendExceptionCreated(tradeId, exceptionEvent);
                }
            }

        } catch (Exception ex) {
            log.error("Unexpected error processing tradeId={}, version={} — routing to DLQ: {}",
                    tradeId, tradeVersion, ex.getMessage(), ex);
            sendToDeadLetter(header, payload, ex, "Unexpected processing failure");
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a fresh outbound {@link MessageHeader} for events produced by this service.
     */
    private MessageHeader buildOutboundHeader(String correlationId) {
        MessageHeader h = new MessageHeader();
        h.setMessageId("MSG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        h.setCorrelationId(correlationId);
        h.setSchemaVersion(SchemaVersions.CURRENT);
        h.setCreatedTimestamp(Instant.now());
        h.setSourceService(validationProperties.getServiceName());
        return h;
    }

    /**
     * Routes the failing message to the dead-letter topic.
     *
     * @param originalHeader header from the original message (may be null on parse failure)
     * @param originalPayload raw JSON that caused the failure
     * @param ex              the exception, if one was thrown (may be null)
     * @param failureReason   human-readable summary of why the message is being dead-lettered
     */
    private void sendToDeadLetter(MessageHeader originalHeader,
                                  String originalPayload,
                                  Exception ex,
                                  String failureReason) {
        try {
            String dlqKey = originalHeader != null && originalHeader.getCorrelationId() != null
                    ? originalHeader.getCorrelationId()
                    : null;

            String stackTrace = null;
            String exceptionClass = null;
            if (ex != null) {
                exceptionClass = ex.getClass().getName();
                StringBuilder sb = new StringBuilder();
                for (StackTraceElement el : ex.getStackTrace()) {
                    sb.append(el.toString()).append("\n");
                    if (sb.length() > 2000) break;
                }
                stackTrace = sb.toString();
            }

            MessageHeader dlqHeader = buildOutboundHeader(
                    originalHeader != null ? originalHeader.getCorrelationId() : null);

            DeadLetterEvent dlqEvent = DeadLetterEvent.builder()
                    .messageHeader(dlqHeader)
                    .originalMessageHeader(originalHeader)
                    .originalPayload(originalPayload)
                    .failedOnTopic(TopicNames.TRADE_ENRICHED)
                    .failedInService(validationProperties.getServiceName())
                    .failureReason(failureReason)
                    .exceptionClass(exceptionClass)
                    .stackTraceExcerpt(stackTrace)
                    .failedAt(Instant.now())
                    .replayable(ex == null || !(ex instanceof com.fasterxml.jackson.core.JsonParseException))
                    .build();

            deadLetterProducer.sendDeadLetter(dlqKey, dlqEvent);

        } catch (Exception dlqEx) {
            // Last resort — if DLQ itself fails, log and continue; don't throw
            log.error("Failed to publish to dead-letter topic: {}", dlqEx.getMessage(), dlqEx);
        }
    }
}
