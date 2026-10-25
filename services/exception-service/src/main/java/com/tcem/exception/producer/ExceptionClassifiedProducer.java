package com.tcem.exception.producer;

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
 * Producer for dispatching classified exception events to the
 * {@code exception.classified} Kafka topic (FR8, FR9).
 * <p>
 * Consumed downstream by the workitem-service to create or link a work item.
 * Fire-and-forget; failures are logged but not re-thrown to the caller.
 * </p>
 */
@Service
public class ExceptionClassifiedProducer {

    private static final Logger log = LoggerFactory.getLogger(ExceptionClassifiedProducer.class);

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper;

    public ExceptionClassifiedProducer(KafkaOperations<String, String> kafkaOperations,
                                       ObjectMapper objectMapper) {
        this.kafkaOperations = kafkaOperations;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes an {@link ExceptionEvent} to the {@code exception.classified} topic.
     *
     * @param tradeId        partition key (tradeId from the exception record)
     * @param exceptionEvent fully classified exception event envelope
     * @return CompletableFuture with the send result
     */
    public CompletableFuture<SendResult<String, String>> sendExceptionClassified(
            String tradeId, ExceptionEvent exceptionEvent) {

        String key = (tradeId != null && !tradeId.isBlank()) ? tradeId : "UNKNOWN_EXCEPTION";
        log.debug("Dispatching exception.classified event for tradeId='{}' to topic '{}'",
                key, TopicNames.EXCEPTION_CLASSIFIED);

        try {
            String jsonPayload = objectMapper.writeValueAsString(exceptionEvent);
            return kafkaOperations.send(TopicNames.EXCEPTION_CLASSIFIED, key, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish exception.classified event with key '{}' to '{}': {}",
                                    key, TopicNames.EXCEPTION_CLASSIFIED, ex.getMessage());
                        } else {
                            log.info("Published exception.classified event with key '{}' to '{}' " +
                                            "[partition={}, offset={}]",
                                    key,
                                    TopicNames.EXCEPTION_CLASSIFIED,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to serialize or dispatch exception.classified event with key '{}' to '{}': {}",
                    key, TopicNames.EXCEPTION_CLASSIFIED, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }
}

