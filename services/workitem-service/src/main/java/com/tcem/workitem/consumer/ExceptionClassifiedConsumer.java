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
 * Kafka consumer for 'exception.classified' (FR8/FR9 output): creates or links the matching work item (FR10).
 * A classified exception also proves a replayed 'exception.created' message was processed.
 */
@Component
public class ExceptionClassifiedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExceptionClassifiedConsumer.class);

    private final ExceptionLinkingService linkingService;
    private final ReplayOutcomeService replayOutcomeService;

    public ExceptionClassifiedConsumer(ExceptionLinkingService linkingService, ReplayOutcomeService replayOutcomeService) {
        this.linkingService = linkingService;
        this.replayOutcomeService = replayOutcomeService;
    }

    @KafkaListener(
            topics = TopicNames.EXCEPTION_CLASSIFIED,
            groupId = "${spring.kafka.consumer.group-id:workitem-service-group}-exceptions",
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        log.info("Received classified exception [key='{}', partition={}, offset={}]",
                record.key(), record.partition(), record.offset());
        try {
            linkingService.onClassified(record.value());
            replayOutcomeService.onPipelineMessage(record.topic(), record.key(), record.value(),
                    ConsumerRecords.hasReplayHeader(record));
        } catch (Exception ex) {
            log.error("Unhandled exception while processing classified exception [key='{}', offset={}]: {}",
                    record.key(), record.offset(), ex.getMessage(), ex);
        }
    }
}
