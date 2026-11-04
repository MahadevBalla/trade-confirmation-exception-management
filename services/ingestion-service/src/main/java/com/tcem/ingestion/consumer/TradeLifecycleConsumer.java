package com.tcem.ingestion.consumer;

import com.tcem.common.constants.TopicNames;
import com.tcem.ingestion.service.TradeLifecycleService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Follows the pipeline topics to keep the {@code trades} read model current. Failures propagate, so Kafka's error
 * handler retries them.
 */
@Component
public class TradeLifecycleConsumer {

    private final TradeLifecycleService lifecycleService;

    public TradeLifecycleConsumer(TradeLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @KafkaListener(
            topics = {TopicNames.TRADE_ENRICHED, TopicNames.STP_VALIDATED, TopicNames.EXCEPTION_CREATED,
                    TopicNames.MANUAL_REVIEW_REQUIRED, TopicNames.CONFIRMATION_GENERATED, TopicNames.DEADLETTER_TRADE},
            groupId = "${spring.kafka.consumer.group-id:ingestion-service-group}-lifecycle",
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        lifecycleService.onPipelineEvent(record.topic(), record.key(), record.value());
    }
}
