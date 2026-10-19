package com.tcem.validation.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.TopicNames;
import com.tcem.validation.model.StpValidatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Producer for publishing STP validation outcomes to the {@code stp.validated} topic (FR5).
 * <p>
 * Dispatched for every trade processed by the {@link com.tcem.validation.rules.StpRulesEngine},
 * regardless of pass/fail outcome. Downstream consumers (exception-service, workitem-service)
 * use this topic as the canonical audit record for STP decisions.
 * </p>
 */
@Service
public class StpValidatedProducer {

    private static final Logger log = LoggerFactory.getLogger(StpValidatedProducer.class);

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper;

    public StpValidatedProducer(KafkaOperations<String, String> kafkaOperations, ObjectMapper objectMapper) {
        this.kafkaOperations = kafkaOperations;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes an {@link StpValidatedEvent} to the {@code stp.validated} topic.
     *
     * @param tradeId partition key; falls back to {@code "UNKNOWN_TRADE"} when null
     * @param event   STP validation outcome event
     * @return CompletableFuture with the Kafka send result
     */
    public CompletableFuture<SendResult<String, String>> sendStpValidated(String tradeId, StpValidatedEvent event) {
        String key = tradeId != null ? tradeId : "UNKNOWN_TRADE";
        log.debug("Dispatching STP validated event to topic '{}' with key '{}'", TopicNames.STP_VALIDATED, key);
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            return kafkaOperations.send(TopicNames.STP_VALIDATED, key, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish STP validated event for trade '{}' to '{}': {}",
                                    key, TopicNames.STP_VALIDATED, ex.getMessage());
                        } else {
                            log.info("Successfully published STP validated event for trade '{}' to '{}' [partition={}, offset={}]",
                                    key,
                                    TopicNames.STP_VALIDATED,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception ex) {
            log.warn("Failed to serialize or dispatch STP validated event for trade '{}' to topic '{}': {}",
                    key, TopicNames.STP_VALIDATED, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }
}

