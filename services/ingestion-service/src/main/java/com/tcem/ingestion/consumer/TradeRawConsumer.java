package com.tcem.ingestion.consumer;

import com.tcem.common.constants.TopicNames;
import com.tcem.ingestion.model.IngestionResult;
import com.tcem.ingestion.service.TradeIngestionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer listening to the 'trade.raw' topic (FR1, FR2).
 * Consumes raw trade JSON messages produced by synthetic generators or upstream feeds
 * and hands them over to TradeIngestionService.
 */
@Component
public class TradeRawConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeRawConsumer.class);

    private final TradeIngestionService tradeIngestionService;

    public TradeRawConsumer(TradeIngestionService tradeIngestionService) {
        this.tradeIngestionService = tradeIngestionService;
    }

    /**
     * Consumes an incoming raw trade record from 'trade.raw'.
     *
     * @param record Kafka consumer record with tradeId key and raw JSON value
     */
    @KafkaListener(
            topics = TopicNames.TRADE_RAW,
            groupId = "${spring.kafka.consumer.group-id:ingestion-service-group}",
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String key = record.key();
        String payload = record.value();
        log.info("Received raw trade message from topic '{}' [key='{}', partition={}, offset={}]",
                record.topic(), key, record.partition(), record.offset());

        try {
            IngestionResult result = tradeIngestionService.processRawTrade(key, payload);
            log.info("Finished processing message with key '{}': status={}", key, result.getStatus());
        } catch (Exception ex) {
            log.error("Unhandled exception while consuming message [key='{}', topic='{}', partition={}, offset={}]: {}",
                    key, record.topic(), record.partition(), record.offset(), ex.getMessage(), ex);
        }
    }
}
