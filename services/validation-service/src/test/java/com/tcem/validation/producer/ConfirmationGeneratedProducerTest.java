package com.tcem.validation.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.ConfirmationStatus;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.ConfirmationEvent;
import com.tcem.common.model.confirmation.ConfirmationRecord;
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
class ConfirmationGeneratedProducerTest {

    @Mock
    private KafkaOperations<String, String> kafkaOperations;

    private ObjectMapper objectMapper;
    private ConfirmationGeneratedProducer producer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        producer = new ConfirmationGeneratedProducer(kafkaOperations, objectMapper);
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
    @DisplayName("Dispatches ConfirmationEvent to 'confirmation.generated' with correct tradeId key")
    void testSendConfirmationGeneratedSuccess() {
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-CONF-001");
        header.setCorrelationId("CORR-CF-001");

        ConfirmationRecord record = new ConfirmationRecord();
        record.setConfirmationId("CONF-20001");
        record.setTradeId("TRD-20001");
        record.setTradeVersion(1);
        record.setTemplateId("TMPL-FX-OPT-001");
        record.setStatus(ConfirmationStatus.ALLEGED);
        record.setGeneratedAt(Instant.parse("2026-01-15T09:00:00Z"));
        record.setGeneratedBy("validation-service");

        ConfirmationEvent event = new ConfirmationEvent(header, record);

        when(kafkaOperations.send(eq(TopicNames.CONFIRMATION_GENERATED), eq("TRD-20001"), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.CONFIRMATION_GENERATED)));

        CompletableFuture<SendResult<String, String>> future = producer.sendConfirmationGenerated("TRD-20001", event);

        assertThat(future).isCompleted();
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.CONFIRMATION_GENERATED), eq("TRD-20001"), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertThat(payload)
                .contains("CONF-20001")
                .contains("TRD-20001")
                .contains("TMPL-FX-OPT-001")
                .contains("ALLEGED")
                .contains("validation-service")
                .contains("MSG-CONF-001");
    }

    @Test
    @DisplayName("Falls back to 'UNKNOWN_CONFIRMATION' key when tradeId is null")
    void testSendConfirmationGeneratedNullTradeIdFallback() {
        ConfirmationEvent event = new ConfirmationEvent();

        when(kafkaOperations.send(eq(TopicNames.CONFIRMATION_GENERATED), eq("UNKNOWN_CONFIRMATION"), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.CONFIRMATION_GENERATED)));

        CompletableFuture<SendResult<String, String>> future = producer.sendConfirmationGenerated(null, event);

        assertThat(future).isCompleted();
        verify(kafkaOperations).send(eq(TopicNames.CONFIRMATION_GENERATED), eq("UNKNOWN_CONFIRMATION"), any());
    }

    @Test
    @DisplayName("Returns failed future when Kafka send fails")
    void testSendConfirmationGeneratedKafkaFailure() {
        ConfirmationEvent event = new ConfirmationEvent();
        RuntimeException kafkaError = new RuntimeException("Broker unavailable");

        when(kafkaOperations.send(eq(TopicNames.CONFIRMATION_GENERATED), eq("TRD-20002"), any()))
                .thenReturn(CompletableFuture.failedFuture(kafkaError));

        CompletableFuture<SendResult<String, String>> future = producer.sendConfirmationGenerated("TRD-20002", event);

        assertThat(future).isCompletedExceptionally();
    }

    @Test
    @DisplayName("Payload contains fingerprint when set on confirmation record")
    void testSendConfirmationGeneratedPayloadContainsFingerprint() {
        ConfirmationRecord record = new ConfirmationRecord();
        record.setConfirmationId("CONF-20003");
        record.setTradeId("TRD-20003");
        record.setConfirmationFingerprint("abc123def456");

        ConfirmationEvent event = new ConfirmationEvent(new MessageHeader(), record);

        when(kafkaOperations.send(eq(TopicNames.CONFIRMATION_GENERATED), eq("TRD-20003"), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.CONFIRMATION_GENERATED)));

        producer.sendConfirmationGenerated("TRD-20003", event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.CONFIRMATION_GENERATED), eq("TRD-20003"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("abc123def456");
    }
}
