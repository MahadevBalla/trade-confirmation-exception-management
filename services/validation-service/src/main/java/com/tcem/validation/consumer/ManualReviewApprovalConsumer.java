package com.tcem.validation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.event.WorkItemEvent;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.workitem.WorkItem;
import com.tcem.common.routing.SchemaVersionRouter;
import com.tcem.validation.config.ValidationProperties;
import com.tcem.validation.confirmation.ConfirmationService;
import com.tcem.validation.producer.DeadLetterProducer;
import com.tcem.validation.repository.HeldTradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Generates the confirmation for a trade a reviewer approved on the manual review queue.
 * <p>
 * Reacts to {@code workitem.linked} events for a closed MANUAL_REVIEW item with decision APPROVED; every other
 * work-item event is ignored. Redelivery is safe: {@link ConfirmationService} suppresses a second confirmation
 * with an unchanged fingerprint. Publishing that confirmation is what makes workitem-service close the trade's
 * remaining open work items.
 * <p>
 * A trade that cannot be confirmed is dead-lettered as non-replayable rather than dropped, because the approved
 * item is already closed and an operator has to pick it up.
 */
@Service
public class ManualReviewApprovalConsumer {

    private static final Logger log = LoggerFactory.getLogger(ManualReviewApprovalConsumer.class);

    private final ObjectMapper objectMapper;
    private final SchemaVersionRouter schemaVersionRouter;
    private final HeldTradeRepository heldTradeRepository;
    private final ConfirmationService confirmationService;
    private final DeadLetterProducer deadLetterProducer;
    private final ValidationProperties properties;

    public ManualReviewApprovalConsumer(ObjectMapper objectMapper,
                                        HeldTradeRepository heldTradeRepository,
                                        ConfirmationService confirmationService,
                                        DeadLetterProducer deadLetterProducer,
                                        ValidationProperties properties) {
        this.objectMapper = objectMapper;
        this.schemaVersionRouter = new SchemaVersionRouter(objectMapper);
        this.heldTradeRepository = heldTradeRepository;
        this.confirmationService = confirmationService;
        this.deadLetterProducer = deadLetterProducer;
        this.properties = properties;
    }

    @KafkaListener(
            topics = TopicNames.WORKITEM_LINKED,
            groupId = "${spring.kafka.consumer.group-id}-manual-review"
    )
    public void onMessage(String payload) {
        WorkItemEvent event;
        try {
            event = objectMapper.readValue(payload, WorkItemEvent.class);
        } catch (Exception ex) {
            log.error("Discarding unparseable record on workitem.linked: {}", ex.getMessage());
            return;
        }
        if (!isApprovedReview(event.getWorkItem())) {
            return;
        }

        String tradeId = event.getWorkItem().getTradeId();
        log.info("Manual review approved [tradeId={}, workItemId={}] — generating confirmation",
                tradeId, event.getWorkItem().getWorkItemId());
        try {
            Optional<String> held = tradeId != null ? heldTradeRepository.find(tradeId) : Optional.empty();
            if (held.isEmpty()) {
                // E.g. a schema-invalid trade: it failed in ingestion and was never enriched.
                deadLetter(tradeId, event, payload, null,
                        "Manual review approved but no enriched trade is held for this trade id");
                return;
            }
            TradeEvent trade = schemaVersionRouter.route(held.get());
            confirmationService.processConfirmation(trade.getMessageHeader(), trade.getTrade());
        } catch (Exception ex) {
            log.error("Failed to confirm approved trade [tradeId={}]: {}", tradeId, ex.getMessage(), ex);
            deadLetter(tradeId, event, payload, ex, "Manual review approved but confirmation failed: " + ex.getMessage());
        }
    }

    private static boolean isApprovedReview(WorkItem item) {
        return item != null
                && "MANUAL_REVIEW".equals(item.getType())
                && "DONE".equals(item.getStatus())
                && "APPROVED".equals(item.getReviewDecision());
    }

    private void deadLetter(String tradeId, WorkItemEvent event, String payload, Exception ex, String reason) {
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        header.setCorrelationId(event.getMessageHeader() != null ? event.getMessageHeader().getCorrelationId() : tradeId);
        header.setEventType(EventType.NEW_TRADE);
        header.setSchemaVersion(SchemaVersions.CURRENT);
        header.setCreatedTimestamp(Instant.now());
        header.setSourceService(properties.getServiceName());

        deadLetterProducer.sendDeadLetter(tradeId, DeadLetterEvent.builder()
                .messageHeader(header)
                .originalMessageHeader(event.getMessageHeader())
                .originalPayload(payload)
                .failedOnTopic(TopicNames.WORKITEM_LINKED)
                .failedInService(properties.getServiceName())
                .failureReason(reason)
                .exceptionClass(ex != null ? ex.getClass().getName() : null)
                .failedAt(Instant.now())
                .replayable(false)
                .build());
    }
}
