package com.tcem.exception.service;

import com.tcem.exception.model.ExceptionProcessingResult;

/**
 * Service contract for processing inbound {@code exception.created} Kafka messages (FR7).
 * <p>
 * Separates the Kafka listener ({@link com.tcem.exception.consumer.ExceptionCreatedConsumer})
 * from the processing logic to allow independent unit testing.
 * </p>
 */
public interface ExceptionProcessingService {

    /**
     * Processes a raw JSON payload received from the {@code exception.created} Kafka topic.
     *
     * @param partitionKey the Kafka record key (typically tradeId)
     * @param rawPayload   raw JSON string of the {@code ExceptionEvent}
     * @return the outcome of the processing pipeline
     */
    ExceptionProcessingResult process(String partitionKey, String rawPayload);
}

