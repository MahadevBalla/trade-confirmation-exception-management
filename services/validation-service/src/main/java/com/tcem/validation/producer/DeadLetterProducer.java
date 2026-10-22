package com.tcem.validation.producer;

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
 * Producer for dispatching irrecoverable processing failures to the {@code deadletter.trade} topic (FR12).
 * <p>
 * Any component within the {@code validation-service} that encounters an unrecoverable error
 * (deserialization failure, unexpected exception in the consumer pipeline, etc.) should route
 * the original message through this producer. The {@code workitem-service} consumes the DLQ topic
 * to store entries in CouchDB and surface a replay button in the Operations UI.
 * </p>
 */
@Service
public class DeadLetterProducer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterProducer.class);

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper;

    public DeadLetterProducer(KafkaOperations<String, String> kafkaOperations, ObjectMapper objectMapper) {
        this.kafkaOperations = kafkaOperations;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a {@link DeadLetterEvent} to the {@code deadletter.trade} topic.
     *
     * @param key             partition key (typically the {@code tradeId} or the original message key);
     *                        falls back to a timestamped {@code "DLQ_<millis>"} key when null
     * @param deadLetterEvent dead-letter event envelope carrying the original payload and failure context
     * @return CompletableFuture with the Kafka send result
     */
    public CompletableFuture<SendResult<String, String>> sendDeadLetter(String key, DeadLetterEvent deadLetterEvent) {
        String partitionKey = key != null ? key : "DLQ_" + System.currentTimeMillis();
        log.warn("Dispatching dead letter event to topic '{}' with key '{}'", TopicNames.DEADLETTER_TRADE, partitionKey);
        try {
            String jsonPayload = objectMapper.writeValueAsString(deadLetterEvent);
            return kafkaOperations.send(TopicNames.DEADLETTER_TRADE, partitionKey, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish dead letter event with key '{}' to '{}': {}",
                                    partitionKey, TopicNames.DEADLETTER_TRADE, ex.getMessage());
                        } else {
                            log.warn("Successfully published dead letter event with key '{}' to '{}' [partition={}, offset={}]",
                                    partitionKey,
                                    TopicNames.DEADLETTER_TRADE,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to serialize or dispatch dead letter event with key '{}' to topic '{}': {}",
                    partitionKey, TopicNames.DEADLETTER_TRADE, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }
}

