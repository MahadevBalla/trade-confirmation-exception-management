package com.tcem.enrichment.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.model.message.TradeEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Service;

@Service
public class TradeEnrichedProducer {
    private final KafkaOperations<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final long sendTimeoutMs;
    public TradeEnrichedProducer(KafkaOperations<String, String> kafka, ObjectMapper objectMapper,
                                 @Value("${enrichment.kafka.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.kafka = kafka; this.objectMapper = objectMapper; this.sendTimeoutMs = sendTimeoutMs;
    }
    public void publish(String tradeId, TradeEvent event) {
        try {
            KafkaSends.await(kafka.send(TopicNames.TRADE_ENRICHED, tradeId, objectMapper.writeValueAsString(event)),
                    sendTimeoutMs, "enriched trade " + tradeId);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize enriched trade", ex);
        }
    }
}
