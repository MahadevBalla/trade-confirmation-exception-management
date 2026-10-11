package com.tcem.ingestion.producer;

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

    @Test
    @DisplayName("Successfully dispatches DeadLetterEvent to 'deadletter.trade' topic")
    void testSendDeadLetterSuccess() {
        MessageHeader header = new MessageHeader();
        header.setMessageId("DLQ-001");

        DeadLetterEvent dlqEvent = DeadLetterEvent.builder()
                .messageHeader(header)
                .originalPayload("{\"invalid\":\"json\"}")
                .failedOnTopic(TopicNames.TRADE_RAW)
                .failedInService("ingestion-service")
                .failureReason("Fatal JSON syntax error")
                .exceptionClass("com.fasterxml.jackson.core.JsonParseException")
                .failedAt(Instant.now())
                .replayable(false)
                .dlqStatus("PENDING_REPLAY")
                .build();

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TopicNames.DEADLETTER_TRADE, 0),
                0, 0, 0, 0, 0
        );
        SendResult<String, String> sendResult = new SendResult<>(null, metadata);
        when(kafkaOperations.send(eq(TopicNames.DEADLETTER_TRADE), eq("KEY-999"), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        CompletableFuture<SendResult<String, String>> future = producer.sendDeadLetter("KEY-999", dlqEvent);

        assertThat(future).isCompleted();
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.DEADLETTER_TRADE), eq("KEY-999"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .contains("Fatal JSON syntax error")
                .contains("ingestion-service")
                .contains("JsonParseException");
    }
}
