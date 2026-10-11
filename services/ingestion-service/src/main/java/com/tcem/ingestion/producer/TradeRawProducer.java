package com.tcem.ingestion.producer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.TopicNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service producing synthetic trade messages to the 'trade.raw' Kafka topic (FR1).
 */
@Service
public class TradeRawProducer {

    private static final Logger log = LoggerFactory.getLogger(TradeRawProducer.class);

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper;

    public TradeRawProducer(KafkaOperations<String, String> kafkaOperations, ObjectMapper objectMapper) {
        this.kafkaOperations = kafkaOperations;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a raw JSON payload to the 'trade.raw' Kafka topic using the given key.
     *
     * @param key     partition key (typically tradeId)
     * @param payload raw JSON string
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, String>> sendTradeRaw(String key, String payload) {
        log.debug("Publishing message to topic '{}' with key '{}'", TopicNames.TRADE_RAW, key);
        return kafkaOperations.send(TopicNames.TRADE_RAW, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish message to '{}' with key '{}': {}",
                                TopicNames.TRADE_RAW, key, ex.getMessage());
                    } else {
                        log.debug("Successfully published message to '{}' [partition={}, offset={}]",
                                TopicNames.TRADE_RAW,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Publishes a batch of raw trade JSON payloads to 'trade.raw'.
     *
     * @param payloads list of raw JSON trade payloads
     * @return number of messages successfully dispatched
     */
    public int sendBatch(List<String> payloads) {
        int count = 0;
        for (String payload : payloads) {
            String key = extractKey(payload);
            sendTradeRaw(key, payload);
            count++;
        }
        log.info("Dispatched batch of {} trades to topic '{}'", count, TopicNames.TRADE_RAW);
        return count;
    }

    /**
     * Extracts partition key from payload (tradeId if present, else fallback).
     */
    private String extractKey(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode tradeNode = root.get("trade");
            if (tradeNode != null && tradeNode.hasNonNull("tradeId")) {
                return tradeNode.get("tradeId").asText();
            }
            JsonNode headerNode = root.get("messageHeader");
            if (headerNode != null && headerNode.hasNonNull("messageId")) {
                return headerNode.get("messageId").asText();
            }
        } catch (Exception ignored) {
            // Unparseable JSON, use default random key
        }
        return "UNKNOWN_KEY_" + System.currentTimeMillis();
    }
}
