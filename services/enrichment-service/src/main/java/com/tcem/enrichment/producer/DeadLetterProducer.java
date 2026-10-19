package com.tcem.enrichment.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.model.message.MessageHeader;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.UUID;

@Service
public class DeadLetterProducer {
    private final KafkaOperations<String, String> kafka;
    private final ObjectMapper objectMapper;
    public DeadLetterProducer(KafkaOperations<String, String> kafka, ObjectMapper objectMapper) { this.kafka = kafka; this.objectMapper = objectMapper; }

    public void publish(String key, String payload, MessageHeader originalHeader, Throwable error, boolean replayable) {
        try {
            MessageHeader dlqHeader = new MessageHeader();
            dlqHeader.setMessageId("DLQ-" + UUID.randomUUID());
            dlqHeader.setEventType(EventType.NEW_TRADE);
            dlqHeader.setSourceService("enrichment-service");
            dlqHeader.setCreatedTimestamp(Instant.now());
            DeadLetterEvent event = DeadLetterEvent.builder().messageHeader(dlqHeader).originalMessageHeader(originalHeader)
                    .originalPayload(payload).failedOnTopic(TopicNames.TRADE_ACCEPTED).failedInService("enrichment-service")
                    .failureReason(error.getMessage()).exceptionClass(error.getClass().getName()).stackTraceExcerpt(stackTrace(error))
                    .failedAt(Instant.now()).replayCount(originalHeader == null ? 0 : originalHeader.getReplayCount())
                    .replayable(replayable).dlqStatus("PENDING_REPLAY").build();
            kafka.send(TopicNames.DEADLETTER_TRADE, key == null ? "DLQ-" + UUID.randomUUID() : key, objectMapper.writeValueAsString(event)).join();
        } catch (Exception dlqError) {
            throw new IllegalStateException("Unable to publish enrichment dead letter", dlqError);
        }
    }

    private String stackTrace(Throwable error) {
        StringWriter writer = new StringWriter(); error.printStackTrace(new PrintWriter(writer));
        String value = writer.toString(); return value.substring(0, Math.min(value.length(), 2000));
    }
}
