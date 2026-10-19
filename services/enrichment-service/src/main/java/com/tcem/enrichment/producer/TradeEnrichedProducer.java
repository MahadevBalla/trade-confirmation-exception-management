package com.tcem.enrichment.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.model.message.TradeEvent;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Service;

@Service
public class TradeEnrichedProducer {
    private final KafkaOperations<String, String> kafka;
    private final ObjectMapper objectMapper;
    public TradeEnrichedProducer(KafkaOperations<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka; this.objectMapper = objectMapper;
    }
    public void publish(String tradeId, TradeEvent event) {
        try {
            kafka.send(TopicNames.TRADE_ENRICHED, tradeId, objectMapper.writeValueAsString(event)).join();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize enriched trade", ex);
        }
    }
}
