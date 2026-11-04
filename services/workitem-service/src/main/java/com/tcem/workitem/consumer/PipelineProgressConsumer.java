package com.tcem.workitem.consumer;

import com.tcem.common.constants.TopicNames;
import com.tcem.workitem.service.ConfirmationService;
import com.tcem.workitem.service.ReplayOutcomeService;
import com.tcem.workitem.service.TradeCancellationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Watches the main pipeline topics to learn the outcome of replays (FR12), and records confirmations
 * ('confirmation.generated') in the audit trail while closing the trade's open work items. Cancelled outcomes
 * on 'stp.validated' cancel the trade's open work items.
 */
@Component
public class PipelineProgressConsumer {

    private static final Logger log = LoggerFactory.getLogger(PipelineProgressConsumer.class);

    private final ReplayOutcomeService replayOutcomeService;
    private final ConfirmationService confirmationService;
    private final TradeCancellationService tradeCancellationService;

    public PipelineProgressConsumer(ReplayOutcomeService replayOutcomeService, ConfirmationService confirmationService,
                                    TradeCancellationService tradeCancellationService) {
        this.replayOutcomeService = replayOutcomeService;
        this.confirmationService = confirmationService;
        this.tradeCancellationService = tradeCancellationService;
    }

    @KafkaListener(
            topics = {TopicNames.TRADE_ACCEPTED, TopicNames.TRADE_ENRICHED, TopicNames.STP_VALIDATED,
                    TopicNames.EXCEPTION_CREATED, TopicNames.CONFIRMATION_GENERATED},
            groupId = "${spring.kafka.consumer.group-id:workitem-service-group}-progress",
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            if (TopicNames.CONFIRMATION_GENERATED.equals(record.topic())) {
                confirmationService.onConfirmation(record.key(), record.value());
            }
            if (TopicNames.STP_VALIDATED.equals(record.topic())) {
                tradeCancellationService.onStpValidated(record.key(), record.value());
            }
            if (record.key() != null) {
                replayOutcomeService.onPipelineMessage(record.topic(), record.key(), record.value(),
                        ConsumerRecords.hasReplayHeader(record));
            }
        } catch (Exception ex) {
            log.error("Unhandled exception while processing '{}' record [key='{}', offset={}]: {}",
                    record.topic(), record.key(), record.offset(), ex.getMessage(), ex);
        }
    }
}
