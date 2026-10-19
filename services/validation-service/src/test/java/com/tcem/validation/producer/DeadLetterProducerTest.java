package com.tcem.validation.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.model.message.MessageHeader;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterProducerTest {

    @Mock
    private KafkaOperations<String, String> kafkaOperations;

    private ObjectMapper objectMapper;
    private DeadLetterProducer producer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        producer = new DeadLetterProducer(kafkaOperations, objectMapper);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private SendResult<String, String> buildSendResult(String topic) {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(topic, 0), 0, 0, 0, 0, 0);
        return new SendResult<>(null, metadata);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Dispatches DeadLetterEvent to 'deadletter.trade' with correct key")
    void testSendDeadLetterSuccess() {
        MessageHeader header = new MessageHeader();
        header.setMessageId("DLQ-VS-001");

        DeadLetterEvent dlqEvent = DeadLetterEvent.builder()
                .messageHeader(header)
                .originalPayload("{\"tradeId\":\"TRD-40001\"}")
                .failedOnTopic(TopicNames.TRADE_ENRICHED)
                .failedInService("validation-service")
                .failureReason("Deserialization failure: unknown eventType field")
                .exceptionClass("com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException")
                .failedAt(Instant.parse("2026-01-15T10:00:00Z"))
                .replayable(true)
                .dlqStatus("PENDING_REPLAY")
                .build();

        when(kafkaOperations.send(eq(TopicNames.DEADLETTER_TRADE), eq("TRD-40001"), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.DEADLETTER_TRADE)));

        CompletableFuture<SendResult<String, String>> future = producer.sendDeadLetter("TRD-40001", dlqEvent);

        assertThat(future).isCompleted();
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.DEADLETTER_TRADE), eq("TRD-40001"), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertThat(payload)
                .contains("DLQ-VS-001")
                .contains("validation-service")
                .contains("Deserialization failure")
                .contains("UnrecognizedPropertyException")
                .contains("PENDING_REPLAY");
    }

    @Test
    @DisplayName("Dispatches non-replayable DeadLetterEvent with correct fields")
    void testSendDeadLetterNonReplayable() {
        DeadLetterEvent dlqEvent = DeadLetterEvent.builder()
                .failedOnTopic(TopicNames.TRADE_ENRICHED)
                .failedInService("validation-service")
                .failureReason("Permanent schema mismatch: version 99 unsupported")
                .failedAt(Instant.now())
                .replayable(false)
                .dlqStatus("PENDING_REPLAY")
                .build();

        when(kafkaOperations.send(eq(TopicNames.DEADLETTER_TRADE), eq("TRD-40002"), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.DEADLETTER_TRADE)));

        producer.sendDeadLetter("TRD-40002", dlqEvent);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.DEADLETTER_TRADE), eq("TRD-40002"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .contains("Permanent schema mismatch")
                .contains("validation-service");
    }

    @Test
    @DisplayName("Falls back to 'DLQ_<millis>' key pattern when key is null")
    void testSendDeadLetterNullKeyFallback() {
        DeadLetterEvent dlqEvent = DeadLetterEvent.builder()
                .failedOnTopic(TopicNames.TRADE_ENRICHED)
                .failedInService("validation-service")
                .failureReason("Unknown key failure")
                .failedAt(Instant.now())
                .build();

        when(kafkaOperations.send(eq(TopicNames.DEADLETTER_TRADE), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.DEADLETTER_TRADE)));

        CompletableFuture<SendResult<String, String>> future = producer.sendDeadLetter(null, dlqEvent);

        assertThat(future).isCompleted();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.DEADLETTER_TRADE), keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).startsWith("DLQ_");
    }

    @Test
    @DisplayName("Returns failed future when Kafka send fails")
    void testSendDeadLetterKafkaFailure() {
        DeadLetterEvent dlqEvent = DeadLetterEvent.builder()
                .failedInService("validation-service")
                .failedAt(Instant.now())
                .build();
        RuntimeException kafkaError = new RuntimeException("Broker unavailable");

        when(kafkaOperations.send(eq(TopicNames.DEADLETTER_TRADE), eq("TRD-40003"), any()))
                .thenReturn(CompletableFuture.failedFuture(kafkaError));

        CompletableFuture<SendResult<String, String>> future = producer.sendDeadLetter("TRD-40003", dlqEvent);

        assertThat(future).isCompletedExceptionally();
    }
}

