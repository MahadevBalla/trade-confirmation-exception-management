package com.tcem.workitem.consumer;

import com.tcem.common.constants.TopicNames;
import com.tcem.workitem.service.DlqService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the 'deadletter.trade' topic (FR12). Hands each DeadLetterEvent to {@link DlqService}.
 */
@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    private final DlqService dlqService;

    public DeadLetterConsumer(DlqService dlqService) {
        this.dlqService = dlqService;
    }

    @KafkaListener(
            topics = TopicNames.DEADLETTER_TRADE,
            groupId = "${spring.kafka.consumer.group-id:workitem-service-group}",
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        log.info("Received dead letter [key='{}', partition={}, offset={}]",
                record.key(), record.partition(), record.offset());
        try {
            dlqService.handleDeadLetter(record.key(), record.value());
        } catch (Exception ex) {
            log.error("Unhandled exception while processing dead letter [key='{}', partition={}, offset={}]: {}",
                    record.key(), record.partition(), record.offset(), ex.getMessage(), ex);
        }
    }
}
