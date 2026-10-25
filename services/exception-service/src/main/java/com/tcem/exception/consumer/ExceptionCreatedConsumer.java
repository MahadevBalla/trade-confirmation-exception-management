package com.tcem.exception.consumer;

import com.tcem.common.constants.TopicNames;
import com.tcem.exception.model.ExceptionProcessingResult;
import com.tcem.exception.service.ExceptionProcessingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the {@code exception.created} topic (FR7).
 * <p>
 * Listens for {@code ExceptionEvent} messages produced by {@code validation-service}
 * (STP failures) and {@code ingestion-service} (schema/parse failures), then delegates
 * the full 7-step processing pipeline to {@link ExceptionProcessingService}.
 * </p>
 *
 * <h3>Error handling strategy</h3>
 * <ul>
 *   <li>All business failures (parse errors, classification failures, CouchDB errors)
 *       are handled inside {@link ExceptionProcessingService#process} and result in a
 *       {@code DEAD_LETTER} outcome — they never propagate as unhandled exceptions.</li>
 *   <li>Any unexpected exception that escapes the service is caught here, logged,
 *       and swallowed so Kafka does not re-deliver (which would cause infinite loops
 *       for structural failures). The message is effectively discarded — the DLQ
 *       was the last resort.</li>
 * </ul>
 */
@Component
public class ExceptionCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExceptionCreatedConsumer.class);

    private final ExceptionProcessingService processingService;

    public ExceptionCreatedConsumer(ExceptionProcessingService processingService) {
        this.processingService = processingService;
    }

    /**
     * Receives a raw JSON exception event from {@code exception.created}.
     *
     * @param record Kafka consumer record with tradeId key and raw JSON value
     */
    @KafkaListener(
            topics = TopicNames.EXCEPTION_CREATED,
            groupId = "${spring.kafka.consumer.group-id:exception-service-group}",
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String key     = record.key();
        String payload = record.value();
        log.info("Received exception.created message [key='{}', partition={}, offset={}]",
                key, record.partition(), record.offset());

        try {
            ExceptionProcessingResult result = processingService.process(key, payload);
            log.info("Finished processing exception.created message [key='{}', result={}]", key, result);
        } catch (Exception ex) {
            // Should never reach here — all failures are handled inside the service.
            // Log as error so it appears in monitoring dashboards.
            log.error("Unexpected unhandled exception processing message [key='{}', partition={}, offset={}]: {}",
                    key, record.partition(), record.offset(), ex.getMessage(), ex);
        }
    }
}

