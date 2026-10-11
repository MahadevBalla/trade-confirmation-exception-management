package com.tcem.ingestion.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.ExceptionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Producer for dispatching ExceptionEvent instances to 'exception.created' (FR7).
 * Consumed downstream by exception-service.
 */
@Service
public class ExceptionCreatedProducer {

    private static final Logger log = LoggerFactory.getLogger(ExceptionCreatedProducer.class);

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper;

    public ExceptionCreatedProducer(KafkaOperations<String, String> kafkaOperations, ObjectMapper objectMapper) {
        this.kafkaOperations = kafkaOperations;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes an ExceptionEvent to the 'exception.created' topic.
     *
     * @param tradeId        partition key
     * @param exceptionEvent exception event envelope
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, String>> sendExceptionCreated(String tradeId, ExceptionEvent exceptionEvent) {
        String key = tradeId != null ? tradeId : "UNKNOWN_EXCEPTION";
        log.debug("Dispatching exception event to topic '{}' with key '{}'", TopicNames.EXCEPTION_CREATED, key);
        try {
            String jsonPayload = objectMapper.writeValueAsString(exceptionEvent);
            return kafkaOperations.send(TopicNames.EXCEPTION_CREATED, key, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish exception for trade '{}' to '{}': {}",
                                    key, TopicNames.EXCEPTION_CREATED, ex.getMessage());
                        } else {
                            log.info("Successfully published exception for trade '{}' to '{}' [partition={}, offset={}]",
                                    key,
                                    TopicNames.EXCEPTION_CREATED,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception ex) {
            log.warn("Failed to serialize or dispatch exception for trade '{}' to topic '{}': {}",
                    key, TopicNames.EXCEPTION_CREATED, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }
}
