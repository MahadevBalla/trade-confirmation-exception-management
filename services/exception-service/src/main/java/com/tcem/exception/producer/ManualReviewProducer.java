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
 * Producer for dispatching exception events that require manual review to the
 * {@code manual.review.required} Kafka topic.
 * <p>
 * Published when the classification confidence falls below the threshold (< 0.6)
 * or when the category is a {@code COUNTERPARTY_MATCHING_DISPUTE}, which always
 * demands human intervention.
 * Consumed downstream by the workitem-service to create HIGH-priority work items.
 * </p>
 */
@Service
public class ManualReviewProducer {

    private static final Logger log = LoggerFactory.getLogger(ManualReviewProducer.class);

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper;

    public ManualReviewProducer(KafkaOperations<String, String> kafkaOperations,
                                ObjectMapper objectMapper) {
        this.kafkaOperations = kafkaOperations;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes an {@link ExceptionEvent} to the {@code manual.review.required} topic.
     *
     * @param tradeId        partition key (tradeId from the exception record)
     * @param exceptionEvent exception event requiring manual review
     * @return CompletableFuture with the send result
     */
    public CompletableFuture<SendResult<String, String>> sendManualReviewRequired(
            String tradeId, ExceptionEvent exceptionEvent) {

        String key = (tradeId != null && !tradeId.isBlank()) ? tradeId : "UNKNOWN_EXCEPTION";
        log.warn("Dispatching manual.review.required event for tradeId='{}' to topic '{}'",
                key, TopicNames.MANUAL_REVIEW_REQUIRED);

        try {
            String jsonPayload = objectMapper.writeValueAsString(exceptionEvent);
            return kafkaOperations.send(TopicNames.MANUAL_REVIEW_REQUIRED, key, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish manual.review.required event with key '{}' to '{}': {}",
                                    key, TopicNames.MANUAL_REVIEW_REQUIRED, ex.getMessage());
                        } else {
                            log.warn("Published manual.review.required event with key '{}' to '{}' " +
                                            "[partition={}, offset={}]",
                                    key,
                                    TopicNames.MANUAL_REVIEW_REQUIRED,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to serialize or dispatch manual.review.required event with key '{}' to '{}': {}",
                    key, TopicNames.MANUAL_REVIEW_REQUIRED, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }
}

