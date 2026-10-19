package com.tcem.validation.producer;

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
 * Producer for dispatching STP exception events to the {@code exception.created} topic (FR7).
 * <p>
 * Published when the {@link com.tcem.validation.rules.StpRulesEngine} determines that STP has
 * failed. The {@link ExceptionEvent} payload is consumed downstream by the {@code exception-service}
 * for classification, theme assignment, and work-item creation.
 * </p>
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
     * Publishes an {@link ExceptionEvent} to the {@code exception.created} topic.
     *
     * @param tradeId        partition key; falls back to {@code "UNKNOWN_EXCEPTION"} when null
     * @param exceptionEvent exception event envelope
     * @return CompletableFuture with the Kafka send result
     */
    public CompletableFuture<SendResult<String, String>> sendExceptionCreated(String tradeId, ExceptionEvent exceptionEvent) {
        String key = tradeId != null ? tradeId : "UNKNOWN_EXCEPTION";
        log.debug("Dispatching exception event to topic '{}' with key '{}'", TopicNames.EXCEPTION_CREATED, key);
        try {
            String jsonPayload = objectMapper.writeValueAsString(exceptionEvent);
            return kafkaOperations.send(TopicNames.EXCEPTION_CREATED, key, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish exception event for trade '{}' to '{}': {}",
                                    key, TopicNames.EXCEPTION_CREATED, ex.getMessage());
                        } else {
                            log.info("Successfully published exception event for trade '{}' to '{}' [partition={}, offset={}]",
                                    key,
                                    TopicNames.EXCEPTION_CREATED,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception ex) {
            log.warn("Failed to serialize or dispatch exception event for trade '{}' to topic '{}': {}",
                    key, TopicNames.EXCEPTION_CREATED, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }
}

