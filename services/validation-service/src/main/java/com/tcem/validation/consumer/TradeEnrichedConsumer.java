package com.tcem.validation.consumer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.idempotency.IdempotencyKey;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.trade.Trade;
import com.tcem.common.routing.SchemaVersionRouter;
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

/**
 * Kafka consumer for the {@code trade.enriched} topic — entry point for the full
 * STP validation pipeline (FR5, FR6, FR7).
 *
 * <h3>Processing flow</h3>
 * <ol>
 *   <li>Deserialise the incoming JSON payload to a {@link TradeEvent} through the
 *       {@link SchemaVersionRouter}; unsupported schema versions are dead-lettered with
 *       reason {@code SCHEMA_VERSION_UNSUPPORTED} (non-replayable).</li>
 *   <li>Extract the {@link MessageHeader} and {@link Trade}; messages missing an identity
 *       field (tradeId, messageId, schemaVersion, eventType) are dead-lettered.</li>
 *   <li>Idempotency guard (unless the event type is {@code REPLAY}): skip the message if its
 *       {@link IdempotencyKey} is already recorded, or if the trade version has already
 *       been confirmed.</li>
 *   <li>Handle {@code CANCEL_TRADE}: log and skip — no STP, no confirmation.</li>
 *   <li>Run the {@link StpRulesEngine} against the full {@link TradeEvent}.</li>
 *   <li>Always publish the STP decision to {@code stp.validated}.</li>
 *   <li>On STP pass: delegate to {@link ConfirmationService} (fingerprint check,
 *       persistence, confirmation.generated).</li>
 *   <li>On STP fail: hold the enriched trade in case a reviewer approves it later, then
 *       map each failure to an {@link ExceptionEvent} via
 *       {@link StpExceptionMapper} and publish each to {@code exception.created}.</li>
 *   <li>Record the {@link IdempotencyKey} once every downstream publish has succeeded.</li>
 *   <li>On irrecoverable parse / unexpected error: publish to {@code deadletter.trade}
 *       via {@link DeadLetterProducer} (the key is NOT recorded, so redelivery/replay retries).</li>
 * </ol>
 *
 * <h3>Idempotency</h3>
 * Like the other services, validation uses the shared {@link IdempotencyKey}
 * ({@code tradeId + messageId + schemaVersion + eventType}) persisted in the
 * {@code validation-idempotency} CouchDB database. This is what stops a redelivered message
 * for a trade that failed STP (which has no {@link ConfirmationDocument}) from emitting a
 * second {@code exception.created}. The older "confirmation already exists for this
 * tradeVersion" check is kept as a second line of defence.
 * {@code REPLAY} events bypass both guards so an operator can force reprocessing after
 * a data fix.
 *
 * <h3>Thread safety</h3>
 * Stateless Spring {@code @Service}; thread-safe as long as all injected collaborators
 * are themselves thread-safe (all are Spring singletons with no mutable state).
 */
@Service
public class TradeEnrichedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeEnrichedConsumer.class);

    /** DLQ failure-reason prefix for payloads whose schemaVersion has no registered migration path. */
    static final String SCHEMA_VERSION_UNSUPPORTED = "SCHEMA_VERSION_UNSUPPORTED";

    private final ObjectMapper objectMapper;
    private final SchemaVersionRouter schemaVersionRouter;
    private final StpRulesEngine stpRulesEngine;
    private final StpValidatedProducer stpValidatedProducer;
    private final ConfirmationService confirmationService;
    private final StpExceptionMapper stpExceptionMapper;
    private final ExceptionCreatedProducer exceptionCreatedProducer;
    private final DeadLetterProducer deadLetterProducer;
    private final ConfirmationCouchDbRepository confirmationRepository;
    private final ValidationIdempotencyRepository idempotencyRepository;
    private final HeldTradeRepository heldTradeRepository;
    private final ValidationProperties validationProperties;

    public TradeEnrichedConsumer(ObjectMapper objectMapper,
                                 StpRulesEngine stpRulesEngine,
                                 StpValidatedProducer stpValidatedProducer,
                                 ConfirmationService confirmationService,
                                 StpExceptionMapper stpExceptionMapper,
                                 ExceptionCreatedProducer exceptionCreatedProducer,
                                 DeadLetterProducer deadLetterProducer,
                                 ConfirmationCouchDbRepository confirmationRepository,
                                 ValidationIdempotencyRepository idempotencyRepository,
                                 HeldTradeRepository heldTradeRepository,
                                 ValidationProperties validationProperties) {
        this.objectMapper = objectMapper;
        this.schemaVersionRouter = new SchemaVersionRouter(objectMapper);
        this.stpRulesEngine = stpRulesEngine;
        this.stpValidatedProducer = stpValidatedProducer;
        this.confirmationService = confirmationService;
        this.stpExceptionMapper = stpExceptionMapper;
        this.exceptionCreatedProducer = exceptionCreatedProducer;
        this.deadLetterProducer = deadLetterProducer;
        this.confirmationRepository = confirmationRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.heldTradeRepository = heldTradeRepository;
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
            tradeEvent = schemaVersionRouter.route(payload);
        } catch (SchemaVersionRouter.UnsupportedSchemaVersionException ex) {
            log.warn("Unsupported schemaVersion on trade.enriched — routing to DLQ: {}", ex.getMessage());
            sendToDeadLetter(extractHeader(payload), payload, ex,
                    SCHEMA_VERSION_UNSUPPORTED + ": " + ex.getMessage(), false);
            return;
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

        if (isBlank(trade.getTradeId()) || isBlank(header.getMessageId())
                || isBlank(header.getSchemaVersion()) || header.getEventType() == null) {
            log.error("Received trade.enriched message missing a required identity field — routing to DLQ");
            sendToDeadLetter(header, payload, null,
                    "trade.enriched event is missing a required identity field", false);
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
            IdempotencyKey idempotencyKey = IdempotencyKey.of(
                    tradeId, header.getMessageId(), header.getSchemaVersion(), eventType);

            if (EventType.REPLAY != eventType) {
                if (idempotencyRepository.isProcessed(idempotencyKey)) {
                    log.info("Idempotency: duplicate message [tradeId={}, messageId={}] — skipping",
                            tradeId, header.getMessageId());
                    return;
                }
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
            MessageHeader stpOutboundHeader = buildOutboundHeader(header);
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
                // Before the exceptions go out, so an approval can never overtake the snapshot.
                heldTradeRepository.hold(tradeId, payload);
                List<ExceptionEvent> exceptionEvents =
                        stpExceptionMapper.mapToExceptionEvents(header, trade, stpResult);
                for (ExceptionEvent exceptionEvent : exceptionEvents) {
                    exceptionCreatedProducer.sendExceptionCreated(tradeId, exceptionEvent);
                }
            }

            // -----------------------------------------------------------------------
            // Step 7: Mark this message as processed (only after every publish above
            //         succeeded; a failure falls through to the DLQ catch below and the
            //         key is left unrecorded so a redelivery / replay can retry).
            // -----------------------------------------------------------------------
            idempotencyRepository.record(idempotencyKey);

        } catch (Exception ex) {
            log.error("Unexpected error processing tradeId={}, version={} — routing to DLQ: {}",
                    tradeId, tradeVersion, ex.getMessage(), ex);
            sendToDeadLetter(header, payload, ex, "Unexpected processing failure");
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Best-effort header extraction for DLQ context when the full payload cannot be routed. */
    private MessageHeader extractHeader(String payload) {
        try {
            return objectMapper.readValue(payload, TradeEvent.class).getMessageHeader();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Dead letters are keyed by tradeId (see docs/kafka-topics.md); null when the payload has none. */
    private String extractTradeId(String payload) {
        try {
            String tradeId = objectMapper.readTree(payload).path("trade").path("tradeId").asText(null);
            return isBlank(tradeId) ? null : tradeId;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Builds a fresh outbound {@link MessageHeader} for events produced by this service, keeping the
     * inbound event type and replay count: workitem-service relies on them to detect a replay's outcome.
     */
    private MessageHeader buildOutboundHeader(MessageHeader inbound) {
        MessageHeader h = new MessageHeader();
        h.setMessageId("MSG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        if (inbound != null) {
            h.setCorrelationId(inbound.getCorrelationId());
            h.setEventType(inbound.getEventType());
            h.setReplayCount(inbound.getReplayCount());
        }
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
        sendToDeadLetter(originalHeader, originalPayload, ex, failureReason,
                ex == null || !(ex instanceof com.fasterxml.jackson.core.JsonParseException));
    }

    /**
     * Same as above, with an explicit {@code replayable} flag (e.g. {@code false} for
     * unsupported schema versions, where a replay would fail again).
     */
    private void sendToDeadLetter(MessageHeader originalHeader,
                                  String originalPayload,
                                  Exception ex,
                                  String failureReason,
                                  boolean replayable) {
        try {
            String dlqKey = extractTradeId(originalPayload);

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

            MessageHeader dlqHeader = buildOutboundHeader(originalHeader);

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
                    .replayable(replayable)
                    .build();

            deadLetterProducer.sendDeadLetter(dlqKey, dlqEvent);

        } catch (Exception dlqEx) {
            // Last resort — if DLQ itself fails, log and continue; don't throw
            log.error("Failed to publish to dead-letter topic: {}", dlqEx.getMessage(), dlqEx);
        }
    }
}
