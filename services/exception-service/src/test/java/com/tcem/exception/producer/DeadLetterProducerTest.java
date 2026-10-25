package com.tcem.exception.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DeadLetterProducer}.
 */
@ExtendWith(MockitoExtension.class)
class DeadLetterProducerTest {

    @Mock
    private KafkaOperations<String, String> kafkaOperations;

    private ObjectMapper objectMapper;
    private DeadLetterProducer producer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        producer = new DeadLetterProducer(kafkaOperations, objectMapper);
    }

    @Test
    void send_withValidKey_publishesToDeadLetterTopic() {
        DeadLetterEvent event = buildEvent();
        when(kafkaOperations.send(eq(TopicNames.DEADLETTER_TRADE), eq("TRD-DLQ-001"), anyString()))
                .thenReturn(completedFuture("TRD-DLQ-001"));

        CompletableFuture<SendResult<String, String>> result =
                producer.sendDeadLetter("TRD-DLQ-001", event);

        assertThat(result).isNotNull();
        verify(kafkaOperations).send(
                eq(TopicNames.DEADLETTER_TRADE), eq("TRD-DLQ-001"), anyString());
    }

    @Test
    void send_withNullKey_usesDlqTimestampFallback() {
        DeadLetterEvent event = buildEvent();
        // The key will be "DLQ_<timestamp>" — we can't predict exact value, so use anyString()
        when(kafkaOperations.send(eq(TopicNames.DEADLETTER_TRADE), anyString(), anyString()))
                .thenReturn(completedFuture("DLQ_FALLBACK"));

        producer.sendDeadLetter(null, event);

        verify(kafkaOperations).send(
                eq(TopicNames.DEADLETTER_TRADE),
                argThat(k -> k != null && k.startsWith("DLQ_")),
                anyString());
    }

    @Test
    void send_withBlankKey_usesDlqTimestampFallback() {
        DeadLetterEvent event = buildEvent();
        when(kafkaOperations.send(eq(TopicNames.DEADLETTER_TRADE), anyString(), anyString()))
                .thenReturn(completedFuture("DLQ_FALLBACK"));

        producer.sendDeadLetter("   ", event);

        verify(kafkaOperations).send(
                eq(TopicNames.DEADLETTER_TRADE),
                argThat(k -> k != null && k.startsWith("DLQ_")),
                anyString());
    }

    @Test
    void send_serializationFailure_returnsFailedFuture() {
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        DeadLetterProducer brokenProducer = new DeadLetterProducer(kafkaOperations, brokenMapper);

        try {
            when(brokenMapper.writeValueAsString(any())).thenThrow(new RuntimeException("ser-error"));
        } catch (Exception ignored) {}

        CompletableFuture<SendResult<String, String>> result =
                brokenProducer.sendDeadLetter("TRD-001", buildEvent());

        assertThat(result.isCompletedExceptionally()).isTrue();
        verifyNoInteractions(kafkaOperations);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private DeadLetterEvent buildEvent() {
        return DeadLetterEvent.builder()
                .failedOnTopic(TopicNames.EXCEPTION_CREATED)
                .failedInService("exception-service")
                .failureReason("Test failure")
                .failedAt(Instant.now())
                .replayable(true)
                .build();
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, String>> completedFuture(String key) {
        ProducerRecord<String, String> pr =
                new ProducerRecord<>(TopicNames.DEADLETTER_TRADE, key, "{}");
        RecordMetadata meta = new RecordMetadata(
                new TopicPartition(TopicNames.DEADLETTER_TRADE, 0), 0, 0, 0, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(pr, meta));
    }
}

