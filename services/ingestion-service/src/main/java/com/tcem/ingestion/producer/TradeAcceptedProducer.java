package com.tcem.ingestion.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.model.message.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Producer for dispatching schema-validated canonical TradeEvents to 'trade.accepted' (FR2).
 * Consumed downstream by enrichment-service.
 */
@Service
public class TradeAcceptedProducer {

    private static final Logger log = LoggerFactory.getLogger(TradeAcceptedProducer.class);

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper;

    public TradeAcceptedProducer(KafkaOperations<String, String> kafkaOperations, ObjectMapper objectMapper) {
        this.kafkaOperations = kafkaOperations;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a canonical TradeEvent to the 'trade.accepted' topic.
     *
     * @param tradeId    partition key
     * @param tradeEvent canonical trade event
     * @return CompletableFuture with the send result
     */
    public CompletableFuture<SendResult<String, String>> sendTradeAccepted(String tradeId, TradeEvent tradeEvent) {
        log.debug("Dispatching trade event to topic '{}' with key '{}'", TopicNames.TRADE_ACCEPTED, tradeId);
        try {
            String jsonPayload = objectMapper.writeValueAsString(tradeEvent);
            return kafkaOperations.send(TopicNames.TRADE_ACCEPTED, tradeId, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish trade '{}' to '{}': {}",
                                    tradeId, TopicNames.TRADE_ACCEPTED, ex.getMessage());
                        } else {
                            log.info("Successfully published trade '{}' to '{}' [partition={}, offset={}]",
                                    tradeId,
                                    TopicNames.TRADE_ACCEPTED,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception ex) {
            log.warn("Failed to serialize or dispatch trade '{}' to topic '{}': {}",
                    tradeId, TopicNames.TRADE_ACCEPTED, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }
}
