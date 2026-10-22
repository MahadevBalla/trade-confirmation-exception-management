package com.tcem.validation.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.ConfirmationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Producer for publishing generated confirmations to the {@code confirmation.generated} topic (FR6).
 * <p>
 * Dispatched by the {@code ConfirmationService} after a confirmation record has been persisted to
 * CouchDB. Not dispatched when duplicate suppression is triggered on an {@code AMEND_TRADE} with
 * an unchanged confirmable fingerprint.
 * </p>
 */
@Service
public class ConfirmationGeneratedProducer {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationGeneratedProducer.class);

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper;

    public ConfirmationGeneratedProducer(KafkaOperations<String, String> kafkaOperations, ObjectMapper objectMapper) {
        this.kafkaOperations = kafkaOperations;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a {@link ConfirmationEvent} to the {@code confirmation.generated} topic.
     *
     * @param tradeId partition key; falls back to {@code "UNKNOWN_CONFIRMATION"} when null
     * @param event   confirmation event envelope
     * @return CompletableFuture with the Kafka send result
     */
    public CompletableFuture<SendResult<String, String>> sendConfirmationGenerated(String tradeId, ConfirmationEvent event) {
        String key = tradeId != null ? tradeId : "UNKNOWN_CONFIRMATION";
        log.debug("Dispatching confirmation generated event to topic '{}' with key '{}'",
                TopicNames.CONFIRMATION_GENERATED, key);
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            return kafkaOperations.send(TopicNames.CONFIRMATION_GENERATED, key, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish confirmation generated event for trade '{}' to '{}': {}",
                                    key, TopicNames.CONFIRMATION_GENERATED, ex.getMessage());
                        } else {
                            log.info("Successfully published confirmation generated event for trade '{}' to '{}' [partition={}, offset={}]",
                                    key,
                                    TopicNames.CONFIRMATION_GENERATED,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception ex) {
            log.warn("Failed to serialize or dispatch confirmation generated event for trade '{}' to topic '{}': {}",
                    key, TopicNames.CONFIRMATION_GENERATED, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }
}

