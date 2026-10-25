package com.tcem.exception.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Producer for dispatching fatal / unrecoverable messages to the
 * {@code deadletter.trade} Kafka topic (FR12).
 * <p>
 * Used by the exception-service when processing cannot proceed and the message
 * must be parked for later replay or manual intervention. The workitem-service
 * consumes this topic to store DLQ entries in CouchDB and optionally create
 * DATA_FIX work items.
 * </p>
 */
@Service
public class DeadLetterProducer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterProducer.class);

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper;

    public DeadLetterProducer(KafkaOperations<String, String> kafkaOperations,
                              ObjectMapper objectMapper) {
        this.kafkaOperations = kafkaOperations;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a {@link DeadLetterEvent} to the {@code deadletter.trade} topic.
     *
     * @param key             partition key (typically tradeId); falls back to
     *                        {@code "DLQ_" + currentTimeMillis} if null or blank
     * @param deadLetterEvent dead-letter event envelope with failure details
     * @return CompletableFuture with the send result
     */
    public CompletableFuture<SendResult<String, String>> sendDeadLetter(
            String key, DeadLetterEvent deadLetterEvent) {

        String partitionKey = (key != null && !key.isBlank())
                ? key
                : "DLQ_" + System.currentTimeMillis();

        log.warn("Dispatching dead-letter event to topic '{}' with key '{}'",
                TopicNames.DEADLETTER_TRADE, partitionKey);

        try {
            String jsonPayload = objectMapper.writeValueAsString(deadLetterEvent);
            return kafkaOperations.send(TopicNames.DEADLETTER_TRADE, partitionKey, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish dead-letter event with key '{}' to '{}': {}",
                                    partitionKey, TopicNames.DEADLETTER_TRADE, ex.getMessage());
                        } else {
                            log.warn("Published dead-letter event with key '{}' to '{}' " +
                                            "[partition={}, offset={}]",
                                    partitionKey,
                                    TopicNames.DEADLETTER_TRADE,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to serialize or dispatch dead-letter event with key '{}' to '{}': {}",
                    partitionKey, TopicNames.DEADLETTER_TRADE, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }
}

