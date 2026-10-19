package com.tcem.workitem.producer;

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
 * Publishes workitem-service's own irrecoverable outcomes (e.g. a rejected manual review) to
 * 'deadletter.trade' (FR12), the same topic every other service uses.
 */
@Service
public class DeadLetterPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterPublisher.class);

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper;

    public DeadLetterPublisher(KafkaOperations<String, String> kafkaOperations, ObjectMapper objectMapper) {
        this.kafkaOperations = kafkaOperations;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<SendResult<String, String>> publish(String key, DeadLetterEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            log.warn("Publishing dead letter with key '{}' to '{}'", key, TopicNames.DEADLETTER_TRADE);
            return kafkaOperations.send(TopicNames.DEADLETTER_TRADE, key, json);
        } catch (Exception ex) {
            log.error("Failed to serialize dead letter with key '{}': {}", key, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }
}
