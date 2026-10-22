package com.tcem.validation.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.validation.model.StpValidatedEvent;
import com.tcem.validation.rules.StpValidationResult;
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

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StpValidatedProducerTest {

    @Mock
    private KafkaOperations<String, String> kafkaOperations;

    private ObjectMapper objectMapper;
    private StpValidatedProducer producer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        producer = new StpValidatedProducer(kafkaOperations, objectMapper);
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
    @DisplayName("Dispatches STP-passed event to 'stp.validated' with correct tradeId key")
    void testSendStpValidatedPassedEvent() {
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-STP-001");
        header.setCorrelationId("CORR-001");

        StpValidationResult result = new StpValidationResult();
        StpValidatedEvent event = StpValidatedEvent.of(header, "TRD-10001", 1, result);

        when(kafkaOperations.send(eq(TopicNames.STP_VALIDATED), eq("TRD-10001"), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.STP_VALIDATED)));

        CompletableFuture<SendResult<String, String>> future = producer.sendStpValidated("TRD-10001", event);

        assertThat(future).isCompleted();
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.STP_VALIDATED), eq("TRD-10001"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .contains("TRD-10001")
                .contains("stpPassed")
                .contains("MSG-STP-001");
    }

    @Test
    @DisplayName("Dispatches STP-failed event with failure details in payload")
    void testSendStpValidatedFailedEvent() {
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-STP-002");

        StpValidationResult result = new StpValidationResult();
        result.addFailure(
                com.tcem.common.constants.ExceptionCategory.MISSING_ECONOMIC_DATA,
                com.tcem.common.constants.ExceptionSubTheme.MISSING_NOTIONAL,
                "NOTIONAL_MISSING",
                "notional"
        );
        StpValidatedEvent event = StpValidatedEvent.of(header, "TRD-10002", 2, result);

        when(kafkaOperations.send(eq(TopicNames.STP_VALIDATED), eq("TRD-10002"), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.STP_VALIDATED)));

        CompletableFuture<SendResult<String, String>> future = producer.sendStpValidated("TRD-10002", event);

        assertThat(future).isCompleted();
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.STP_VALIDATED), eq("TRD-10002"), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertThat(payload)
                .contains("TRD-10002")
                .contains("MISSING_ECONOMIC_DATA")
                .contains("MISSING_NOTIONAL")
                .contains("NOTIONAL_MISSING")
                .contains("failureSummary");
    }

    @Test
    @DisplayName("Falls back to 'UNKNOWN_TRADE' key when tradeId is null")
    void testSendStpValidatedNullTradeIdFallback() {
        StpValidatedEvent event = new StpValidatedEvent();

        when(kafkaOperations.send(eq(TopicNames.STP_VALIDATED), eq("UNKNOWN_TRADE"), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.STP_VALIDATED)));

        CompletableFuture<SendResult<String, String>> future = producer.sendStpValidated(null, event);

        assertThat(future).isCompleted();
        verify(kafkaOperations).send(eq(TopicNames.STP_VALIDATED), eq("UNKNOWN_TRADE"), any());
    }

    @Test
    @DisplayName("Returns failed future when Kafka send fails")
    void testSendStpValidatedKafkaFailure() {
        StpValidatedEvent event = new StpValidatedEvent();
        RuntimeException kafkaError = new RuntimeException("Broker unavailable");

        when(kafkaOperations.send(eq(TopicNames.STP_VALIDATED), eq("TRD-10003"), any()))
                .thenReturn(CompletableFuture.failedFuture(kafkaError));

        CompletableFuture<SendResult<String, String>> future = producer.sendStpValidated("TRD-10003", event);

        assertThat(future).isCompletedExceptionally();
    }
}

