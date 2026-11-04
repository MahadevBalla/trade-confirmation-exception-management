package com.tcem.workitem.consumer;

import com.tcem.common.constants.TopicNames;
import com.tcem.workitem.service.ExceptionLinkingService;
import com.tcem.workitem.service.ReplayOutcomeService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for 'manual.review.required': puts the exception on the manual review queue as a
 * MANUAL_REVIEW work item. The payload is an {@code ExceptionEvent}. Like a classified exception, it
 * also proves a replayed 'exception.created' message was processed.
 */
@Component
public class ManualReviewConsumer {

    private static final Logger log = LoggerFactory.getLogger(ManualReviewConsumer.class);

    private final ExceptionLinkingService linkingService;
    private final ReplayOutcomeService replayOutcomeService;

    public ManualReviewConsumer(ExceptionLinkingService linkingService, ReplayOutcomeService replayOutcomeService) {
        this.linkingService = linkingService;
        this.replayOutcomeService = replayOutcomeService;
    }

    @KafkaListener(
            topics = TopicNames.MANUAL_REVIEW_REQUIRED,
            groupId = "${spring.kafka.consumer.group-id:workitem-service-group}-manual-review",
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        log.info("Received manual review request [key='{}', partition={}, offset={}]",
                record.key(), record.partition(), record.offset());
        try {
            linkingService.onManualReview(record.value());
            replayOutcomeService.onPipelineMessage(record.topic(), record.key(), record.value(),
                    ConsumerRecords.hasReplayHeader(record));
        } catch (Exception ex) {
            log.error("Unhandled exception while processing manual review request [key='{}', offset={}]: {}",
                    record.key(), record.offset(), ex.getMessage(), ex);
        }
    }
}
