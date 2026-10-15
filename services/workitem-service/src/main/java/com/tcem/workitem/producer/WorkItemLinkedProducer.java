package com.tcem.workitem.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.WorkItemEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes {@link WorkItemEvent} records to 'workitem.linked' when a work item is created or linked (FR10).
 */
@Service
public class WorkItemLinkedProducer {

    private static final Logger log = LoggerFactory.getLogger(WorkItemLinkedProducer.class);

    private final KafkaOperations<String, String> kafkaOperations;
    private final ObjectMapper objectMapper;

    public WorkItemLinkedProducer(KafkaOperations<String, String> kafkaOperations, ObjectMapper objectMapper) {
        this.kafkaOperations = kafkaOperations;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<SendResult<String, String>> sendWorkItemLinked(String key, WorkItemEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            return kafkaOperations.send(TopicNames.WORKITEM_LINKED, key, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish work item event with key '{}': {}", key, ex.getMessage());
                        } else {
                            log.info("Published work item event with key '{}' to '{}'", key, TopicNames.WORKITEM_LINKED);
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to serialize work item event with key '{}': {}", key, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }
}
